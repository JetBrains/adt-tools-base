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
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does build steps with them.
 */
public class ExternalNativeBuildTask extends BaseTask {

    private List<File> nativeBuildConfigurationsJsons;
    private File soFolder;
    private File objFolder;

    @TaskAction
    void build() throws ProcessException, IOException {
        diagnostic("starting build\n");
        diagnostic("bringing jsons up-to-date\n");
        Preconditions.checkNotNull(getVariantName());
        Collection<NativeBuildConfigValue> configValueList = ExternalNativeBuildTaskUtils
                .getNativeBuildConfigValues(
                        nativeBuildConfigurationsJsons, getVariantName());
        List<String> buildCommands = Lists.newArrayList();

         diagnostic("executing build commands in jsons\n");
        for (NativeBuildConfigValue config : configValueList) {
            if (config.libraries == null) {
                continue;
            }
            for (String libraryName : config.libraries.keySet()) {
                NativeLibraryValue libraryValue = config.libraries.get(libraryName);
                buildCommands.add(libraryValue.buildCommand);
                diagnostic("About to build %s\n", libraryValue.buildCommand);
            }
        }
        executeProcessBatch(buildCommands);

        diagnostic("copying build outputs from json-defined locations to expected locations\n");
        for (NativeBuildConfigValue config : configValueList) {
            if (config.libraries == null) {
                continue;
            }
            for (String libraryName : config.libraries.keySet()) {
                NativeLibraryValue libraryValue = config.libraries.get(libraryName);
                Preconditions.checkNotNull(libraryValue);
                Preconditions.checkNotNull(libraryValue.output);
                if (!libraryValue.output.exists()) {
                    throw new GradleException(
                            String.format("Expected output file at %s but there was none",
                                    libraryValue.output));
                }
                if (libraryValue.abi == null) {
                    throw new GradleException("Expected NativeLibraryValue to have non-null abi");
                }

                File destinationFolder = new File(getSoFolder(), libraryValue.abi);
                File destinationFile = new File(destinationFolder, libraryValue.output.getName());

                // If external tool chose to write to the expected location then no need to copy.
                if (destinationFile.getCanonicalPath().equals(
                        libraryValue.output.getCanonicalPath())) {
                    diagnostic(
                            "not copying output file because it is already in prescribed location: %s\n",
                            libraryValue.output);
                    continue;
                }
                if (destinationFolder.mkdirs()) {
                    diagnostic("created folder %s\n", destinationFolder);
                }
                diagnostic("copy from %s to %s\n", libraryValue.output, destinationFile);
                Files.copy(libraryValue.output, destinationFile);
            }
        }

         diagnostic("build complete\n");
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private void executeProcessBatch(@NonNull List<String> commands) throws ProcessException {
        for (String command : commands) {
            List<String> tokens = StringHelper.tokenizeString(command);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            diagnostic("%s\n", processBuilder);
            ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError(
                    getBuilder(),
                    processBuilder.createProcess());
        }
    }

    /**
     * Log low level diagnostic information.
     */
    private void diagnostic(String format, Object... args) {
        getLogger().info(
                String.format("External native build " + getName() + ":" + format + "\n", args));
    }

    public File getSoFolder() {
        return soFolder;
    }

    private void setSoFolder(File soFolder) {
        this.soFolder = soFolder;
    }

    @NonNull
    @SuppressWarnings("unused")
    public File getObjFolder() {
        return objFolder;
    }

    private void setObjFolder(File objFolder) {
        this.objFolder = objFolder;
    }

    @SuppressWarnings("unused")
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    private void setNativeBuildConfigurationsJsons(
            List<File> nativeBuildConfigurationsJsons) {
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
    }

    public static class ConfigAction implements TaskConfigAction<ExternalNativeBuildTask> {
        @NonNull
        private final ExternalNativeJsonGenerator generator;
        @NonNull
        private final VariantScope scope;
        @NonNull
        private final AndroidBuilder androidBuilder;

        public ConfigAction(
                @NonNull ExternalNativeJsonGenerator generator,
                @NonNull VariantScope scope,
                @NonNull AndroidBuilder androidBuilder) {
            this.generator = generator;
            this.scope = scope;
            this.androidBuilder = androidBuilder;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("externalNativeBuild");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildTask> getType() {
            return ExternalNativeBuildTask.class;
        }

        @Override
        public void execute(@NonNull ExternalNativeBuildTask task) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            task.setVariantName(variantData.getName());
            task.setSoFolder(generator.getSoFolder());
            task.setObjFolder(generator.getObjFolder());
            task.setNativeBuildConfigurationsJsons(generator.getNativeBuildConfigurationsJsons());
            task.setAndroidBuilder(androidBuilder);
        }
    }
}
