package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.builder.core.AndroidBuilder;
import com.google.common.collect.Lists;

import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;

import java.util.Collection;
import java.util.Collections;

/**
 * {@code android} extension for {@code com.android.library} projects.
 */
public class LibraryExtension extends TestedExtension {

    private final DefaultDomainObjectSet<LibraryVariant> libraryVariantList
            = new DefaultDomainObjectSet<LibraryVariant>(LibraryVariant.class);

    private boolean packageBuildConfig = true;

    private Collection<String> aidlPackageWhiteList = null;

    public LibraryExtension(@NonNull ProjectInternal project, @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder, @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull ExtraModelInfo extraModelInfo, boolean isLibrary) {
        super(project, instantiator, androidBuilder, sdkHandler, buildTypes, productFlavors,
                signingConfigs, extraModelInfo, isLibrary);
    }

    /**
     * Returns the list of library variants. Since the collections is built after evaluation, it
     * should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DefaultDomainObjectSet<LibraryVariant> getLibraryVariants() {
        return libraryVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant) {
        libraryVariantList.add((LibraryVariant) variant);
    }

    public void packageBuildConfig(boolean value) {
        if (!value) {
            LoggingUtil.displayDeprecationWarning(logger, project,
                    "Support for not packaging BuildConfig is deprecated.");
        }

        packageBuildConfig = value;
    }

    @Deprecated
    public void setPackageBuildConfig(boolean value) {
        // Remove when users stop requiring this setting.
        packageBuildConfig(value);
    }

    @Override
    public Boolean getPackageBuildConfig() {
        return packageBuildConfig;
    }

    public void aidlPackageWhiteList(String ... aidlFqcns) {
        if (aidlPackageWhiteList == null) {
            aidlPackageWhiteList = Lists.newArrayList();
        }
        Collections.addAll(aidlPackageWhiteList, aidlFqcns);
    }

    public void setAidlPackageWhiteList(Collection<String> aidlPackageWhiteList) {
        this.aidlPackageWhiteList = Lists.newArrayList(aidlPackageWhiteList);
    }

    @Override
    public Collection<String> getAidlPackageWhiteList() {
        return aidlPackageWhiteList;
    }
}
