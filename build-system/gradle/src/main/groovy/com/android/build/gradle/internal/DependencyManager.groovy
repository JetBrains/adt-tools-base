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

import com.android.annotations.NonNull
import com.android.build.gradle.internal.dependency.DependencyChecker
import com.android.build.gradle.internal.dependency.JarInfo
import com.android.build.gradle.internal.dependency.LibInfo
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.model.MavenCoordinatesImpl
import com.android.build.gradle.internal.model.SyncIssueImpl
import com.android.build.gradle.internal.model.SyncIssueKey
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryDependency
import com.android.builder.model.SyncIssue
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.specs.Specs
import org.gradle.util.GUtil

import static com.android.SdkConstants.DOT_JAR
import static com.android.SdkConstants.EXT_ANDROID_PACKAGE
import static com.android.SdkConstants.EXT_JAR
import static com.android.builder.core.BuilderConstants.EXT_LIB_ARCHIVE
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY

/**
 * A manager to resolve configuration dependencies.
 */
@CompileStatic
class DependencyManager {
    protected static final boolean DEBUG_DEPENDENCY = false

    Project project

    ILogger logger

    final Map<LibraryDependencyImpl, PrepareLibraryTask> prepareTaskMap = [:]

    private boolean buildModelForIde

    /** @deprecated use syncIssue instead */
    @Deprecated
    private final Collection<String> unresolvedDependencies = Sets.newHashSet()
    private final Map<SyncIssueKey, SyncIssue> syncIssues = Maps.newHashMap()

    DependencyManager(Project project) {
        this.project = project
        logger = new LoggerWrapper(Logging.getLogger(this.class))
        buildModelForIde = isBuildModelForIde()
    }

    public Collection<String> getUnresolvedDependencies() {
        return unresolvedDependencies
    }

    public Map<SyncIssueKey, SyncIssue> getSyncIssues() {
        return syncIssues
    }

    public void addDependencyToPrepareTask(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull PrepareDependenciesTask prepareDependenciesTask,
            @NonNull LibraryDependencyImpl lib) {
        PrepareLibraryTask prepareLibTask = prepareTaskMap.get(lib)
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn prepareLibTask
            prepareLibTask.dependsOn variantData.preBuildTask
        }

        for (childLib in lib.dependencies) {
            addDependencyToPrepareTask(
                    variantData,
                    prepareDependenciesTask,
                    childLib as LibraryDependencyImpl)
        }
    }

    public void resolveDependencies(VariantDependencies variantDeps) {
        Multimap<LibraryDependency, VariantDependencies> reverseMap = ArrayListMultimap.create()

        resolveDependencyForConfig(variantDeps, reverseMap)

        processLibraries(variantDeps.getLibraries()) { LibraryDependencyImpl libDependency ->
            Task task = handleLibrary(project, libDependency)

            // Use the reverse map to find all the configurations that included this android
            // library so that we can make sure they are built.
            // TODO fix, this is not optimum as we bring in more dependencies than we should.
            List<VariantDependencies> configDepList = reverseMap.get(libDependency)
            if (configDepList != null && !configDepList.isEmpty()) {
                for (VariantDependencies configDependencies: configDepList) {
                    task.dependsOn configDependencies.compileConfiguration.buildDependencies
                }
            }

            // check if this library is created by a parent (this is based on the
            // output file.
            // TODO Fix this as it's fragile
            /*
            This is a somewhat better way but it doesn't work in some project with
            weird setups...
            Project parentProject = DependenciesImpl.getProject(library.getBundle(), projects)
            if (parentProject != null) {
                String configName = library.getProjectVariant();
                if (configName == null) {
                    configName = "default"
                }

                prepareLibraryTask.dependsOn parentProject.getPath() + ":assemble${configName.capitalize()}"
            }
*/
        }
    }

    private void processLibraries(Collection<LibraryDependencyImpl> libraries, Closure closure) {
        for (LibraryDependencyImpl lib : libraries) {
            closure.call(lib)
            processLibraries(lib.getDependencies() as Collection<LibraryDependencyImpl>, closure)
        }
    }

    /**
     * Handles the library and returns a task to "prepare" the library (ie unarchive it). The task
     * will be reused for all projects using the same library.
     *
     * @param project the project
     * @param library the library.
     * @return the prepare task.
     */
    private PrepareLibraryTask handleLibrary(
            @NonNull Project project,
            @NonNull LibraryDependencyImpl library) {
        String bundleName = GUtil
                .toCamelCase(library.getName().replaceAll("\\:", " "))

        PrepareLibraryTask prepareLibraryTask = prepareTaskMap.get(library)

        if (prepareLibraryTask == null) {
            prepareLibraryTask = project.tasks.create(
                    "prepare" + bundleName + "Library", PrepareLibraryTask.class)

            prepareLibraryTask.setDescription("Prepare " + library.getName())
            conventionMapping(prepareLibraryTask).map("bundle")  { library.getBundle() }
            conventionMapping(prepareLibraryTask).map("explodedDir") { library.getBundleFolder() }

            prepareTaskMap.put(library, prepareLibraryTask)
        }

        return prepareLibraryTask;
    }

    private void resolveDependencyForConfig(
            @NonNull VariantDependencies variantDeps,
            @NonNull Multimap<LibraryDependency, VariantDependencies> reverseMap) {

        Configuration compileClasspath = variantDeps.compileConfiguration
        Configuration packageClasspath = variantDeps.packageConfiguration

        if (DEBUG_DEPENDENCY) {
            println ">>>>>>>>>>"
            println "${project.name}:${compileClasspath.name}/${packageClasspath.name}"
        }

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath)
        ensureConfigured(packageClasspath)

        variantDeps.checker = new DependencyChecker(variantDeps, logger)

        Set<String> currentUnresolvedDependencies = Sets.newHashSet()

        // TODO - defer downloading until required -- This is hard to do as we need the info to build the variant config.
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = Maps.newHashMap()
        collectArtifacts(compileClasspath, artifacts)
        collectArtifacts(packageClasspath, artifacts)

        // --- Handle the external/module dependencies ---
        // keep a map of modules already processed so that we don't go through sections of the
        // graph that have been seen elsewhere.
        Map<ModuleVersionIdentifier, List<LibInfo>> foundLibraries = Maps.newHashMap()
        Map<ModuleVersionIdentifier, List<JarInfo>> foundJars = Maps.newHashMap()

        // first get the compile dependencies. Note that in both case the libraries and the
        // jars are a graph. The list only contains the first level of dependencies, and
        // they themselves contain transitive dependencies (libraries can contain both, jars only
        // contains jars)
        List<LibInfo> compiledAndroidLibraries = Lists.newArrayList()
        List<JarInfo> compiledJars = Lists.newArrayList()

        def dependencies = compileClasspath.incoming.resolutionResult.root.dependencies
        dependencies.each { dep ->
            if (dep instanceof ResolvedDependencyResult) {
                addDependency(
                        (dep as ResolvedDependencyResult).selected,
                        variantDeps,
                        compiledAndroidLibraries,
                        compiledJars,
                        foundLibraries,
                        foundJars,
                        artifacts,
                        reverseMap,
                        0)
            } else if (dep instanceof UnresolvedDependencyResult) {
                def attempted = (dep as UnresolvedDependencyResult).attempted
                if (attempted != null) {
                    currentUnresolvedDependencies.add(attempted.toString())
                }
            }
        }

        // then the packaged ones.
        List<LibInfo> packagedAndroidLibraries = []
        List<JarInfo> packagedJars = []
        dependencies = packageClasspath.incoming.resolutionResult.root.dependencies
        dependencies.each { dep ->
            if (dep instanceof ResolvedDependencyResult) {
                addDependency(
                        (dep as ResolvedDependencyResult).selected,
                        variantDeps,
                        packagedAndroidLibraries,
                        packagedJars,
                        foundLibraries,
                        foundJars,
                        artifacts,
                        reverseMap,
                        0)
            } else if (dep instanceof UnresolvedDependencyResult) {
                def attempted = (dep as UnresolvedDependencyResult).attempted
                if (attempted != null) {
                    currentUnresolvedDependencies.add(attempted.toString())
                }
            }
        }

        // now look through both results.
        // 1. All Android libraries must be in both lists.
        // since we reuse the same instance of LibInfo for identical modules
        // we can simply run through each list and look for libs that are in only one.
        // While the list of library is actually a graph, it's fine to look only at the
        // top level ones since they transitive ones are in the same scope as the direct libraries.
        List<LibInfo> copyOfPackagedLibs = Lists.newArrayList(packagedAndroidLibraries)

        for (LibraryDependencyImpl lib : compiledAndroidLibraries) {
            if (!copyOfPackagedLibs.contains(lib)) {
                handleSyncError(
                        lib.resolvedCoordinates.toString(),
                        SyncIssue.TYPE_NON_JAR_PROVIDED_DEP,
                        "Project ${project.name}: provided dependencies can only be jars. " +
                                "${lib.resolvedCoordinates} is an Android Library.")
            } else {
                copyOfPackagedLibs.remove(lib);
            }
        }
        // at this stage copyOfPackagedLibs should be empty, if not, error.
        for (LibraryDependencyImpl lib : copyOfPackagedLibs) {
            handleSyncError(
                    lib.resolvedCoordinates.toString(),
                    SyncIssue.TYPE_NON_JAR_PACKAGE_DEP,
                    "Project ${project.name}: apk dependencies can only be jars. " +
                            "${lib.resolvedCoordinates} is an Android Library.")
        }

        // 2. merge jar dependencies with a single list where items have packaged/compiled properties.
        // since we reuse the same instance of a JarInfo for identical modules, we can use an
        // Identity set (ie both compiledJars and packagedJars will contain the same instance
        // if it's both compiled and packaged)
        Set<JarInfo> jarInfoSet = Sets.newIdentityHashSet()

        // go through the graphs of dependencies (jars and libs) and gather all the transitive
        // jar dependencies.
        // At the same this we set the compiled/packaged properties.
        gatherJarDependencies(jarInfoSet, compiledJars, true /*compiled*/, false /*packaged*/)
        gatherJarDependencies(jarInfoSet, packagedJars, false /*compiled*/, true /*packaged*/)
        // at this step, we know that libraries have been checked and libraries can only
        // be in both compiled and packaged scope.
        gatherJarDependenciesFromLibraries(jarInfoSet, compiledAndroidLibraries, true, true)

        // and convert them to the right class.
        List<JarDependency> jars = Lists.newArrayListWithCapacity(jarInfoSet.size())
        for (JarInfo jarInfo : jarInfoSet) {
            jars.add(jarInfo.createJarDependency())
        }

        // --- Handle the local jar dependencies ---

        // also need to process local jar files, as they are not processed by the
        // resolvedConfiguration result. This only includes the local jar files for this project.
        Set<File> localCompiledJars = []
        compileClasspath.allDependencies.each { dep ->
            if (dep instanceof SelfResolvingDependency &&
                    !(dep instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dep).resolve()
                for (File f : files) {
                    if (DEBUG_DEPENDENCY) println "LOCAL compile: " + f.getName()
                    // only accept local jar, no other types.
                    if (!f.getName().toLowerCase().endsWith(DOT_JAR)) {
                        handleSyncError(
                                f.absolutePath,
                                SyncIssue.TYPE_NON_JAR_LOCAL_DEP,
                                "Project ${project.name}: Only Jar-type local " +
                                        "dependencies are supported. Cannot handle: ${f.absolutePath}")
                    } else {
                        localCompiledJars.add(f)
                    }
                }
            }
        }

        Set<File> localPackagedJars = []
        packageClasspath.allDependencies.each { dep ->
            if (dep instanceof SelfResolvingDependency &&
                    !(dep instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dep).resolve()
                for (File f : files) {
                    if (DEBUG_DEPENDENCY) println "LOCAL package: " + f.getName()
                    // only accept local jar, no other types.
                    if (!f.getName().toLowerCase().endsWith(DOT_JAR)) {
                        handleSyncError(
                                f.absolutePath,
                                SyncIssue.TYPE_NON_JAR_LOCAL_DEP,
                                "Project ${project.name}: Only Jar-type local " +
                                        "dependencies are supported. Cannot handle: ${f.absolutePath}")
                    } else {
                        localPackagedJars.add(f)
                    }
                }
            }
        }

        // loop through both the compiled and packaged jar to compute the list
        // of jars that are: compile-only, package-only, or both.
        Map<File, JarDependency> localJars = Maps.newHashMap()
        for (File file : localCompiledJars) {
            localJars.put(file, new JarDependency(
                    file,
                    true /*compiled*/,
                    localPackagedJars.contains(file) /*packaged*/,
                    null /*resolvedCoordinates*/))
        }

        for (File file : localPackagedJars) {
            if (!localCompiledJars.contains(file)) {
                localJars.put(file, new JarDependency(
                        file,
                        false /*compiled*/,
                        true /*packaged*/,
                        null /*resolvedCoordinates*/))
            }
        }

        if (buildModelForIde &&
                compileClasspath.resolvedConfiguration.hasError() &&
                !currentUnresolvedDependencies.isEmpty()) {
            unresolvedDependencies.addAll(currentUnresolvedDependencies)

            for (String dependency : currentUnresolvedDependencies) {
                handleSyncError(
                        dependency,
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        "Unable to resolve dependency '${dependency}'")
            }
        }

        // convert the LibInfo in LibraryDependencyImpl and update the reverseMap
        // with the converted keys
        List<LibraryDependencyImpl> libList = convertLibraryInfoIntoDependency(
                compiledAndroidLibraries, reverseMap)

        variantDeps.addLibraries(libList)
        variantDeps.addJars(jars)
        variantDeps.addLocalJars(localJars.values())

        configureBuild(variantDeps)

        if (DEBUG_DEPENDENCY) {
            println "${project.name}:${compileClasspath.name}/${packageClasspath.name}"
            println "<<<<<<<<<<"
        }

    }

    private static List<LibraryDependencyImpl> convertLibraryInfoIntoDependency(
            @NonNull List<LibInfo> libInfos,
            @NonNull Multimap<LibraryDependency, VariantDependencies> reverseMap) {
        List<LibraryDependencyImpl> list = Lists.newArrayListWithCapacity(libInfos.size())

        // since the LibInfos is a graph and the previous "foundLibraries" map ensure we reuse
        // instance where applicable, we'll create a map to keep track of what we have already
        // converted.
        Map<LibInfo, LibraryDependencyImpl> convertedMap = Maps.newIdentityHashMap()

        for (LibInfo libInfo : libInfos) {
            list.add(convertLibInfo(libInfo, reverseMap, convertedMap))
        }

        return list
    }

    private static LibraryDependencyImpl convertLibInfo(
            @NonNull LibInfo libInfo,
            @NonNull Multimap<LibraryDependency, VariantDependencies> reverseMap,
            @NonNull Map<LibInfo, LibraryDependencyImpl> convertedMap) {
        LibraryDependencyImpl convertedLib = convertedMap.get(libInfo)
        if (convertedLib == null) {
            // first, convert the children.
            List<LibInfo> children = libInfo.getDependencies() as List<LibInfo>
            List<LibraryDependency> convertedChildren = Lists.newArrayListWithCapacity(children.size())

            for (LibInfo child : children) {
                convertedChildren.add(convertLibInfo(child, reverseMap, convertedMap))
            }

            // now convert the libInfo
            convertedLib = new LibraryDependencyImpl(
                    libInfo.getBundle(),
                    libInfo.getFolder(),
                    convertedChildren,
                    libInfo.getName(),
                    libInfo.getProjectVariant(),
                    libInfo.getRequestedCoordinates(),
                    libInfo.getResolvedCoordinates())

            // add it to the map
            convertedMap.put(libInfo, convertedLib)

            // and update the reversemap
            // get the items associated with the libInfo. Put in a fresh list as the returned
            // collection is backed by the content of the map.
            Collection<VariantDependencies> values = Lists.newArrayList(reverseMap.get(libInfo))
            reverseMap.removeAll(libInfo)
            reverseMap.putAll(convertedLib, values)
        }

        return convertedLib
    }

    private void gatherJarDependencies(
            Set<JarInfo> outJarInfos,
            Collection<JarInfo> inJarInfos,
            boolean compiled,
            boolean packaged) {
        for (JarInfo jarInfo : inJarInfos) {
            if (!outJarInfos.contains(jarInfo)) {
                outJarInfos.add(jarInfo)
            }

            if (compiled) {
                jarInfo.setCompiled(true)
            }
            if (packaged) {
                jarInfo.setPackaged(true)
            }

            gatherJarDependencies(outJarInfos, jarInfo.getDependencies(), compiled, packaged)
        }
    }

    private void gatherJarDependenciesFromLibraries(
            Set<JarInfo> outJarInfos,
            Collection<LibInfo> inLibraryDependencies,
            boolean compiled,
            boolean packaged) {
        for (LibInfo libInfo : inLibraryDependencies) {
            gatherJarDependencies(outJarInfos, libInfo.getJarDependencies(), compiled, packaged)

            gatherJarDependenciesFromLibraries(
                    outJarInfos,
                    libInfo.getDependencies() as Collection<LibInfo>,
                    compiled,
                    packaged)
        }
    }

    private void ensureConfigured(Configuration config) {
        config.allDependencies.withType(ProjectDependency).each { dep ->
            project.evaluationDependsOn(dep.dependencyProject.path)
            try {
                ensureConfigured(dep.projectConfiguration)
            } catch (Throwable e) {
                throw new UnknownProjectException(
                        "Cannot evaluate module ${dep.name} : ${e.getMessage()}", e);
            }
        }
    }

    private void collectArtifacts(
            Configuration configuration,
            Map<ModuleVersionIdentifier,
                    List<ResolvedArtifact>> artifacts) {

        Set<ResolvedArtifact> allArtifacts
        if (buildModelForIde) {
            allArtifacts = configuration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.satisfyAll())
        } else {
            allArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        }

        allArtifacts.each { ResolvedArtifact artifact ->
            ModuleVersionIdentifier id = artifact.moduleVersion.id
            List<ResolvedArtifact> moduleArtifacts = artifacts.get(id)

            if (moduleArtifacts == null) {
                moduleArtifacts = Lists.newArrayList()
                artifacts.put(id, moduleArtifacts)
            }

            if (!moduleArtifacts.contains(artifact)) {
                moduleArtifacts.add(artifact)
            }
        }
    }

    private static void printIndent(int indent, String message) {
        for (int i = 0 ; i < indent ; i++) {
            print "\t"
        }

        println message
    }

    private void addDependency(
            ResolvedComponentResult moduleVersion,
            VariantDependencies configDependencies,
            Collection<LibInfo> outLibraries,
            List<JarInfo> outJars,
            Map<ModuleVersionIdentifier, List<LibInfo>> alreadyFoundLibraries,
            Map<ModuleVersionIdentifier, List<JarInfo>> alreadyFoundJars,
            Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            Multimap<LibraryDependency, VariantDependencies> reverseMap,
            int indent) {

        ModuleVersionIdentifier id = moduleVersion.moduleVersion
        if (configDependencies.checker.excluded(id)) {
            return
        }

        if (id.name.equals("support-annotations") && id.group.equals("com.android.support")) {
            configDependencies.annotationsPresent = true
        }

        List<LibInfo> libsForThisModule = alreadyFoundLibraries.get(id)
        List<JarInfo> jarsForThisModule = alreadyFoundJars.get(id)

        if (libsForThisModule != null) {
            if (DEBUG_DEPENDENCY) {
                printIndent indent, "FOUND LIB: " + id.name
            }
            outLibraries.addAll(libsForThisModule)

            for (LibInfo lib : libsForThisModule) {
                reverseMap.put(lib, configDependencies)
            }

        } else if (jarsForThisModule != null) {
            if (DEBUG_DEPENDENCY) {
                printIndent indent, "FOUND JAR: " + id.name
            }
            outJars.addAll(jarsForThisModule)
        }
        else {
            if (DEBUG_DEPENDENCY) {
                printIndent indent, "NOT FOUND: " + id.name
            }
            // new module! Might be a jar or a library

            // get the nested components first.
            List<LibInfo> nestedLibraries = Lists.newArrayList()
            List<JarInfo> nestedJars = Lists.newArrayList()

            Set<? extends DependencyResult> dependencies = moduleVersion.dependencies
            dependencies.each { dep ->
                if (dep instanceof ResolvedDependencyResult) {
                    addDependency(
                            (dep as ResolvedDependencyResult).selected,
                            configDependencies,
                            nestedLibraries,
                            nestedJars,
                            alreadyFoundLibraries,
                            alreadyFoundJars,
                            artifacts,
                            reverseMap,
                            indent+1)
                }
            }

            if (DEBUG_DEPENDENCY) {
                printIndent indent, "BACK2: " + id.name
                printIndent indent, "NESTED LIBS: " + nestedLibraries.size();
                printIndent indent, "NESTED JARS: " + nestedJars.size();
            }

            // now loop on all the artifact for this modules.
            List<ResolvedArtifact> moduleArtifacts = artifacts.get(id)

            moduleArtifacts?.each { artifact ->
                if (artifact.type == EXT_LIB_ARCHIVE) {
                    if (DEBUG_DEPENDENCY) {
                        printIndent indent, "TYPE: AAR"
                    }
                    if (libsForThisModule == null) {
                        libsForThisModule = Lists.newArrayList()
                        alreadyFoundLibraries.put(id, libsForThisModule)
                    }

                    String path = "${normalize(logger, id, id.group)}" +
                            "/${normalize(logger, id, id.name)}" +
                            "/${normalize(logger, id, id.version)}"
                    String name = "$id.group:$id.name:$id.version"
                    if (artifact.classifier != null && !artifact.classifier.isEmpty()) {
                        path += "/${normalize(logger, id, artifact.classifier)}"
                        name += ":$artifact.classifier"
                    }
                    //def explodedDir = project.file("$project.rootProject.buildDir/${FD_INTERMEDIATES}/exploded-aar/$path")
                    File explodedDir = project.file(
                            "$project.buildDir/${FD_INTERMEDIATES}/exploded-aar/$path")
                    LibInfo libInfo = new LibInfo(
                            artifact.file,
                            explodedDir,
                            nestedLibraries as List<LibraryDependency>,
                            nestedJars,
                            name,
                            artifact.classifier,
                            null /*requestedCoordinates*/,
                            new MavenCoordinatesImpl(artifact))

                    libsForThisModule.add(libInfo)
                    outLibraries.add(libInfo)
                    reverseMap.put(libInfo, configDependencies)

                } else if (artifact.type == EXT_JAR) {
                    if (DEBUG_DEPENDENCY) {
                        printIndent indent, "TYPE: JAR"
                    }
                    // check this jar does not have a dependency on an library, as this would not work.
                    if (!nestedLibraries.isEmpty()) {
                        handleSyncError(
                                new MavenCoordinatesImpl(artifact).toString(),
                                SyncIssue.TYPE_JAR_DEPEND_ON_AAR,
                                "Module version $id depends on libraries but is a jar");
                    }

                    if (jarsForThisModule == null) {
                        jarsForThisModule = Lists.newArrayList()
                        alreadyFoundJars.put(id, jarsForThisModule)
                    }

                    JarInfo jarInfo = new JarInfo(
                            artifact.file,
                            new MavenCoordinatesImpl(artifact),
                            nestedJars)

                    jarsForThisModule.add(jarInfo);
                    outJars.add(jarInfo)

                } else if (artifact.type == EXT_ANDROID_PACKAGE) {
                    String name = "$id.group:$id.name:$id.version"
                    if (artifact.classifier != null) {
                        name += ":$artifact.classifier"
                    }

                    handleSyncError(
                            name,
                            SyncIssue.TYPE_DEPENDENCY_IS_APK,
                            "Dependency ${name} on project ${project.name} resolves to an APK" +
                                    " archive which is not supported" +
                                    " as a compilation dependency. File: ${artifact.file}")
                } else if (artifact.type == "apklib") {
                    String name = "$id.group:$id.name:$id.version"
                    if (artifact.classifier != null) {
                        name += ":$artifact.classifier"
                    }

                    handleSyncError(
                            name,
                            SyncIssue.TYPE_DEPENDENCY_IS_APKLIB,
                            "Packaging for dependency ${name} is 'apklib' and is not supported. " +
                                    "Only 'aar' libraries are supported.")
                }
            }

            if (DEBUG_DEPENDENCY) {
                printIndent indent, "DONE: " + id.name
            }

        }
    }

    private void handleSyncError(String data, int type, String msg) {
        if (!buildModelForIde) {
            throw new GradleException(msg)
        } else {
            SyncIssue syncIssue = new SyncIssueImpl(
                    type,
                    SyncIssue.SEVERITY_ERROR,
                    data,
                    msg)
            syncIssues.put(SyncIssueKey.from(syncIssue), syncIssue)
        }
    }

    /**
     * Normalize a path to remove all illegal characters for all supported operating systems.
     * {@see http://en.wikipedia.org/wiki/Filename#Comparison%5Fof%5Ffile%5Fname%5Flimitations}
     *
     * @param id the module coordinates that generated this path
     * @param path the proposed path name
     * @return the normalized path name
     */
    static String normalize(ILogger logger, ModuleVersionIdentifier id, String path) {
        if (path == null || path.isEmpty()) {
            logger.info("When unzipping library '${id.group}:${id.name}:${id.version}, " +
                    "either group, name or version is empty")
            return path;
        }
        // list of illegal characters
        String normalizedPath = path.replaceAll("[%<>:\"/?*\\\\]","@");
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            // if the path normalization failed, return the original path.
            logger.info("When unzipping library '${id.group}:${id.name}:${id.version}, " +
                    "the normalized '${path}' is empty")
            return path
        }
        try {
            int pathPointer = normalizedPath.length() - 1;
            // do not end your path with either a dot or a space.
            String suffix = "";
            while (pathPointer >= 0 && (normalizedPath.charAt(pathPointer) == '.'
                    || normalizedPath.charAt(pathPointer) == ' ')) {
                pathPointer--
                suffix += "@"
            }
            if (pathPointer < 0) {
                throw new RuntimeException(
                        "When unzipping library '${id.group}:${id.name}:${id.version}, " +
                                "the path '${path}' cannot be transformed into a valid directory name");
            }
            return normalizedPath.substring(0, pathPointer + 1) + suffix
        } catch (Exception e) {
            logger.error(e, "When unzipping library '${id.group}:${id.name}:${id.version}', " +
                    "Path normalization failed for input ${path}")
            return path;
        }
    }

    private void configureBuild(VariantDependencies configurationDependencies) {
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, "compile");
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, "compile");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects
     * are determined from project lib dependencies using the specified configuration name.
     * These may be projects this project depends on or projects that depend on this project
     * based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise
     * use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private static void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn,
            String otherProjectTaskName,
            String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(
                configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(
                useDependedOn, otherProjectTaskName));
    }

    @CompileDynamic
    private static ConventionMapping conventionMapping(Task task) {
        task.conventionMapping
    }

    /**
     * Returns whether we are just trying to build a model for the IDE instead of building.
     * This means we will attempt to resolve dependencies even if some are broken/unsupported
     * to avoid failing the import in the IDE.
     */
    private boolean isBuildModelForIde() {
        boolean flagValue = false;
        if (project.hasProperty(PROPERTY_BUILD_MODEL_ONLY)) {
            Object value = project.getProperties().get(PROPERTY_BUILD_MODEL_ONLY);
            if (value instanceof String) {
                flagValue = Boolean.parseBoolean(value);
            }
        }
        return flagValue
    }
}
