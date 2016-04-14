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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An adaptor to convert a managed.BaseConfig to a model.BaseConfig.
 */
public class BaseConfigAdaptor implements BaseConfig {

    @NonNull
    private final com.android.build.gradle.managed.BaseConfig baseConfig;

    public BaseConfigAdaptor(@NonNull com.android.build.gradle.managed.BaseConfig baseConfig) {
        this.baseConfig = baseConfig;
    }

    @NonNull
    @Override
    public String getName() {
        return baseConfig.getName();
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        ImmutableMap.Builder<String, ClassField> builder = ImmutableMap.builder();
        for (com.android.build.gradle.managed.ClassField cf : baseConfig.getBuildConfigFields()) {
            builder.put(
                    cf.getName(),
                    new ClassFieldImpl(
                            cf.getType(),
                            cf.getName(),
                            cf.getValue(),
                            ImmutableSet.copyOf(cf.getAnnotations()),
                            Objects.firstNonNull(cf.getDocumentation(), "")));
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        ImmutableMap.Builder<String, ClassField> builder = ImmutableMap.builder();
        for (com.android.build.gradle.managed.ClassField cf : baseConfig.getResValues()) {
            builder.put(
                    cf.getName(),
                    new ClassFieldImpl(
                            cf.getType(),
                            cf.getName(),
                            cf.getValue(),
                            Objects.firstNonNull(cf.getAnnotations(), ImmutableSet.<String>of()),
                            Objects.firstNonNull(cf.getDocumentation(), "")));
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Collection<File> getProguardFiles() {
        return ImmutableList.copyOf(baseConfig.getProguardFiles());
    }

    @NonNull
    @Override
    public Collection<File> getConsumerProguardFiles() {
        return ImmutableList.copyOf(baseConfig.getConsumerProguardFiles());
    }

    @NonNull
    @Override
    public Collection<File> getTestProguardFiles() {
        return ImmutableList.copyOf(baseConfig.getTestProguardFiles());
    }

    @NonNull
    @Override
    public Map<String, Object> getManifestPlaceholders() {
        // TODO: To be implemented
        return Maps.newHashMap();
    }

    @Nullable
    @Override
    public Boolean getMultiDexEnabled() {
        return baseConfig.getMultiDexEnabled();
    }

    @Nullable
    @Override
    public File getMultiDexKeepFile() {
        return baseConfig.getMultiDexKeepFile();
    }

    @Nullable
    @Override
    public File getMultiDexKeepProguard() {
        return baseConfig.getMultiDexKeepProguard();
    }

    @Nullable
    @Override
    public String getApplicationIdSuffix() {
        return baseConfig.getApplicationIdSuffix();
    }

    @NonNull
    @Override
    public List<File> getJarJarRuleFiles() {
        return ImmutableList.copyOf(baseConfig.getJarJarRuleFiles());
    }
}
