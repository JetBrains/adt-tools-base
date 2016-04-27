/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.ide.common.process.ProcessException;
import com.google.common.collect.Sets;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Base class for C++ tasks like CmakeJsonModelGenerationTask and NdkBuildJsonModelGenerationTask that shell out to an
 * external process and take well-known, build-specific parameters.
 *
 * Derived classes must implement createNativeBuildJson to produce JSON specific to the given build system
 */
abstract public class ExternalNativeBuildJsonModelGenerationBaseTask extends BaseTask {

    private File projectPath;
    private File objFolder;
    private File soFolder;
    private File jsonFolder;
    private File ndkFolder;
    private boolean debuggable = false;
    private String cFlags = null;
    private String cppFlags = null;
    private Set<String> abis = Sets.newHashSet();
    private List<File> nativeBuildConfigurationsJsons;

    /**
     * Log low level diagnostic information.
     */
    protected void diagnostic(String format, Object... args) {
        getLogger().info(String.format(
            "External native build generation " + getName() + ":" + format + "\n", args));
    }

    @TaskAction
    public void build() throws ProcessException, IOException {
        ThreadRecorder.get().record(ExecutionType.TASK_EXTERNAL_NATIVE_BUILD_GENERATE_JSON_PROCESS,
            new Recorder.Block<Void>() {
                @Override
                public Void call() throws IOException, ProcessException {
                    diagnostic("starting build\n");
                    diagnostic("bringing jsons up-to-date\n");
                    Set<String> abis = ExternalNativeBuildTaskUtils.getAbiFilters(getAbis());
                    for (String abi : abis) {
                        File expectedJson = ExternalNativeBuildTaskUtils.getOutputJson(getJsonFolder(), abi);
                        if (ExternalNativeBuildTaskUtils.shouldRebuildJson(expectedJson)) {
                            diagnostic("rebuilding json '%s'\n", expectedJson);
                            expectedJson.getParentFile().mkdirs();

                            ThreadRecorder.get().record(
                                    ExecutionType.TASK_EXTERNAL_NATIVE_BUILD_GENERATE_JSON_PROCESS_PER_ABI,
                                new Recorder.Block<Void>() {
                                    @Override
                                    public Void call() throws IOException, ProcessException {
                                        createNativeBuildJson(abi, expectedJson);
                                        return null;
                                    }
                                }, new Recorder.Property("abi", abi));

                            if (!expectedJson.exists()) {
                                throw new GradleException(
                                        String.format("Expected json generation to create '%s' but it didn't",
                                                expectedJson));
                            }
                        } else {
                            diagnostic("json '%s' was up-to-date", expectedJson);
                        }
                    }
                    diagnostic("build complete\n");
                    return null;
                }
            }, new Recorder.Property("variantName", getVariantName()));

    }

    /**
     * Derived class implements this method to produce Json model output file
     * @param abi -- the abi to produce JSon for.
     * @param outputJson -- the file to write the JSon to.
     */
    abstract void createNativeBuildJson(@NonNull String abi, @NonNull File outputJson)
            throws ProcessException, IOException;

    @Input
    public File getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(File projectPath) {
        this.projectPath = projectPath;
    }

    @OutputDirectory
    public File getObjFolder() {
        return objFolder;
    }

    public void setObjFolder(File objFolder) {
        this.objFolder = objFolder;
    }

    @OutputDirectory
    public File getSoFolder() {
        return soFolder;
    }

    public void setSoFolder(File soFolder) {
        this.soFolder = soFolder;
    }

    @OutputDirectory
    public File getJsonFolder() {
        return jsonFolder;
    }

    public void setJsonFolder(File jsonFolder) {
        this.jsonFolder = jsonFolder;
    }

    @Input
    public File getNdkFolder() {
        return ndkFolder;
    }

    public void setNdkFolder(File ndkFolder) {
        this.ndkFolder = ndkFolder;
    }

    @Optional
    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Optional
    @Input
    public String getcFlags() {
        return cFlags;
    }

    public void setcFlags(String cFlags) {
        this.cFlags = cFlags;
    }

    public void addcFlags(String cFlags) {
        if (cFlags == null || cFlags.isEmpty()) {
            return;
        }
        if (this.cFlags == null || this.cFlags.isEmpty()) {
            this.cFlags = cFlags;
            return;
        }
        this.cFlags = this.cFlags + " " + cFlags;
    }

    @Optional
    @Input
    public String getCppFlags() {
        return cppFlags;
    }

    public void setCppFlags(String cppFlags) {
        this.cppFlags = cppFlags;
    }

    public void addCppFlags(String cppFlags) {
        if (cppFlags == null || cppFlags.isEmpty()) {
            return;
        }
        if (this.cppFlags == null || this.cppFlags.isEmpty()) {
            this.cppFlags = cppFlags;
            return;
        }
        this.cppFlags = this.cppFlags + " " + cppFlags;
    }

    @Optional
    @Input
    public Set<String> getAbis() {
        return abis;
    }

    public void addAbis(Set<String> abis) {
        if (abis == null || abis.isEmpty()) {
            return;
        }
        this.abis.addAll(abis);
    }

    @OutputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    public void setNativeBuildConfigurationsJsons(
            List<File> nativeBuildConfigurationsJsons) {
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
    }

    protected abstract static class ConfigActionBase {
        private final File projectPath;
        protected final SdkHandler sdkHandler;
        private final AndroidBuilder androidBuilder;
        protected final VariantScope scope;

        ConfigActionBase(
                @NonNull File projectPath,
                @NonNull SdkHandler sdkHandler,
                @NonNull AndroidBuilder androidBuilder,
                @NonNull VariantScope scope) {
            this.projectPath = projectPath;
            this.sdkHandler = sdkHandler;
            this.androidBuilder = androidBuilder;
            this.scope = scope;
        }

        /**
         * Configure the commonalities between CMake and ndk-build external native build tasks.
         */
        protected void initCommon(
                @NonNull ExternalNativeBuildJsonModelGenerationBaseTask task,
                @NonNull VariantScope scope) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            task.setProjectPath(projectPath);
            task.setVariantName(variantData.getName());

            task.setNdkFolder(sdkHandler.getNdkFolder());
            task.setObjFolder(new File(scope.getExternalNativeBuildIntermediatesFolder(), "obj"));
            task.setSoFolder(new File(scope.getExternalNativeBuildIntermediatesFolder(), "lib"));
            task.setJsonFolder(new File(scope.getExternalNativeBuildIntermediatesFolder(), "json"));

            task.setAndroidBuilder(androidBuilder);

            task.setNativeBuildConfigurationsJsons(
                    ExternalNativeBuildTaskUtils.getOutputJsons(task.getJsonFolder(), task.getAbis()));

            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
            ConventionMappingHelper.map(task, "debuggable", new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return variantConfig.getBuildType().isDebuggable();
                }
            });

            Collection<File> soFolder = scope.getExternalNativeBuildSoFolder();
            if (soFolder != null && !soFolder.isEmpty()) {
                File folder = soFolder.iterator().next();
                task.setSoFolder(folder);
            }
        }
    }
}
