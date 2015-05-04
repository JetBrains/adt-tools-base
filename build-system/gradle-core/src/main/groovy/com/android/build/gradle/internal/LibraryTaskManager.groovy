/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal
import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.profile.SpanRecorders
import com.android.build.gradle.internal.scope.AndroidTask
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.build.gradle.internal.variant.LibVariantOutputData
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.VariantHelper
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.MergeResources
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.dependency.LibraryBundle
import com.android.builder.dependency.LibraryDependency
import com.android.builder.dependency.ManifestDependency
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.MavenCoordinates
import com.android.builder.profile.ExecutionType
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import static com.android.SdkConstants.LIBS_FOLDER
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static com.android.builder.model.AndroidProject.FD_OUTPUTS
/**
 * TaskManager for creating tasks in an Android library project.
 */
class LibraryTaskManager extends TaskManager {

    private static final String ANNOTATIONS = "annotations"

    private Task assembleDefault;

    public LibraryTaskManager (
            Project project,
            AndroidBuilder androidBuilder,
            BaseExtension extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, extension, sdkHandler, dependencyManager, toolingRegistry)
    }

    @Override
    public void createTasksForVariantData(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        LibraryVariantData libVariantData = variantData as LibraryVariantData
        GradleVariantConfiguration variantConfig = variantData.variantConfiguration
        CoreBuildType buildType = variantConfig.buildType

        VariantScope variantScope = variantData.getScope()

        String fullName = variantConfig.fullName
        String dirName = variantConfig.dirName

        createAnchorTasks(tasks, variantScope)

        createCheckManifestTask(tasks, variantScope)

        // Add a task to create the res values
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK) {
            createGenerateResValuesTask(tasks, variantScope)
        }

        // Add a task to process the manifest(s)
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK) {
            createMergeLibManifestsTask(tasks, variantScope)
        }

        // Add a task to compile renderscript files.
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK) {
            createRenderscriptTask(tasks, variantScope)
        }

        AndroidTask<MergeResources> packageRes = SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK) {
            // Create a merge task to only merge the resources from this library and not
            // the dependencies. This is what gets packaged in the aar.
            AndroidTask<MergeResources> mergeResourceTask = basicCreateMergeResourcesTask(
                    tasks,
                    variantScope,
                    "package",
                    new File(variantScope.getGlobalScope().getIntermediatesDir(),
                            "$DIR_BUNDLES/$variantScope.variantConfiguration.dirName/res"),
                    false /*includeDependencies*/,
                    false /*process9Patch*/);

            if (!variantData.variantDependency.androidDependencies.isEmpty()) {
                // Add a task to merge the resource folders, including the libraries, in order to
                // generate the R.txt file with all the symbols, including the ones from
                // the dependencies.
                createMergeResourcesTask(tasks, variantScope)
            }

            mergeResourceTask.configure(tasks) { MergeResources task ->
                task.conventionMapping.publicFile = {
                    new File(variantScope.globalScope.intermediatesDir,
                            "$DIR_BUNDLES/${dirName}/${SdkConstants.FN_PUBLIC_TXT}")
                }
            }

            return mergeResourceTask;
        }

        // Add a task to merge the assets folders
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK) {
            createMergeAssetsTask(tasks, variantScope)
        }

        // Add a task to create the BuildConfig class
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK) {
            createBuildConfigTask(tasks, variantScope)
        }

        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_BACKPORT_RESOURCES_TASK) {
            createPreprocessResourcesTask(tasks, variantScope)
        }

        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_PROCESS_RES_TASK) {
            // Add a task to generate resource source files, directing the location
            // of the r.txt file to be directly in the bundle.
            createProcessResTask(tasks, variantScope,
                    new File("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}"),
                    false /*generateResourcePackage*/,
            )

            // process java resources
            createProcessJavaResTask(tasks, variantScope)
        }

        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_AIDL_TASK) {
            createAidlTask(tasks, variantScope)
        }

        // Add a compile task
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_COMPILE_TASK) {
            createJavaCompileTask(tasks, variantScope);
        }

        // package the prebuilt native libs into the bundle folder
        Sync packageJniLibs = project.tasks.create(
                "package${fullName.capitalize()}JniLibs",
                Sync)

        // Add dependencies on NDK tasks if NDK plugin is applied.
        if (isNdkTaskNeeded) {
            // Add NDK tasks
            SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_NDK_TASK) {
                createNdkTasks(variantData);
                packageJniLibs.dependsOn variantData.ndkCompileTask
                packageJniLibs.from(variantData.ndkCompileTask.soFolder).include("**/*.so")
            }
        }

        Sync packageRenderscript = SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_PACKAGING_TASK) {
            // package from 2 sources.
            packageJniLibs.from(variantConfig.jniLibsList).include("**/*.so")
            packageJniLibs.into(project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/jni"))

            // package the renderscript header files files into the bundle folder
            Sync packageRenderscript = project.tasks.create(
                    "package${fullName.capitalize()}Renderscript",
                    Sync)
            // package from 3 sources. the order is important to make sure the override works well.
            packageRenderscript.from(variantConfig.renderscriptSourceList).include("**/*.rsh")
            packageRenderscript.into(project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$SdkConstants.FD_RENDERSCRIPT"))
            return packageRenderscript;
        }

        // merge consumer proguard files from different build types and flavors
        MergeFileTask mergeProGuardFileTask = SpanRecorders.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_PROGUARD_FILE_TASK) {
            MergeFileTask mergeProGuardFileTask = project.tasks.create(
                    "merge${fullName.capitalize()}ProguardFiles",
                    MergeFileTask)
            mergeProGuardFileTask.inputFiles =
                project.files(variantConfig.getConsumerProguardFiles()).files
            mergeProGuardFileTask.outputFile = project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$LibraryBundle.FN_PROGUARD_TXT")
            return mergeProGuardFileTask
        }

        // copy lint.jar into the bundle folder
        Copy lintCopy = project.tasks.create(
                "copy${fullName.capitalize()}Lint",
                Copy)
        lintCopy.dependsOn LINT_COMPILE
        lintCopy.from("$project.buildDir/${FD_INTERMEDIATES}/lint/lint.jar")
        lintCopy.into("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/$dirName")

        Zip bundle = project.tasks.create(
                "bundle${fullName.capitalize()}",
                Zip)

        libVariantData.generateAnnotationsTask = variantData.variantDependency.annotationsPresent ? createExtractAnnotations(
                fullName, project, variantData) : null
        if (libVariantData.generateAnnotationsTask != null) {
            bundle.dependsOn(libVariantData.generateAnnotationsTask)
        }

        final boolean instrumented = variantConfig.buildType.isTestCoverageEnabled()


        // data holding dependencies and input for the dex. This gets updated as new
        // post-compilation steps are inserted between the compilation and dx.
        PostCompilationData pcData = new PostCompilationData()
        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_POST_COMPILATION_TASK) {
            pcData.classGeneratingTask = [variantScope.javaCompileTask.name]
            pcData.libraryGeneratingTask = Collections.singletonList(
                    variantData.variantDependency.packageConfiguration.buildDependencies)
            pcData.inputFiles = {
                return variantData.javaCompileTask.outputs.files.files
            }
            pcData.inputDir = {
                return variantScope.javaOutputDir
            }
            pcData.inputLibraries = {
                return Collections.emptyList()
            }

            // if needed, instrument the code
            if (instrumented) {
                pcData = createJacocoTask(tasks, variantScope, pcData);
            }
        }

        if (buildType.isMinifyEnabled()) {
            // run proguard on output of compile task
            SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_PROGUARD_TASK) {
                File outFile = maybeCreateProguardTasks(tasks, variantScope, pcData);
                pcData.inputFiles = { [outFile] }
                pcData.inputDir = null
                pcData.inputLibraries = { [] }
            }
        } else {
            // package the local jar in libs/
            SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_PACKAGE_LOCAL_JAR) {
                Sync packageLocalJar = project.tasks.create(
                        "package${fullName.capitalize()}LocalJar",
                        Sync)
                packageLocalJar.from(
                        DependencyManager.getPackagedLocalJarFileList(
                                variantData.variantDependency).toArray())
                packageLocalJar.into(project.file(
                        "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$LIBS_FOLDER"))

                // add the input libraries. This is only going to be the agent jar if applicable
                // due to how inputLibraries is initialized.
                // TODO: clean this.
                packageLocalJar.from(pcData.inputLibraries)
                TaskManager.optionalDependsOn(packageLocalJar, pcData.libraryGeneratingTask)
                pcData.libraryGeneratingTask = Collections.singletonList(packageLocalJar)

                // jar the classes.
                Jar jar = project.tasks.create("package${fullName.capitalize()}Jar", Jar);
                jar.dependsOn variantScope.processJavaResourcesTask.name

                // add the class files (whether they are instrumented or not.
                jar.from(pcData.inputDir)
                TaskManager.optionalDependsOn(jar, pcData.classGeneratingTask)
                pcData.classGeneratingTask = Collections.singletonList(jar)

                jar.from(variantScope.getJavaResourcesDestinationDir())

                jar.destinationDir = project.file(
                        "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}")
                jar.archiveName = "classes.jar"

                String packageName = variantConfig.getPackageFromManifest()
                if (packageName == null) {
                    throw new BuildException("Failed to read manifest", null)
                }
                packageName = packageName.replace('.', '/');

                jar.exclude(packageName + "/R.class")
                jar.exclude(packageName + "/R\$*.class")
                if (!((LibraryExtension) extension).packageBuildConfig) {
                    jar.exclude(packageName + "/Manifest.class")
                    jar.exclude(packageName + "/Manifest\$*.class")
                    jar.exclude(packageName + "/BuildConfig.class")
                }

                if (libVariantData.generateAnnotationsTask != null) {
                    // In case extract annotations strips out private typedef annotation classes
                    jar.dependsOn libVariantData.generateAnnotationsTask
                }
            }
        }

        bundle.dependsOn packageRes.name, packageRenderscript, lintCopy, packageJniLibs, mergeProGuardFileTask
        TaskManager.optionalDependsOn(bundle, pcData.classGeneratingTask)
        TaskManager.optionalDependsOn(bundle, pcData.libraryGeneratingTask)

        bundle.setDescription("Assembles a bundle containing the library in ${fullName.capitalize()}.");
        bundle.destinationDir = project.file("$project.buildDir/${FD_OUTPUTS}/aar")
        bundle.setArchiveName("${project.name}-${variantConfig.baseName}.${BuilderConstants.EXT_LIB_ARCHIVE}")
        bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
        bundle.from(project.file("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}"))
        bundle.from(project.file("$project.buildDir/${FD_INTERMEDIATES}/$ANNOTATIONS/${dirName}"))

        // get the single output for now, though that may always be the case for a library.
        LibVariantOutputData variantOutputData = libVariantData.outputs.get(0)
        variantOutputData.packageLibTask = bundle

        variantData.assembleVariantTask.dependsOn bundle
        variantOutputData.assembleTask = variantData.assembleVariantTask

        if (extension.defaultPublishConfig.equals(fullName)) {
            VariantHelper.setupDefaultConfig(project,
                    variantData.variantDependency.packageConfiguration)

            // add the artifact that will be published
            project.artifacts.add("default", bundle)

            getAssembleDefault().dependsOn variantData.assembleVariantTask
        }

        // also publish the artifact with its full config name
        if (extension.publishNonDefault) {
            project.artifacts.add(variantData.variantDependency.publishConfiguration.name, bundle)
            bundle.classifier = variantData.variantDependency.publishConfiguration.name
        }


        // configure the variant to be testable.
        variantConfig.output = new LibraryBundle(
                bundle.archivePath,
                project.file("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}"),
                variantData.getName(),
                project.getPath()) {

            @Override
            @Nullable
            String getProjectVariant() {
                return variantData.getName()
            }

            @NonNull
            @Override
            List<LibraryDependency> getDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<? extends AndroidLibrary> getLibraryDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<ManifestDependency> getManifestDependencies() {
                return variantConfig.directLibraries
            }

            @Override
            @Nullable
            MavenCoordinates getRequestedCoordinates() {
                return null
            }

            @Override
            @Nullable
            MavenCoordinates getResolvedCoordinates() {
                return null
            }

            @Override
            @NonNull
            protected File getJarsRootFolder() {
                return getFolder();
            }
        };

        SpanRecorders.record(ExecutionType.LIB_TASK_MANAGER_CREATE_LINT_TASK) {
            createLintTasks(tasks, variantScope);
        }
    }

    public ExtractAnnotations createExtractAnnotations(
            String fullName, Project project, BaseVariantData variantData) {
        GradleVariantConfiguration config = variantData.variantConfiguration
        String dirName = config.dirName

        ExtractAnnotations task = project.tasks.create(
                "extract${fullName.capitalize()}Annotations",
                ExtractAnnotations)
        task.description =
                "Extracts Android annotations for the ${fullName} variant into the archive file"
        task.group = BasePlugin.BUILD_GROUP
        task.variant = variantData
        task.destinationDir = project.file("$project.buildDir/${FD_INTERMEDIATES}/$ANNOTATIONS/${dirName}")
        task.output = new File(task.destinationDir, FN_ANNOTATIONS_ZIP)
        task.classDir = project.file("$project.buildDir/${FD_INTERMEDIATES}/classes/${variantData.variantConfiguration.dirName}")
        task.source = variantData.getJavaSources()
        task.encoding = extension.compileOptions.encoding
        task.sourceCompatibility = extension.compileOptions.sourceCompatibility
        conventionMapping(task).map("classpath") {
            project.files(androidBuilder.getCompileClasspath(config))
        }
        task.dependsOn variantData.javaCompileTask

        // Setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed. (Same as in compileTask)
        task.doFirst {
            task.bootClasspath = androidBuilder.getBootClasspathAsStrings()
        }

        return task
    }

    private Task getAssembleDefault() {
        if (assembleDefault == null) {
            assembleDefault = project.tasks.findByName("assembleDefault");
        }
        return assembleDefault
    }

    private static ConventionMapping conventionMapping(Task task) {
        task.conventionMapping
    }
}
