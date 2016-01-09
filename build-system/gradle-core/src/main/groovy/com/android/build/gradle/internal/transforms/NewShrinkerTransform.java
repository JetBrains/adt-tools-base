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

package com.android.build.gradle.internal.transforms;

import static com.android.build.gradle.shrinker.AbstractShrinker.logTime;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.shrinker.AbstractShrinker.CounterSet;
import com.android.build.gradle.shrinker.FullRunShrinker;
import com.android.build.gradle.shrinker.IncrementalShrinker;
import com.android.build.gradle.shrinker.JavaSerializationShrinkerGraph;
import com.android.build.gradle.shrinker.KeepRules;
import com.android.build.gradle.shrinker.ProguardConfig;
import com.android.build.gradle.shrinker.ProguardFlagsKeepRules;
import com.android.build.gradle.shrinker.ShrinkerLogger;
import com.android.builder.core.VariantType;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.gradle.tooling.BuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Transform that performs shrinking - only reachable methods in reachable class files are copied
 * into the output folders (one per stream).
 */
public class NewShrinkerTransform extends ProguardConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(NewShrinkerTransform.class);
    private static final String NAME = "newClassShrinker";

    private final VariantType variantType;
    private final Set<File> platformJars;
    private final File incrementalDir;
    private final List<String> dontwarnLines;
    private final List<String> keepLines;

    public NewShrinkerTransform(@NonNull VariantScope scope) {
        this.platformJars = ImmutableSet.copyOf(
                scope.getGlobalScope().getAndroidBuilder().getBootClasspath(true));
        this.variantType = scope.getVariantData().getType();
        this.incrementalDir = scope.getIncrementalDir(scope.getTaskName(NAME));
        this.dontwarnLines = Lists.newArrayList();
        this.keepLines = Lists.newArrayList();
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        if (variantType == VariantType.LIBRARY) {
            return Sets.immutableEnumSet(Scope.PROJECT, Scope.PROJECT_LOCAL_DEPS);
        }

        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        Set<Scope> set = Sets.newLinkedHashSetWithExpectedSize(5);
        if (variantType == VariantType.LIBRARY) {
            set.add(Scope.SUB_PROJECTS);
            set.add(Scope.SUB_PROJECTS_LOCAL_DEPS);
            set.add(Scope.EXTERNAL_LIBRARIES);
        }

        if (variantType.isForTesting()) {
            set.add(Scope.TESTED_CODE);
        }

        set.add(Scope.PROVIDED_ONLY);

        return Sets.immutableEnumSet(set);
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return getAllConfigurationFiles();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(incrementalDir);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        TransformOutputProvider output = invocation.getOutputProvider();
        Collection<TransformInput> referencedInputs = invocation.getReferencedInputs();

        checkNotNull(output, "Missing output object for transform " + getName());

        if (isIncrementalRun(invocation.isIncremental(), referencedInputs)) {
            incrementalRun(invocation.getInputs(), referencedInputs, output);
        } else {
            fullRun(invocation.getInputs(), referencedInputs, output);
        }

    }

    private void fullRun(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @NonNull TransformOutputProvider output) throws IOException {
        ProguardConfig config = getConfig();

        ShrinkerLogger shrinkerLogger =
                new ShrinkerLogger(config.getFlags().getDontWarnSpecs(), logger);

        FullRunShrinker<String> shrinker =
                new FullRunShrinker<String>(
                        new WaitableExecutor<Void>(),
                        JavaSerializationShrinkerGraph.empty(incrementalDir),
                        platformJars,
                        shrinkerLogger);

        // Only save state if incremental mode is enabled.
        boolean saveState = this.isIncremental();

        shrinker.run(
                inputs,
                referencedInputs,
                output,
                ImmutableMap.<CounterSet, KeepRules>of(
                        CounterSet.SHRINK,
                        new ProguardFlagsKeepRules(config.getFlags(), shrinkerLogger)),
                saveState);

        checkForWarnings(config, shrinkerLogger);
    }

    private static void checkForWarnings(
            @NonNull ProguardConfig config,
            @NonNull ShrinkerLogger shrinkerLogger) {
        if (shrinkerLogger.getWarningsCount() > 0 && !config.getFlags().isIgnoreWarnings()) {
            throw new BuildException(
                    "Warnings found during shrinking, please use -dontwarn or -ignorewarnings to suppress them.",
                    null);
        }
    }

    @NonNull
    private ProguardConfig getConfig() throws IOException {
        ProguardConfig config = new ProguardConfig();

        for (File configFile : getAllConfigurationFiles()) {
            config.parse(configFile);
        }

        config.parse(getAdditionalConfigString());
        return config;
    }

    @NonNull
    private String getAdditionalConfigString() {
        StringBuilder sb = new StringBuilder();

        for (String keepLine : keepLines) {
            sb.append("-keep ");
            sb.append(keepLine);
            sb.append("\n");
        }

        for (String dontWarn : dontwarnLines) {
            sb.append("-dontwarn ");
            sb.append(dontWarn);
            sb.append("\n");
        }

        return sb.toString();
    }

    private void incrementalRun(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @NonNull TransformOutputProvider output) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        JavaSerializationShrinkerGraph graph =
                JavaSerializationShrinkerGraph.readFromDir(incrementalDir);
        logTime("loading state", stopwatch);

        ProguardConfig config = getConfig();

        ShrinkerLogger shrinkerLogger =
                new ShrinkerLogger(config.getFlags().getDontWarnSpecs(), logger);

        IncrementalShrinker<String> shrinker =
                new IncrementalShrinker<String>(new WaitableExecutor<Void>(), graph, shrinkerLogger);

        try {
            shrinker.incrementalRun(inputs, output);
            checkForWarnings(config, shrinkerLogger);
        } catch (IncrementalShrinker.IncrementalRunImpossibleException e) {
            logger.warn("Incremental shrinker run impossible: " + e.getMessage());
            fullRun(inputs, referencedInputs, output);
        }
    }

    private static boolean isIncrementalRun(
            boolean isIncremental,
            @NonNull Collection<TransformInput> referencedInputs) {
        if (!isIncremental) {
            return false;
        }

        for (TransformInput referencedInput : referencedInputs) {
            for (JarInput jarInput : referencedInput.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    return false;
                }
            }

            for (DirectoryInput directoryInput : referencedInput.getDirectoryInputs()) {
                if (!directoryInput.getChangedFiles().isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void keep(@NonNull String keep) {
        this.keepLines.add(keep);
    }

    @Override
    public void dontwarn(@NonNull String dontwarn) {
        this.dontwarnLines.add(dontwarn);
    }
}
