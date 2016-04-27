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
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.StringHelper;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does build steps with them.
 */
public class ExternalNativeBuildTask extends BaseTask {

    private List<File> nativeBuildConfigurationsJsons;
    private File soFolder;

    @TaskAction
    void build() throws ProcessException, IOException {
        diagnostic("starting build\n");
        diagnostic("bringing jsons up-to-date\n");
        List<NativeBuildConfigValue> configValueList = getOrCreateNativeBuildConfigValues();
        List<String> buildCommands = Lists.newArrayList();

         diagnostic("executing build commands in jsons\n");
        for (NativeBuildConfigValue config : configValueList) {
            for (String libraryName : config.libraries.keySet()) {
                NativeLibraryValue libraryValue = config.libraries.get(libraryName);
                buildCommands.add(libraryValue.buildCommand);
                diagnostic("About to build %s\n", libraryValue.buildCommand);
            }
        }
        executeProcessBatch(buildCommands);

        diagnostic("copying build outputs from json-defined locations to expected locations\n");
        for (NativeBuildConfigValue config : configValueList) {
            for (String libraryName : config.libraries.keySet()) {
                NativeLibraryValue libraryValue = config.libraries.get(libraryName);
                if (!libraryValue.output.exists()) {
                    throw new GradleException(
                            String.format("Expected output file at %s but there was none",
                                    libraryValue.output));
                }
                if (libraryValue.abi == null) {
                    throw new GradleException(
                            String.format("Expected NativeLibraryValue to have non-null abi: %s",
                                    libraryValue.abi));
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
                destinationFolder.mkdirs();
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
    private void executeProcessBatch(List<String> commands) throws ProcessException {
        for (String command : commands) {
            List<String> tokens = StringHelper.tokenizeString(command);
            ProcessInfoBuilder builder = new ProcessInfoBuilder();
            builder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                builder.addArgs(tokens.get(i));
            }
            diagnostic("%s\n", builder);
            ProcessOutputHandler handler = new LoggedProcessOutputHandler(
                    getBuilder().getLogger());
            getBuilder().executeProcess(builder.createProcess(), handler)
                    .rethrowFailure().assertNormalExitValue();
        }
    }

    /**
     Return the list of NativeBuildConfigValue that correspond to this build task.
     JSON files will be regenerated if necessary to achieve this.
     */
    private List<NativeBuildConfigValue> getOrCreateNativeBuildConfigValues()
            throws ProcessException, IOException {
        return nativeBuildConfigurationsJsons.stream().map(this::getNativeBuildConfigValue)
                .collect(Collectors.toList());
    }

    /**
     * Deserialize a JSON file into NativeBuildConfigValue. Emit task-specific exception if there is
     * an issue.
     */
    private NativeBuildConfigValue getNativeBuildConfigValue(File json) {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                    .create();
            List<String> lines = Files.readLines(json, Charsets.UTF_8);
            return gson.fromJson(Joiner.on("\n").join(lines), NativeBuildConfigValue.class);
        } catch (Throwable e) {
            throw new GradleException(
                    String.format("Could not parse '%s'", json), e);
        }
    }

    /**
     * Log low level diagnostic information.
     */
    protected void diagnostic(String format, Object... args) {
        getLogger().info(
                String.format("External native build " + getName() + ":" + format + "\n", args));
    }

    @OutputDirectory
    public File getSoFolder() {
        return soFolder;
    }

    public void setSoFolder(File soFolder) {
        this.soFolder = soFolder;
    }


    @InputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    public void setNativeBuildConfigurationsJsons(
            List<File> nativeBuildConfigurationsJsons) {
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
    }

    public static class ConfigAction implements TaskConfigAction<ExternalNativeBuildTask> {
        private VariantScope scope;
        private final AndroidBuilder androidBuilder;

        public ConfigAction(@NonNull VariantScope scope, @NonNull AndroidBuilder androidBuilder) {
            this.scope = scope;
            this.androidBuilder = androidBuilder;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("ExternalNativeBuild");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildTask> getType() {
            return ExternalNativeBuildTask.class;
        }

        @Override
        public void execute(@NonNull ExternalNativeBuildTask task) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
            task.setVariantName(variantData.getName());
            Set<String> abis = ExternalNativeBuildTaskUtils.getAbiFilters(
                    variantConfig.getExternalNativeAbiFilters());
            File soFolder = new File(scope.getExternalNativeBuildIntermediatesFolder(),  "lib");
            File jsonFolder = new File(scope.getExternalNativeBuildIntermediatesFolder(),  "json");
            task.setSoFolder(soFolder);
            task.setNativeBuildConfigurationsJsons(
                    ExternalNativeBuildTaskUtils.getOutputJsons(jsonFolder, abis));
            task.setAndroidBuilder(androidBuilder);
        }
    }
}
