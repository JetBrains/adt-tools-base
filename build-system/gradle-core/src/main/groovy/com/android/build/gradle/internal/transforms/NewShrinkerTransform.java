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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.core.VariantType;
import com.android.builder.shrinker.JavaSerializationShrinkerGraph;
import com.android.builder.shrinker.ProguardConfigKeepRulesBuilder;
import com.android.builder.shrinker.Shrinker;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * Transform that performs shrinking - only reachable methods in reachable class files are copied
 * into the output folders (one per stream).
 */
public class NewShrinkerTransform extends ProguardConfigurable {

    private static final String NAME = "newClassShrinker";

    private final VariantType variantType;
    private final File platformJar;
    private final File incrementalDir;

    public NewShrinkerTransform(VariantScope scope) {
        IAndroidTarget target = scope.getGlobalScope().getAndroidBuilder().getTarget();
        checkState(target != null, "SDK target not ready.");
        this.platformJar = new File(target.getPath(IAndroidTarget.ANDROID_JAR));
        this.variantType = scope.getVariantData().getType();
        this.incrementalDir = scope.getIncrementalDir(scope.getTaskName(NAME));
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
            throw new IllegalStateException("New class shrinker is not supported in test variants.");
        }

        set.add(Scope.PROVIDED_ONLY);

        return Sets.immutableEnumSet(set);
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return ImmutableList.<File>builder()
                .addAll(getAllConfigurationFiles())
                .add(this.incrementalDir)
                .build();
    }

    @Override
    public boolean isIncremental() {
        // TODO: Make it incremental.
        return false;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        Shrinker<String> shrinker = new Shrinker<String>(
                new WaitableExecutor<Void>(),
                new JavaSerializationShrinkerGraph(incrementalDir),
                platformJar);

        ProguardConfigKeepRulesBuilder parser = new ProguardConfigKeepRulesBuilder();

        for (File configFile : getAllConfigurationFiles()) {
            parser.parse(configFile);
        }

        shrinker.run(
                inputs,
                referencedInputs,
                outputProvider,
                // TODO: Multidex class list.
                ImmutableMap.of(Shrinker.ShrinkType.SHRINK, parser.getKeepRules()),
                isIncremental);
    }
}
