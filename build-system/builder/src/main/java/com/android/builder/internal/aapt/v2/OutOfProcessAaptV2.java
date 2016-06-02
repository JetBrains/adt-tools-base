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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptUtils;
import com.android.builder.internal.aapt.AbstractProcessExecutionAapt;
import com.android.builder.model.AaptOptions;
import com.android.builder.png.QueuedCruncher;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}.
 */
public class OutOfProcessAaptV2 extends AbstractProcessExecutionAapt {

    /**
     * Buildtools version for which {@code aapt} can run in server mode and, therefore,
     * {@link QueuedCruncher} can be used.
     */
    private static final Revision VERSION_FOR_SERVER_AAPT = new Revision(22, 0, 0);

    /**
     * Build tools.
     */
    @NonNull
    private final BuildToolInfo mBuildToolInfo;

    /**
     * Directory where to store intermediate files.
     */
    @NonNull
    private final File mIntermediateDir;

    /**
     * Creates a new entry point to the original {@code aapt}.
     *
     * @param processExecutor the executor for external processes
     * @param processOutputHandler the handler to process the executed process' output
     * @param buildToolInfo the build tools to use
     * @param intermediateDir directory where to store intermediate files
     * @param logger logger to use
     */
    public OutOfProcessAaptV2(
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull File intermediateDir,
            @NonNull ILogger logger) {
        super(processExecutor, processOutputHandler);

        Preconditions.checkArgument(
                intermediateDir.isDirectory(),
                "!intermediateDir.isDirectory()");

        mBuildToolInfo = buildToolInfo;
        mIntermediateDir = intermediateDir;
    }

    @Nullable
    @Override
    protected CompileInvocation makeCompileProcessBuilder(
            @NonNull File file, @NonNull File output) throws AaptException {
        Preconditions.checkArgument(file.isFile(), "!file.isFile()");
        Preconditions.checkArgument(output.isDirectory(), "!output.isDirectory()");

        return new CompileInvocation(
                new ProcessInfoBuilder()
                        .setExecutable(getAapt2ExecutablePath())
                        .addArgs("compile")
                        .addArgs("-o")
                        .addArgs(output.getAbsolutePath())
                        .addArgs(file.getAbsolutePath()),
                new File(output, Aapt2RenamingConventions.compilationRename(file)));
    }

    @NonNull
    @Override
    protected ProcessInfoBuilder makePackageProcessBuilder(@NonNull AaptPackageConfig config)
            throws AaptException {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        builder.setExecutable(getAapt2ExecutablePath());
        builder.addArgs("link");

        if (config.isVerbose()) {
            builder.addArgs("-v");
        }

        File stableResourceIdsFile = new File(mIntermediateDir, "stable-resource-ids.txt");
        // TODO: For now, we ignore this file, but as soon as aapt2 supports it, we'll use it.

        // inputs
        IAndroidTarget target = config.getAndroidTarget();
        Preconditions.checkNotNull(target);
        builder.addArgs("-I", target.getPath(IAndroidTarget.ANDROID_JAR));

        File manifestFile = config.getManifestFile();
        Preconditions.checkNotNull(manifestFile);
        builder.addArgs("--manifest", manifestFile.getAbsolutePath());

        if (config.getResourceDir() != null) {
            // TODO: Fix when aapt 2 supports -R directories (http://b.android.com/209331)
            // builder.addArgs("-R", config.getResourceDir().getAbsolutePath());
            try {
                Files.walk(config.getResourceDir().toPath())
                        .filter(Files::isRegularFile)
                        .forEach((p) -> builder.addArgs("-R", p.toString()));
            } catch (IOException e) {
                throw new AaptException("Failed to walk path " + config.getResourceDir());
            }
        }

        builder.addArgs("--auto-add-overlay");

        // outputs
        if (config.getSourceOutputDir() != null) {
            builder.addArgs("--java", config.getSourceOutputDir().getAbsolutePath());
        }

        if (config.getResourceOutputApk() != null) {
            builder.addArgs("-o", config.getResourceOutputApk().getAbsolutePath());
        } else {
            throw new AaptException("No output apk defined.");
        }

        if (config.getProguardOutputFile()!= null) {
            builder.addArgs("--proguard", config.getProguardOutputFile().getAbsolutePath());
        }

        if (config.getSplits() != null) {
            for (String split : config.getSplits()) {
//                builder.addArgs("--split", split);
            }
        }

        // options controlled by build variants

        if (config.isDebuggable()) {
//            builder.addArgs("--debug-mode");
        }

        ILogger logger = config.getLogger();
        Preconditions.checkNotNull(logger);
        if (config.getVariantType() != VariantType.ANDROID_TEST
                && config.getCustomPackageForR() != null) {
            builder.addArgs("--custom-package", config.getCustomPackageForR());
        }

        if (config.isPseudoLocalize()) {
            Preconditions.checkState(mBuildToolInfo.getRevision().getMajor() >= 21);
//            builder.addArgs("--pseudo-localize");
        }

        // library specific options
        if (config.getVariantType() == VariantType.LIBRARY) {
            builder.addArgs("--static-lib");        // --non-constant-id
        }

        // AAPT options
        AaptOptions options = config.getOptions();
        Preconditions.checkNotNull(options);
        String ignoreAssets = options.getIgnoreAssets();
        if (ignoreAssets != null) {
//            builder.addArgs("--ignore-assets", ignoreAssets);
        }

        if (config.getOptions().getFailOnMissingConfigEntry()) {
            Preconditions.checkState(mBuildToolInfo.getRevision().getMajor() > 20);
//            builder.addArgs("--error-on-missing-config-entry");
        }

        /*
         * Never compress apks.
         */
//        builder.addArgs("-0", "apk");

        /*
         * Add custom no-compress extensions.
         */
        Collection<String> noCompressList = config.getOptions().getNoCompress();
        if (noCompressList != null) {
            for (String noCompress : noCompressList) {
                builder.addArgs("-0", noCompress);
            }
        }
        List<String> additionalParameters = config.getOptions().getAdditionalParameters();
        if (additionalParameters != null) {
            builder.addArgs(additionalParameters);
        }

        List<String> resourceConfigs = new ArrayList<String>();
        resourceConfigs.addAll(config.getResourceConfigs());

        /*
         * Split the density and language resource configs, since starting in 21, the
         * density resource configs should be passed with --preferred-density to ensure packaging
         * of scalable resources when no resource for the preferred density is present.
         */
        Collection<String> otherResourceConfigs;
        String preferredDensity = null;
        Collection<String> densityResourceConfigs = Lists.newArrayList(
                AaptUtils.getDensityResConfigs(resourceConfigs));
        otherResourceConfigs = Lists.newArrayList(AaptUtils.getNonDensityResConfigs(
                resourceConfigs));
        preferredDensity = config.getPreferredDensity();

        if (preferredDensity != null && !densityResourceConfigs.isEmpty()) {
            throw new AaptException(
                    String.format("When using splits in tools 21 and above, "
                                    + "resConfigs should not contain any densities. Right now, it "
                                    + "contains \"%1$s\"\nSuggestion: remove these from resConfigs "
                                    + "from build.gradle",
                            Joiner.on("\",\"").join(densityResourceConfigs)));
        }

        if (densityResourceConfigs.size() > 1) {
            throw new AaptException("Cannot filter assets for multiple densities using "
                    + "SDK build tools 21 or later. Consider using apk splits instead.");
        }

        if (preferredDensity == null && densityResourceConfigs.size() == 1) {
            preferredDensity = Iterables.getOnlyElement(densityResourceConfigs);
        }

        if (!otherResourceConfigs.isEmpty()) {
            Joiner joiner = Joiner.on(',');
            builder.addArgs("-c", joiner.join(otherResourceConfigs));
        }

        if (preferredDensity != null) {
            builder.addArgs("--preferred-density", preferredDensity);
        }

        if (config.getSymbolOutputDir() != null && (config.getVariantType() == VariantType.LIBRARY
                || !config.getLibraries().isEmpty())) {
//            builder.addArgs("--output-text-symbols",
//                    config.getSymbolOutputDir().getAbsolutePath());
        }

        builder.addArgs("--no-version-vectors");

        return builder;
    }

    /**
     * Obtains the path for the {@code aapt} executable.
     *
     * @return the path
     */
    @NonNull
    private String getAapt2ExecutablePath() {
        String aapt2 = mBuildToolInfo.getPath(BuildToolInfo.PathId.AAPT2);
        if (aapt2 == null || !new File(aapt2).isFile()) {
            throw new IllegalStateException("aapt2 is missing on '" + aapt2 + "'");
        }

        return aapt2;
    }
}
