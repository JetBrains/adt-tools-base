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

package com.android.build.gradle.tasks;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.builder.core.VariantConfiguration;
import com.android.utils.StringHelper;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import groovy.lang.Closure;
import proguard.ParseException;
import proguard.gradle.ProGuardTask;

/**
 * Decoration for the {@link ProGuardTask} so it implements shared interfaces with our custom
 * tasks.
 */
public class AndroidProGuardTask extends ProGuardTask implements FileSupplier {

    /**
     * resulting obfuscation mapping file.
     */
    @Nullable
    @InputFile
    @Optional
    File mappingFile;

    /**
     * if this is a test related proguard task, this will point to tested application mapping file
     * which can be absent in case the tested application did not request obfuscation.
     */
    @Nullable
    @InputFile
    @Optional
    File testedAppMappingFile;

    @Override
    public void printmapping(Object printMapping) throws ParseException {
        mappingFile = (File) printMapping;
        super.printmapping(printMapping);
    }

    @Override
    public void applymapping(Object applyMapping) throws ParseException {
        testedAppMappingFile = (File) applyMapping;
    }

    @Override
    public File get() {
        return mappingFile;
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    @Override
    @TaskAction
    public void proguard() throws ParseException, IOException {
        // only set the tested application mapping file if it exists (it must at this point or that
        // means the tested application did not request obfuscation).
        if (testedAppMappingFile != null && testedAppMappingFile.exists()) {
            super.applymapping(testedAppMappingFile);
        }
        super.proguard();
    }

    public static class ConfigAction implements TaskConfigAction<AndroidProGuardTask> {

        private VariantScope scope;

        private Closure<File> inputDir;

        private Closure<List<File>> inputLibraries;

        public ConfigAction(VariantScope scope,
                TaskManager.PostCompilationData pcData) {
            this.scope = scope;
            inputDir = pcData.getInputDir();
            inputLibraries = pcData.getInputLibraries();
        }

        @Override
        public String getName() {
            return scope.getTaskName("proguard");
        }

        @Override
        public Class<AndroidProGuardTask> getType() {
            return AndroidProGuardTask.class;
        }

        @Override
        public void execute(final AndroidProGuardTask proguardTask) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final VariantConfiguration variantConfig = scope.getVariantData().getVariantConfiguration();
            final BaseVariantData testedVariantData = scope.getTestedVariantData();

            // use single output for now.
            final BaseVariantOutputData variantOutputData = scope.getVariantData().getOutputs().get(0);

            if (testedVariantData != null) {
                proguardTask.dependsOn(testedVariantData.getScope().getObfuscationTask().getName());
            }

            variantData.obfuscationTask = proguardTask;
            variantData.mappingFileProviderTask = proguardTask;

            // --- Output File ---

            final File outFile = variantData instanceof LibraryVariantData ? new File(
                    String.valueOf(scope.getGlobalScope().getBuildDir()) + "/" + FD_INTERMEDIATES + "/"
                            + TaskManager.DIR_BUNDLES + "/" + variantData.getVariantConfiguration()
                            .getDirName() + "/classes.jar") : new File(
                    String.valueOf(scope.getGlobalScope().getBuildDir()) + "/" + FD_INTERMEDIATES
                            + "/classes-proguard/" + variantData.getVariantConfiguration().getDirName()
                            + "/classes.jar");
            variantData.obfuscatedClassesJar = outFile;

            // --- Proguard Config ---

            try {

                if (testedVariantData != null) {
                    // Don't remove any code in tested app.
                    proguardTask.dontshrink();
                    proguardTask.dontoptimize();

                    // We can't call dontobfuscate, since that would make ProGuard ignore the mapping file.
                    proguardTask.keep("class * {*;}");
                    proguardTask.keep("interface * {*;}");
                    proguardTask.keep("enum * {*;}");

                    // Input the mapping from the tested app so that we can deal with obfuscated code.
                    proguardTask.applymapping(testedVariantData.getMappingFile());

                    // All -dontwarn rules for test dependencies should go in here:
                    proguardTask.configuration(
                            testedVariantData.getVariantConfiguration().getTestProguardFiles());
                } else {
                    if (variantConfig.isTestCoverageEnabled()) {
                        // when collecting coverage, don't remove the JaCoCo runtime
                        proguardTask.keep("class com.vladium.** {*;}");
                        proguardTask.keep("class org.jacoco.** {*;}");
                        proguardTask.keep("interface org.jacoco.** {*;}");
                        proguardTask.dontwarn("org.jacoco.**");
                    }

                    proguardTask.configuration(new Closure<Collection<File>>(this, this) {
                        public Collection<File> doCall(Object it) {
                            List<File> proguardFiles = variantConfig.getProguardFiles(true,
                                    Collections.singletonList(scope.getGlobalScope().getExtension()
                                            .getDefaultProguardFile(
                                                    TaskManager.DEFAULT_PROGUARD_CONFIG_FILE)));
                            proguardFiles.add(
                                    variantOutputData.processResourcesTask.getProguardOutputFile());
                            return proguardFiles;
                        }

                        public Collection<File> doCall() {
                            return doCall(null);
                        }

                    });
                }

                // --- InJars / LibraryJars ---

                if (variantData instanceof LibraryVariantData) {
                    String packageName = variantConfig.getPackageFromManifest();
                    if (packageName == null) {
                        throw new BuildException("Failed to read manifest", null);
                    }

                    packageName = packageName.replace(".", "/");

                    // injar: the compilation output
                    // exclude R files and such from output
                    String exclude = "!" + packageName + "/R.class";
                    exclude += (", !" + packageName + "/R$*.class");
                    if (!((LibraryExtension) scope.getGlobalScope().getExtension())
                            .getPackageBuildConfig()) {
                        exclude += (", !" + packageName + "/Manifest.class");
                        exclude += (", !" + packageName + "/Manifest$*.class");
                        exclude += (", !" + packageName + "/BuildConfig.class");
                    }

                    proguardTask.injars(ImmutableMap.of("filter", exclude), inputDir);

                    // include R files and such for compilation
                    String include = exclude.replace("!", "");
                    LinkedHashMap<String, Object> map1 = new LinkedHashMap<String, Object>(1);
                    map1.put("filter", include);
                    proguardTask.libraryjars(map1, inputDir);

                    // injar: the local dependencies
                    Closure inJars = new Closure<List<File>>(this, this) {
                        public List<File> doCall(Object it) {
                            return DependencyManager
                                    .getPackagedLocalJarFileList(variantData.getVariantDependency());
                        }

                        public List<File> doCall() {
                            return doCall(null);
                        }

                    };

                    proguardTask.injars(ImmutableMap.of("filter", "!META-INF/MANIFEST.MF"), inJars);

                    // libjar: the library dependencies. In this case we take all the compile-scope
                    // dependencies
                    Closure libJars = new Closure<Iterable<File>>(this, this) {
                        public Iterable<File> doCall(Object it) {
                            // get all the compiled jar.
                            Set<File> compiledJars = scope.getGlobalScope().getAndroidBuilder()
                                    .getCompileClasspath(variantConfig);
                            // and remove local jar that are also packaged
                            final List<File> localJars = DependencyManager
                                    .getPackagedLocalJarFileList(variantData.getVariantDependency());

                            return Iterables.filter(compiledJars, new Predicate<File>() {
                                @Override
                                public boolean apply(File file) {
                                    return !localJars.contains(file);
                                }
                            });
                        }

                        public Iterable<File> doCall() {
                            return doCall(null);
                        }
                    };

                    proguardTask.libraryjars(ImmutableMap.of("filter", "!META-INF/MANIFEST.MF"), libJars);

                    // ensure local jars keep their package names
                    proguardTask.keeppackagenames();
                } else {
                    // injar: the compilation output
                    proguardTask.injars(inputDir);

                    // injar: the packaged dependencies
                    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
                    map.put("filter", "!META-INF/MANIFEST.MF");
                    proguardTask.injars(map, inputLibraries);

                    // the provided-only jars as libraries.
                    Closure libJars = new Closure<List<File>>(this, this) {
                        public List<File> doCall(Object it) {
                            return variantData.getVariantConfiguration().getProvidedOnlyJars();
                        }

                        public List<File> doCall() {
                            return doCall(null);
                        }

                    };

                    proguardTask.libraryjars(libJars);
                }

                // libraryJars: the runtime jars. Do this in doFirst since the boot classpath isn't
                // available until the SDK is loaded in the prebuild task
                proguardTask.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task proguardTask) {
                        for (String runtimeJar : scope.getGlobalScope().getAndroidBuilder()
                                .getBootClasspathAsStrings()) {
                            try {
                                ((AndroidProGuardTask)proguardTask).libraryjars(runtimeJar);
                            } catch (ParseException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                });

                if (testedVariantData != null) {
                    // input the tested app as library
                    proguardTask.libraryjars(testedVariantData.javaCompileTask.getDestinationDir());
                    // including its dependencies
                    Closure testedPackagedJars = new Closure<Set<File>>(this, this) {
                        public Set<File> doCall(Object it) {
                            return scope.getGlobalScope().getAndroidBuilder()
                                    .getPackagedJars(testedVariantData.getVariantConfiguration());
                        }

                        public Set<File> doCall() {
                            return doCall(null);
                        }

                    };

                    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
                    map.put("filter", "!META-INF/MANIFEST.MF");
                    proguardTask.libraryjars(map, testedPackagedJars);
                }

                // --- Out files ---

                proguardTask.outjars(outFile);

                final File proguardOut = new File(
                        String.valueOf(scope.getGlobalScope().getBuildDir()) + "/" + FD_OUTPUTS
                                + "/mapping/" + variantData.getVariantConfiguration().getDirName());

                proguardTask.dump(new File(proguardOut, "dump.txt"));
                proguardTask.printseeds(new File(proguardOut, "seeds.txt"));
                proguardTask.printusage(new File(proguardOut, "usage.txt"));
                proguardTask.printmapping(new File(proguardOut, "mapping.txt"));

                // proguard doesn't verify that the seed/mapping/usage folders exist and will fail
                // if they don't so create them.
                proguardTask.doFirst(new Closure<Boolean>(this, this) {
                    public Boolean doCall(Task it) {
                        return proguardOut.mkdirs();
                    }

                    public Boolean doCall() {
                        return doCall(null);
                    }

                });
            } catch (ParseException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
