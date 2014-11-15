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
import com.android.build.gradle.BasePlugin
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.model.ApiVersion
import com.android.builder.model.ClassField
import com.android.build.gradle.internal.core.NdkConfig
import com.android.builder.model.ProductFlavor
import com.google.common.base.Strings
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator
/**
 * DSL overlay to make methods that accept String... work.
 */
class ProductFlavorDsl extends DefaultProductFlavor {

    @NonNull
    protected final Project project

    @NonNull
    protected final Logger logger

    private final NdkConfigDsl ndkConfig

    private Boolean useJack

    ProductFlavorDsl(@NonNull String name,
            @NonNull Project project,
            @NonNull Instantiator instantiator,
            @NonNull Logger logger) {
        super(name)
        this.project = project
        this.logger = logger
        ndkConfig = instantiator.newInstance(NdkConfigDsl.class)
    }

    @Nullable
    public NdkConfig getNdkConfig() {
        return ndkConfig;
    }

    @NonNull
    public ProductFlavor setMinSdkVersion(int minSdkVersion) {
        setMinSdkVersion(new DefaultApiVersion(minSdkVersion));
        return this;
    }

    /**
     * Sets minimum SDK version.
     */
    @NonNull
    public ProductFlavor minSdkVersion(int minSdkVersion) {
        setMinSdkVersion(minSdkVersion);
        return this;
    }

    @NonNull
    public ProductFlavor setMinSdkVersion(String minSdkVersion) {
        setMinSdkVersion(getApiVersion(minSdkVersion))
        return this;
    }

    /**
     * Sets minimum SDK version.
     */
    @NonNull
    public ProductFlavor minSdkVersion(String minSdkVersion) {
        setMinSdkVersion(minSdkVersion);
        return this;
    }

    @NonNull
    public ProductFlavor setTargetSdkVersion(int targetSdkVersion) {
        setTargetSdkVersion(new DefaultApiVersion(targetSdkVersion));
        return this;
    }

    /**
     * Sets target SDK version.
     */
    @NonNull
    public ProductFlavor targetSdkVersion(int targetSdkVersion) {
        setTargetSdkVersion(targetSdkVersion);
        return this;
    }

    @NonNull
    public ProductFlavor setTargetSdkVersion(String targetSdkVersion) {
        setTargetSdkVersion(getApiVersion(targetSdkVersion))
        return this;
    }

    /**
     * Sets target SDK version.
     */
    @NonNull
    public ProductFlavor targetSdkVersion(String targetSdkVersion) {
        setTargetSdkVersion(targetSdkVersion);
        return this;
    }

    @NonNull
    public ProductFlavor maxSdkVersion(int targetSdkVersion) {
        setMaxSdkVersion(targetSdkVersion);
        return this;
    }

    @Nullable
    private static ApiVersion getApiVersion(@Nullable String value) {
        if (!Strings.isNullOrEmpty(value)) {
            if (Character.isDigit(value.charAt(0))) {
                try {
                    int apiLevel = Integer.valueOf(value)
                    return new DefaultApiVersion(apiLevel)
                } catch (NumberFormatException e) {
                    throw new RuntimeException("'${value}' is not a valid API level. ", e)
                }
            }

            return new DefaultApiVersion(value)
        }

        return null
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    public void buildConfigField(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getBuildConfigFields().get(name);
        if (alreadyPresent != null) {
            String flavorName = getName();
            if (BuilderConstants.MAIN.equals(flavorName)) {
                logger.info(
                        "DefaultConfig: buildConfigField '$name' value is being replaced: ${alreadyPresent.value} -> $value");
            } else {
                logger.info(
                        "ProductFlavor($flavorName): buildConfigField '$name' value is being replaced: ${alreadyPresent.value} -> $value");
            }
        }
        addBuildConfigField(AndroidBuilder.createClassField(type, name, value));
    }

    public void resValue(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getResValues().get(name);
        if (alreadyPresent != null) {
            String flavorName = getName();
            if (BuilderConstants.MAIN.equals(flavorName)) {
                logger.info(
                        "DefaultConfig: resValue '$name' value is being replaced: ${alreadyPresent.value} -> $value");
            } else {
                logger.info(
                        "ProductFlavor($flavorName): resValue '$name' value is being replaced: ${alreadyPresent.value} -> $value");
            }
        }
        addResValue(AndroidBuilder.createClassField(type, name, value));
    }

    @NonNull
    public ProductFlavorDsl proguardFile(Object proguardFile) {
        proguardFiles.add(project.file(proguardFile))
        return this
    }

    @NonNull
    public ProductFlavorDsl proguardFiles(Object... proguardFileArray) {
        proguardFiles.addAll(project.files(proguardFileArray).files)
        return this
    }

    @NonNull
    public ProductFlavorDsl setProguardFiles(Iterable<?> proguardFileIterable) {
        proguardFiles.clear()
        for (Object proguardFile : proguardFileIterable) {
            proguardFiles.add(project.file(proguardFile))
        }
        return this
    }

    @NonNull
    public ProductFlavorDsl consumerProguardFiles(Object... proguardFileArray) {
        consumerProguardFiles.addAll(project.files(proguardFileArray).files)
        return this
    }

    @NonNull
    public ProductFlavorDsl setConsumerProguardFiles(Iterable<?> proguardFileIterable) {
        consumerProguardFiles.clear()
        for (Object proguardFile : proguardFileIterable) {
            consumerProguardFiles.add(project.file(proguardFile))
        }
        return this
    }

    void ndk(Action<NdkConfigDsl> action) {
        action.execute(ndkConfig)
    }

    void resConfig(@NonNull String config) {
        addResourceConfiguration(config);
    }

    void resConfigs(@NonNull String... config) {
        addResourceConfigurations(config);
    }

    void resConfigs(@NonNull Collection<String> config) {
        addResourceConfigurations(config);
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

    // ---------------
    // TEMP for compatibility
    // STOPSHIP Remove in 1.0

    /**
     * Sets the package name.
     *
     * @param packageName the package name
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setPackageName(String packageName) {
        BasePlugin.displayDeprecationWarning(logger, project,
                "\"packageName\" is deprecated (and will soon stop working); change to \"applicationId\" instead");
        return setApplicationId(packageName);
    }

    @NonNull
    public ProductFlavor packageName(String packageName) {
        return setPackageName(packageName); // not setApplicationId: we want the warning message
    }

    @Nullable
    public String getPackageName() {
        BasePlugin.displayDeprecationWarning(logger, project,
                "\"packageName\" is deprecated (and will soon stop working); change to \"applicationId\" instead");
        return getApplicationId();
    }

    @Nullable
    public String getTestPackageName() {
        BasePlugin.displayDeprecationWarning(logger, project,
                "\"testPackageName\" is deprecated (and will soon stop working); change to \"testApplicationId\" instead");
        return getTestApplicationId();
    }

    @Nullable
    public ProductFlavor setTestPackageName(String packageName) {
        BasePlugin.displayDeprecationWarning(logger, project,
                "\"testPackageName\" is deprecated (and will soon stop working); change to \"testApplicationId\" instead");
        return setTestApplicationId(packageName);
    }

    /**
     * Sets whether the renderscript code should be compiled in support mode to make it compatible
     * with older versions of Android.
     */
    public ProductFlavor setRenderscriptSupportMode(Boolean renderscriptSupportMode) {
        BasePlugin.displayDeprecationWarning(logger, project,
                "\"renderscriptSupportMode\" is deprecated (and will soon stop working); change to \"renderscriptSupportModeEnabled\" instead");
        return setRenderscriptNdkModeEnabled(renderscriptSupportMode);
    }

    /** Sets whether the renderscript code should be compiled to generate C/C++ bindings. */
    public ProductFlavor setRenderscriptNdkMode(Boolean renderscriptNdkMode) {
        BasePlugin.displayDeprecationWarning(logger, project,
                "\"renderscriptNdkMode\" is deprecated (and will soon stop working); change to \"renderscriptNdkModeEnabled\" instead");

        return super.setRenderscriptNdkModeEnabled(renderscriptNdkMode);
    }
}