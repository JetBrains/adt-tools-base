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

package com.android.builder.internal.aapt.v1;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptUtils;
import com.android.builder.internal.aapt.AbstractProcessExecutionAapt;
import com.android.builder.model.AaptOptions;
import com.android.builder.png.QueuedCruncher;
import com.android.ide.common.internal.PngException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.resources.Density;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of an interface to the original {@code aapt}. This implementation relies on
 * process execution of {@code aapt}.
 */
public class AaptV1 extends AbstractProcessExecutionAapt {

    /**
     * What mode should PNG be processed?
     */
    public enum PngProcessMode {
        /**
         * All PNGs should be crunched and 9-patch processed.
         */
        ALL {
            @Override
            public boolean shouldProcess(@NonNull File file) {
                return file.getName().endsWith(SdkConstants.DOT_PNG);
            }
        },

        /**
         * PNGs should not be crunched, but 9-patch processed.
         */
        NINE_PATCH_ONLY {
            @Override
            public boolean shouldProcess(@NonNull File file) {
                return file.getName().endsWith(SdkConstants.DOT_9PNG);
            }
        },

        /**
         * PNGs should not be crunched and 9-patch should not be processed.
         */
        NONE {
            @Override
            public boolean shouldProcess(@NonNull File file) {
                return false;
            }
        };

        /**
         * Should a file be processed in this mode?
         *
         * @param file the file
         * @return should it be processed
         */
        public abstract boolean shouldProcess(@NonNull File file);
    }

    /**
     * How much time, in milliseconds, before a wait thread automatically shuts down.
     */
    private static final long AUTO_THREAD_SHUTDOWN_MS = 250;

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
     * Queued cruncher, if available.
     */
    @Nullable
    private final QueuedCruncher mCruncher;

    /**
     * Request handlers we wait for. Everytime a request is made to the {@link #mCruncher},
     * we add an entry here to wait for it to end.
     */
    @NonNull
    private final Executor mWaitExecutor;

    /**
     * The process mode to run {@code aapt} on.
     */
    @NonNull
    private final PngProcessMode mProcessMode;

    /**
     * Creates a new entry point to the original {@code aapt}.
     *
     * @param processExecutor the executor for external processes
     * @param processOutputHandler the handler to process the executed process' output
     * @param buildToolInfo the build tools to use
     * @param logger logger to use
     * @param processMode the process mode to run {@code aapt} on
     */
    public AaptV1(
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull ILogger logger,
            @NonNull PngProcessMode processMode) {
        super(processExecutor, processOutputHandler);

        mBuildToolInfo = buildToolInfo;
        mWaitExecutor = new ThreadPoolExecutor(
                0, // Core threads
                1, // Maximum threads
                AUTO_THREAD_SHUTDOWN_MS,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        mProcessMode = processMode;

        if (buildToolInfo.getRevision().compareTo(VERSION_FOR_SERVER_AAPT) >= 0) {
            mCruncher =
                    QueuedCruncher.Builder.INSTANCE.newCruncher(getAaptExecutablePath(), logger);
        } else {
            mCruncher = null;
        }
    }

    @Override
    @NonNull
    protected ProcessInfoBuilder makePackageProcessBuilder(@NonNull AaptPackageConfig config)
            throws AaptException {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        /*
         * AaptPackageProcessBuilder had this code below, but nothing was ever added to
         * mEnvironment.
         */
        //builder.addEnvironments(mEnvironment);

        builder.setExecutable(getAaptExecutablePath());
        builder.addArgs("package");

        if (config.isVerbose()) {
            builder.addArgs("-v");
        }

        builder.addArgs("-f");
        builder.addArgs("--no-crunch");

        // inputs
        IAndroidTarget target = config.getAndroidTarget();
        Preconditions.checkNotNull(target);
        builder.addArgs("-I", target.getPath(IAndroidTarget.ANDROID_JAR));

        File manifestFile = config.getManifestFile();
        Preconditions.checkNotNull(manifestFile);
        builder.addArgs("-M", manifestFile.getAbsolutePath());

        if (config.getResourceDir() != null) {
            builder.addArgs("-S", config.getResourceDir().getAbsolutePath());
        }

        if (config.getAssetsDir() != null) {
            builder.addArgs("-A", config.getAssetsDir().getAbsolutePath());
        }

        // outputs
        if (config.getSourceOutputDir() != null) {
            builder.addArgs("-m");
            builder.addArgs("-J", config.getSourceOutputDir().getAbsolutePath());
        }

        if (config.getResourceOutputApk() != null) {
            builder.addArgs("-F", config.getResourceOutputApk().getAbsolutePath());
        }

        if (config.getProguardOutputFile()!= null) {
            builder.addArgs("-G", config.getProguardOutputFile().getAbsolutePath());
        }

        if (config.getSplits() != null) {
            for (String split : config.getSplits()) {
                builder.addArgs("--split", split);
            }
        }

        // options controlled by build variants

        if (config.isDebuggable()) {
            builder.addArgs("--debug-mode");
        }

        ILogger logger = config.getLogger();
        Preconditions.checkNotNull(logger);
        if (config.getVariantType() != VariantType.ANDROID_TEST
                && config.getCustomPackageForR() != null) {
            builder.addArgs("--custom-package", config.getCustomPackageForR());
            logger.verbose("Custom package for R class: '%s'", config.getCustomPackageForR());
        }

        if (config.isPseudoLocalize()) {
            Preconditions.checkState(mBuildToolInfo.getRevision().getMajor() >= 21);
            builder.addArgs("--pseudo-localize");
        }

        // library specific options
        if (config.getVariantType() == VariantType.LIBRARY) {
            builder.addArgs("--non-constant-id");
        }

        // AAPT options
        AaptOptions options = config.getOptions();
        Preconditions.checkNotNull(options);
        String ignoreAssets = options.getIgnoreAssets();
        if (ignoreAssets != null) {
            builder.addArgs("--ignore-assets", ignoreAssets);
        }

        if (config.getOptions().getFailOnMissingConfigEntry()) {
            Preconditions.checkState(mBuildToolInfo.getRevision().getMajor() > 20);
            builder.addArgs("--error-on-missing-config-entry");
        }

        /*
         * Never compress apks.
         */
        builder.addArgs("-0", "apk");

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

        if (mBuildToolInfo.getRevision().getMajor() < 21 && config.getPreferredDensity() != null) {
            resourceConfigs.add(config.getPreferredDensity());

            /*
             * when adding a density filter, also always add the nodpi option.
             */
            resourceConfigs.add(Density.NODPI.getResourceValue());
        }


        /*
         * Split the density and language resource configs, since starting in 21, the
         * density resource configs should be passed with --preferred-density to ensure packaging
         * of scalable resources when no resource for the preferred density is present.
         */
        Collection<String> otherResourceConfigs;
        String preferredDensity = null;
        if (mBuildToolInfo.getRevision().getMajor() >= 21) {
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
        } else {
            /*
             * Before build tools v21, everything is passed with -c option.
             */
            otherResourceConfigs = resourceConfigs;
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
            builder.addArgs("--output-text-symbols",
                    config.getSymbolOutputDir().getAbsolutePath());
        }

        // All the vector XML files that are outside of an "-anydpi-v21" directory were left there
        // intentionally, for the support library to consume. Leave them alone.
        if (mBuildToolInfo.getRevision().getMajor() >= 23) {
            builder.addArgs("--no-version-vectors");
        }

        return builder;
    }

    @NonNull
    @Override
    public ListenableFuture<File> compile(@NonNull File file, @NonNull File output)
            throws AaptException {
        if (mCruncher == null) {
            /*
             * Revert to old-style crunching.
             */
            return super.compile(file, output);
        }

        Preconditions.checkArgument(file.isFile(), "!file.isFile()");
        Preconditions.checkArgument(output.isDirectory(), "!output.isDirectory()");

        SettableFuture<File> future = SettableFuture.create();

        if (!mProcessMode.shouldProcess(file)) {
            future.set(null);
            return future;
        }

        File outputFile = compileOutputFor(file, output);

        int key = mCruncher.start();
        try {
            mCruncher.crunchPng(key, file, outputFile);
        } catch (PngException e) {
            throw new AaptException("Failed to crunch file '" + file.getAbsolutePath() + "' "
                    + "into '" + outputFile.getAbsolutePath() + "'.");
        }

        mWaitExecutor.execute(() -> {
            try {
                mCruncher.end(key);
                future.set(outputFile);
            } catch (Exception e) {
                future.setException(e);
            }
        });

        return future;
    }

    @Nullable
    @Override
    protected CompileInvocation makeCompileProcessBuilder(@NonNull File file, @NonNull File output)
            throws AaptException {
        Preconditions.checkArgument(file.isFile(), "!file.isFile()");
        Preconditions.checkArgument(output.isDirectory(), "!directory.isDirectory()");

        if (!file.getName().endsWith(SdkConstants.DOT_PNG)) {
            return null;
        }

        File outputFile = compileOutputFor(file, output);

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(getAaptExecutablePath());
        builder.addArgs("singleCrunch");
        builder.addArgs("-i", file.getAbsolutePath());
        builder.addArgs("-o", outputFile.getAbsolutePath());
        return new CompileInvocation(builder, outputFile);
    }

    /**
     * Obtains the file that will receive the compilation output of a given file. This method
     * will return a unique file in the output directory for each input file.
     *
     * @param file the file
     * @param output the output directory
     * @return the output file
     */
    @NonNull
    private static File compileOutputFor(@NonNull File file, @NonNull File output) {
        Preconditions.checkArgument(file.isFile(), "!file.isFile()");
        Preconditions.checkArgument(output.isDirectory(), "!output.isDirectory()");
        return new File(output, file.getName());
    }

    /**
     * Obtains the path for the {@code aapt} executable.
     *
     * @return the path
     */
    @NonNull
    private String getAaptExecutablePath() {
        String aapt = mBuildToolInfo.getPath(BuildToolInfo.PathId.AAPT);
        if (aapt == null || !new File(aapt).isFile()) {
            throw new IllegalStateException("aapt is missing on '" + aapt + "'");
        }

        return aapt;
    }
}
