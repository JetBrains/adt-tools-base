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
import com.android.build.gradle.internal.core.NdkConfig
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.model.BaseConfig
import com.android.builder.model.ClassField
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator

/**
 * DSL object to configure build types.
 */
public class BuildType extends DefaultBuildType implements CoreBuildType, Serializable {
    private static final long serialVersionUID = 1L

    @NonNull
    private final Project project
    @NonNull
    private final Logger logger

    private final NdkOptions ndkConfig

    private Boolean useJack

    public BuildType(@NonNull String name,
                     @NonNull Project project,
                     @NonNull Instantiator instantiator,
                     @NonNull Logger logger) {
        super(name)
        this.project = project
        this.logger = logger
        ndkConfig = instantiator.newInstance(NdkOptions.class)
    }

    @VisibleForTesting
    BuildType(@NonNull String name,
              @NonNull Project project,
              @NonNull Logger logger) {
        super(name)
        this.project = project
        this.logger = logger
        ndkConfig = null
    }

    @Nullable
    public NdkConfig getNdkConfig() {
        return ndkConfig;
    }

    /**
     * Initialize the DSL object. Not meant to be used from the build scripts.
     */
    public void init(SigningConfig debugSigningConfig) {
        if (BuilderConstants.DEBUG.equals(getName())) {
            setDebuggable(true)
            setEmbedMicroApp(false)

            assert debugSigningConfig != null
            setSigningConfig(debugSigningConfig)
        } else if (BuilderConstants.RELEASE.equals(getName())) {
            // no config needed for now.
        }
    }

    /** The signing configuration. */
    @Override
    SigningConfig getSigningConfig() {
        return (SigningConfig) super.signingConfig
    }

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        super._initWith(that)
        shrinkResources = that.isShrinkResources()
        useJack = that.useJack
    }

    int hashCode() {
        int result = super.hashCode()
        result = 31 * result + (useJack != null ? useJack.hashCode() : 0)
        result = 31 * result + (shrinkResources ? 1 : 0)
        return result
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        if (!super.equals(o)) return false
        if (useJack != o.useJack) return false
        if (shrinkResources != o.isShrinkResources()) return false

        return true
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /**
     * Adds a new field to the generated BuildConfig class.
     *
     * <p>The field is generated as: <code>&lt;type&gt; &lt;name&gt; = &lt;value&gt;;</code>
     *
     * <p>This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
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

    /**
     * Adds a new generated resource.
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    public void resValue(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getResValues().get(name);
        if (alreadyPresent != null) {
            logger.info(
                    "BuildType(${getName()}): resValue '$name' value is being replaced: ${alreadyPresent.value} -> $value");
        }
        addResValue(AndroidBuilder.createClassField(type, name, value));
    }

    /**
     * Adds a new ProGuard configuration file.
     *
     * <p><code>proguardFile getDefaultProguardFile('proguard-android.txt')</code></p>
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType proguardFile(Object proguardFile) {
        proguardFiles.add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds new ProGuard configuration files.
     */
    @NonNull
    public BuildType proguardFiles(Object... proguardFileArray) {
        proguardFiles.addAll(project.files(proguardFileArray).files);
        return this;
    }

    /**
     * Sets the ProGuard configuration files.
     */
    @NonNull
    public BuildType setProguardFiles(Iterable<?> proguardFileIterable) {
        proguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            proguardFiles.add(project.file(proguardFile));
        }
        return this;
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType testProguardFile(Object proguardFile) {
        testProguardFiles.add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds new ProGuard configuration files.
     */
    @NonNull
    public BuildType testProguardFiles(Object... proguardFileArray) {
        testProguardFiles.addAll(project.files(proguardFileArray).files);
        return this;
    }

    @NonNull
    public BuildType consumerProguardFiles(Object... proguardFileArray) {
        consumerProguardFiles.addAll(project.files(proguardFileArray).files);
        return this;
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType setConsumerProguardFiles(Iterable<?> proguardFileIterable) {
        consumerProguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            consumerProguardFiles.add(project.file(proguardFile));
        }
        return this;
    }

    void ndk(Action<NdkOptions> action) {
        action.execute(ndkConfig)
    }

    /**
     * Whether the experimental Jack toolchain should be used.
     */
    Boolean getUseJack() {
        return useJack
    }

    /**
     * Whether the experimental Jack toolchain should be used.
     */
    void setUseJack(Boolean useJack) {
        this.useJack = useJack
    }

    /**
     * Whether the experimental Jack toolchain should be used.
     */
    void useJack(Boolean useJack) {
        setUseJack(useJack)
    }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    boolean shrinkResources = false // opt-in for now until we've validated it in the field

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    void shrinkResources(boolean flag) {
        this.shrinkResources = flag
    }
}
