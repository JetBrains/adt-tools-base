/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.annotations.VisibleForTesting
import com.android.builder.AndroidBuilder
import com.android.builder.BuilderConstants
import com.android.builder.DefaultBuildType
import com.android.builder.model.ClassField
import com.android.builder.model.NdkConfig
import com.android.builder.model.SigningConfig
import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator
/**
 * DSL overlay to make methods that accept String... work.
 */
public class BuildTypeDsl extends DefaultBuildType implements Serializable {
    private static final long serialVersionUID = 1L

    @NonNull
    private final FileResolver fileResolver
    @NonNull
    private final Logger logger

    private final NdkConfigDsl ndkConfig


    public BuildTypeDsl(@NonNull String name,
                        @NonNull FileResolver fileResolver,
                        @NonNull Instantiator instantiator,
                        @NonNull Logger logger) {
        super(name)
        this.fileResolver = fileResolver
        this.logger = logger
        ndkConfig = instantiator.newInstance(NdkConfigDsl.class)
    }

    @VisibleForTesting
    BuildTypeDsl(@NonNull String name,
                 @NonNull FileResolver fileResolver,
                 @NonNull Logger logger) {
        super(name)
        this.fileResolver = fileResolver
        this.logger = logger
        ndkConfig = null
    }

    @Override
    @Nullable
    public NdkConfig getNdkConfig() {
        return ndkConfig;
    }

    public void init(SigningConfig debugSigningConfig) {
        if (BuilderConstants.DEBUG.equals(getName())) {
            setDebuggable(true)
            setZipAlign(false)

            assert debugSigningConfig != null
            setSigningConfig(debugSigningConfig)
        } else if (BuilderConstants.RELEASE.equals(getName())) {
            // no config needed for now.
        }
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        if (!super.equals(o)) return false

        return true
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    public void buildConfigField(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getBuildConfigFields().get(name);
        if (alreadyPresent != null) {
            logger.info(
                    "BuildType(${getName()}): buildConfigField '$name' value is being replaced: ${alreadyPresent.value} -> $value");
        }
        addBuildConfigField(AndroidBuilder.createClassField(type, name, value));
    }

    @NonNull
    public BuildTypeDsl proguardFile(Object proguardFile) {
        proguardFiles.add(fileResolver.resolve(proguardFile));
        return this;
    }

    @NonNull
    public BuildTypeDsl proguardFiles(Object... proguardFileArray) {
        proguardFiles.addAll(fileResolver.resolveFiles(proguardFileArray).files);
        return this;
    }

    @NonNull
    public BuildTypeDsl setProguardFiles(Iterable<?> proguardFileIterable) {
        proguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            proguardFiles.add(fileResolver.resolve(proguardFile));
        }
        return this;
    }

    @NonNull
    public BuildTypeDsl consumerProguardFiles(Object... proguardFileArray) {
        consumerProguardFiles.addAll(fileResolver.resolveFiles(proguardFileArray).files);
        return this;
    }

    @NonNull
    public BuildTypeDsl setConsumerProguardFiles(Iterable<?> proguardFileIterable) {
        consumerProguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            consumerProguardFiles.add(fileResolver.resolve(proguardFile));
        }
        return this;
    }

    void ndk(Action<NdkConfigDsl> action) {
        action.execute(ndkConfig)
    }
}
