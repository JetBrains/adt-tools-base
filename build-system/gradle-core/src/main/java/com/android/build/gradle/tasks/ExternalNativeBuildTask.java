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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does build steps with them.
 */
public class ExternalNativeBuildTask extends ExternalNativeBaseTask {

    private List<File> nativeBuildConfigurationsJsons;

    private File soFolder;

    private File objFolder;

    private Set<String> targets;

    @TaskAction
    void build() throws ProcessException, IOException {
        diagnostic("starting build");
        diagnostic("bringing JSON up-to-date");
        checkNotNull(getVariantName());
        Collection<NativeBuildConfigValue> configValueList = ExternalNativeBuildTaskUtils
                .getNativeBuildConfigValues(
                        nativeBuildConfigurationsJsons, getVariantName());
        List<String> buildCommands = Lists.newArrayList();
        List<String> libraryNames = Lists.newArrayList();

        // Check the resulting JSON targets against the targets specified in ndkBuild.targets or
        // cmake.targets. If a target name specified by the user isn't present then provide an
        // error to the user that lists
        if (!targets.isEmpty()) {
            diagnostic("executing build commands for targets: '%s'", Joiner.on(", ").join(targets));

            // Search libraries for matching targets.
            Set<String> matchingTargets = Sets.newHashSet();
            Set<String> unmatchedTargets = Sets.newHashSet();
            for (NativeBuildConfigValue config : configValueList) {
                if (config.libraries == null) {
                    continue;
                }
                for (NativeLibraryValue libraryValue : config.libraries.values()) {
                    if (targets.contains(libraryValue.artifactName)) {
                        matchingTargets.add(libraryValue.artifactName);
                    } else {
                        unmatchedTargets.add(libraryValue.artifactName);
                    }
                }
            }

            // All targets must be found or it's a build error
            for (String target : targets) {
                if (!matchingTargets.contains(target)) {
                    throw new GradleException(
                            String.format("Unexpected native build target %s. Valid values are: %s",
                                    target, Joiner.on(", ").join(unmatchedTargets)));
                }
            }
        }

        for (NativeBuildConfigValue config : configValueList) {
            if (config.libraries == null) {
                continue;
            }
            for (String libraryName : config.libraries.keySet()) {
                NativeLibraryValue libraryValue = config.libraries.get(libraryName);
                if (!targets.isEmpty() && !targets.contains(libraryValue.artifactName)) {
                    diagnostic("not building target %s because it isn't in targets set",
                      libraryValue.artifactName);
                    continue;
                }
                if (Strings.isNullOrEmpty(libraryValue.buildCommand)) {
                    // This can happen when there's an externally referenced library.
                    diagnostic("not building target %s because there was no build command for it",
                            libraryValue.artifactName);
                    continue;

                }
                buildCommands.add(libraryValue.buildCommand);
                libraryNames.add(checkNotNull(libraryValue.output).getPath());
                diagnostic("about to build %s", libraryValue.buildCommand);
            }
        }
        executeProcessBatch(libraryNames, buildCommands);

        diagnostic("copying build outputs from JSON-defined locations to expected locations");
        for (NativeBuildConfigValue config : configValueList) {
            if (config.libraries == null) {
                continue;
            }
            for (String libraryName : config.libraries.keySet()) {
                NativeLibraryValue libraryValue = config.libraries.get(libraryName);
                checkNotNull(libraryValue);
                checkNotNull(libraryValue.output);
                checkState(!Strings.isNullOrEmpty(libraryValue.artifactName));
                if (!targets.isEmpty() && !targets.contains(libraryValue.artifactName)) {
                    continue;
                }
                if (!libraryValue.output.exists()) {
                    throw new GradleException(
                            String.format("Expected output file at %s for target %s"
                                    + " but there was none",
                                    libraryValue.output, libraryValue.artifactName));
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
                            "not copying output file because it is already in prescribed location: %s",
                            libraryValue.output);
                    continue;
                }
                if (destinationFolder.mkdirs()) {
                    diagnostic("created folder %s", destinationFolder);
                }
                diagnostic("copy from %s to %s", libraryValue.output, destinationFile);
                Files.copy(libraryValue.output, destinationFile);
            }
        }

         diagnostic("build complete");
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    protected void executeProcessBatch(
            @NonNull List<String> libraryNames,
            @NonNull List<String> commands) throws ProcessException {
        // Order of building doesn't matter to final result but building in reverse order causes
        // the dependencies to be built first for CMake and ndk-build. This gives better progress
        // visibility to the user because they will see "building XXXXX.a" before
        // "building XXXXX.so". Nicer still would be to have the dependency information in the JSON
        // so we can build in toposort order.
        for (int library = libraryNames.size() - 1; library >= 0; --library) {
            String libraryName = libraryNames.get(library);
            getLogger().lifecycle(String.format("  building %s", libraryName));
            String command = commands.get(library);
            List<String> tokens = StringHelper.tokenizeString(command);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            diagnostic("%s", processBuilder);
            ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError(
                    getBuilder(),
                    processBuilder.createProcess());
        }
    }

    @NonNull
    public File getSoFolder() {
        return soFolder;
    }

    private void setSoFolder(@NonNull File soFolder) {
        this.soFolder = soFolder;
    }

    private void setTargets(@NonNull Set<String> targets) {
        this.targets = targets;
    }

    @NonNull
    @SuppressWarnings("unused")
    public File getObjFolder() {
        return objFolder;
    }

    private void setObjFolder(@NonNull
            File objFolder) {
        this.objFolder = objFolder;
    }

    @NonNull
    @SuppressWarnings("unused")
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    private void setNativeBuildConfigurationsJsons(
            @NonNull List<File> nativeBuildConfigurationsJsons) {
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
    }

    public static class ConfigAction implements TaskConfigAction<ExternalNativeBuildTask> {
        @Nullable
        private final String buildTargetAbi;
        @NonNull
        private final ExternalNativeJsonGenerator generator;
        @NonNull
        private final VariantScope scope;
        @NonNull
        private final AndroidBuilder androidBuilder;

        public ConfigAction(
                @Nullable String buildTargetAbi,
                @NonNull ExternalNativeJsonGenerator generator,
                @NonNull VariantScope scope,
                @NonNull AndroidBuilder androidBuilder) {
            this.buildTargetAbi = buildTargetAbi;
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
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final Set<String> targets;
            CoreExternalNativeBuildOptions nativeBuildOptions =
                    variantData.getVariantConfiguration().getExternalNativeBuildOptions();
            switch (generator.getNativeBuildSystem()) {
                case CMAKE: {
                    CoreExternalNativeCmakeOptions options = checkNotNull(nativeBuildOptions
                            .getExternalNativeCmakeOptions());
                    targets = options.getTargets();
                    break;
                }
                case NDK_BUILD: {
                    CoreExternalNativeNdkBuildOptions options = checkNotNull(nativeBuildOptions
                            .getExternalNativeNdkBuildOptions());
                    targets = options.getTargets();
                    break;
                }
                default:
                    throw new RuntimeException("Unexpected native build system "
                            + generator.getNativeBuildSystem().getName());
            }
            task.setTargets(targets);
            task.setVariantName(variantData.getName());
            task.setSoFolder(generator.getSoFolder());
            task.setObjFolder(generator.getObjFolder());
            if (Strings.isNullOrEmpty(buildTargetAbi)) {
                task.setNativeBuildConfigurationsJsons(
                        generator.getNativeBuildConfigurationsJsons());
            } else {
                // Android Studio has requested a particular ABI to build or the user has specified
                // one from the command-line like with: -Pandroid.injected.build.abi=x86
                //
                // In this case, the requested ABI overrides and abiFilters in the variantConfig.
                // So this can build ABIs that aren't specified in any variant.
                //
                // It is possible for multiple ABIs to be passed through buildTargetAbi. In this
                // case, take the first. It is preferred.
                List<File> expectedJson = ExternalNativeBuildTaskUtils.getOutputJsons(
                            generator.getJsonFolder(),
                            Arrays.asList(buildTargetAbi.split(",")));
                // Remove JSONs that won't be created by the generator.
                expectedJson.retainAll(generator.getNativeBuildConfigurationsJsons());
                // If no JSONs remain then issue a warning and proceed with no-op build.
                if (expectedJson.isEmpty()) {
                    androidBuilder.getErrorReporter().handleSyncWarning(
                            scope.getFullVariantName(),
                            SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                            String.format("Targeted device ABI or comma-delimited ABIs [%s] is not"
                                    + " one of [%s]. Nothing to build.",
                                    buildTargetAbi,
                                    Joiner.on(", ").join(generator.getAbis().stream()
                                            .map(Abi::getName)
                                            .collect(Collectors.toList()))));
                } else {
                    // Take the first JSON that matched the build configuration
                    task.setNativeBuildConfigurationsJsons(
                            Lists.newArrayList(expectedJson.iterator().next()));
                }
            }

            task.setAndroidBuilder(androidBuilder);
            variantData.externalNativeBuildTasks.add(task);
        }
    }
}
