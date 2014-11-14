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
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.core.NdkConfig
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.model.BaseConfig
import com.android.builder.model.BuildType
import com.android.builder.model.ClassField
import com.android.builder.model.SigningConfig
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator
/**
 * DSL object to configure build types.
 */
public class BuildTypeDsl extends DefaultBuildType implements Serializable {
    private static final long serialVersionUID = 1L

    @NonNull
    private final Project project
    @NonNull
    private final Logger logger

    private final NdkConfigDsl ndkConfig

    private Boolean useJack

    public BuildTypeDsl(@NonNull String name,
                        @NonNull Project project,
                        @NonNull Instantiator instantiator,
                        @NonNull Logger logger) {
        super(name)
        this.project = project
        this.logger = logger
        ndkConfig = instantiator.newInstance(NdkConfigDsl.class)
    }

    @VisibleForTesting
    BuildTypeDsl(@NonNull String name,
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

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        super._initWith(that)
        shrinkResources = that.shrinkResources
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
        if (shrinkResources != o.shrinkResources) return false

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
    public BuildTypeDsl proguardFile(Object proguardFile) {
        proguardFiles.add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds new ProGuard configuration files.
     */
    @NonNull
    public BuildTypeDsl proguardFiles(Object... proguardFileArray) {
        proguardFiles.addAll(project.files(proguardFileArray).files);
        return this;
    }

    /**
     * Sets the ProGuard configuration files.
     */
    @NonNull
    public BuildTypeDsl setProguardFiles(Iterable<?> proguardFileIterable) {
        proguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            proguardFiles.add(project.file(proguardFile));
        }
        return this;
    }

    @NonNull
    public BuildTypeDsl consumerProguardFiles(Object... proguardFileArray) {
        consumerProguardFiles.addAll(project.files(proguardFileArray).files);
        return this;
    }

    @NonNull
    public BuildTypeDsl setConsumerProguardFiles(Iterable<?> proguardFileIterable) {
        consumerProguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            consumerProguardFiles.add(project.file(proguardFile));
        }
        return this;
    }

    void ndk(Action<NdkConfigDsl> action) {
        action.execute(ndkConfig)
    }

    Boolean getUseJack() {
        return useJack
    }

    void setUseJack(Boolean useJack) {
        this.useJack = useJack
    }

    void useJack(Boolean useJack) {
        setUseJack(useJack)
    }

    boolean shrinkResources = false // opt-in for now until we've validated it in the field

    void shrinkResources(boolean flag) {
        this.shrinkResources = flag
    }

    // ---------------
    // TEMP for compatibility
    // STOPSHIP Remove in 1.0

    public BuildType runProguard(boolean enabled) {
        return setRunProguard(enabled);
    }

    public BuildType setRunProguard(boolean enabled) {
        BasePlugin.displayDeprecationWarning(logger, project, "WARNING: runProguard is deprecated (and will soon stop working); change to \"minifyEnabled\" instead");
        return setMinifyEnabled(enabled)
    }

    /** Package name suffix applied to this build type. */
    @NonNull
    public BuildType setPackageNameSuffix(@Nullable String packageNameSuffix) {
        BasePlugin.displayDeprecationWarning(logger, project, "WARNING: packageNameSuffix is deprecated (and will soon stop working); change to \"applicationIdSuffix\" instead");
        return setApplicationIdSuffix(packageNameSuffix);
    }

    @NonNull
    public BuildType packageNameSuffix(@Nullable String packageNameSuffix) {
        return setPackageNameSuffix(packageNameSuffix);
    }

    @Nullable
    public String getPackageNameSuffix() {
        BasePlugin.displayDeprecationWarning(logger, project, "WARNING: packageNameSuffix is deprecated (and will soon stop working); change to \"applicationIdSuffix\" instead");
        return getApplicationIdSuffix();
    }

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    @NonNull
    public BuildType setJniDebugBuild(boolean jniDebugBuild) {
        BasePlugin.displayDeprecationWarning(logger, project, "WARNING: jniDebugBuild is deprecated (and will soon stop working); change to \"jniDebuggable\" instead");
        return super.setJniDebuggable(jniDebugBuild);
    }

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    public BuildType setRenderscriptDebugBuild(boolean renderscriptDebugBuild) {
        BasePlugin.displayDeprecationWarning(logger, project, "WARNING: renderscriptDebugBuild is deprecated (and will soon stop working); change to \"renderscriptDebuggable\" instead");
        return setRenderscriptDebuggable(renderscriptDebugBuild);
    }

    /** Whether zipalign is enabled for this build type. */
    @NonNull
    public BuildType setZipAlign(boolean zipAlign) {
        BasePlugin.displayDeprecationWarning(logger, project, "WARNING: zipAlign is deprecated (and will soon stop working); change to \"zipAlignEnabled\" instead");
        return setZipAlignEnabled(zipAlign);
    }
}
