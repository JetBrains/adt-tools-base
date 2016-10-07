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

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.VariantType;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base class for transforms that consume ProGuard configuration files.
 *
 * We use this type to configure ProGuard and the built-in shrinker consistently, using the same
 * code.
 */
public abstract class ProguardConfigurable extends Transform {
    private final List<Supplier<Collection<File>>> configurationFiles =
            Lists.newArrayListWithExpectedSize(3);

    private final VariantType variantType;

    ProguardConfigurable(@NonNull VariantScope scope) {
        this.variantType = scope.getVariantData().getType();
    }

    public void setConfigurationFiles(Supplier<Collection<File>> configFiles) {
        configurationFiles.add(configFiles);
    }

    List<File> getAllConfigurationFiles() {
        List<File> files = Lists.newArrayList();
        for (Supplier<Collection<File>> supplier : configurationFiles) {
            files.addAll(supplier.get());
        }
        return files;
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
        Set<Scope> set = Sets.newHashSetWithExpectedSize(5);
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

    public abstract void keep(@NonNull String keep);

    public abstract void dontwarn(@NonNull String dontwarn);
}
