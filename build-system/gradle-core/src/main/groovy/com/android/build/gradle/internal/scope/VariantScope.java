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

package com.android.build.gradle.internal.scope;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.builder.core.VariantConfiguration;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * A scope containing data for a specific variant.
 */
public class VariantScope {

    @NonNull
    private GlobalScope globalScope;
    @NonNull
    private BaseVariantData<? extends BaseVariantOutputData> variantData;

    @Nullable
    private Collection<Object> ndkBuildable;
    @Nullable
    private Collection<File> ndkOutputDirectories;

    // Tasks
    @Nullable
    private AndroidTask dexTask;
    @Nullable
    private AndroidTask javaCompileTask;
    @Nullable
    private AndroidTask jacocoIntrumentTask;

    public VariantScope(
            @NonNull GlobalScope globalScope,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        this.globalScope = globalScope;
        this.variantData = variantData;
    }

    @NonNull
    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @NonNull
    public BaseVariantData<? extends BaseVariantOutputData> getVariantData() {
        return variantData;
    }

    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantData.getVariantConfiguration();
    }

    @Nullable
    public Collection<Object> getNdkBuildable() {
        return ndkBuildable;
    }

    public void setNdkBuildable(@NonNull Collection<Object> ndkBuildable) {
        this.ndkBuildable = ndkBuildable;
    }

    @Nullable
    public Collection<File> getNdkOutputDirectories() {
        return ndkOutputDirectories;
    }

    public void setNdkOutputDirectories(@NonNull Collection<File> ndkOutputDirectories) {
        this.ndkOutputDirectories = ndkOutputDirectories;
    }

    @NonNull
    public Set<File> getJniFolders() {
        assert getNdkOutputDirectories() != null;

        VariantConfiguration config = getVariantConfiguration();
        ApkVariantData apkVariantData = (ApkVariantData) variantData;
        // for now only the project's compilation output.
        Set<File> set = Sets.newHashSet();
        set.addAll(getNdkOutputDirectories());
        set.add(apkVariantData.renderscriptCompileTask.getLibOutputDir());
        set.addAll(config.getLibraryJniFolders());
        set.addAll(config.getJniLibsList());

        if (config.getMergedFlavor().getRenderscriptSupportModeEnabled() != null &&
                config.getMergedFlavor().getRenderscriptSupportModeEnabled()) {
            File rsLibs = globalScope.getAndroidBuilder().getSupportNativeLibFolder();
            if (rsLibs != null && rsLibs.isDirectory()) {
                set.add(rsLibs);
            }
        }
        return set;
    }

    @Nullable
    public BaseVariantData getTestedVariantData() {
        return variantData instanceof TestVariantData ?
                (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData() :
                null;
    }

    @NonNull
    public File getDexOutputFolder() {
        return new File(getGlobalScope().getBuildDir() + "/" + FD_INTERMEDIATES +
                "/dex/" + getVariantConfiguration().getDirName());

    }

    @NonNull
    public File getJavaOutputDir() {
        return new File(globalScope.getBuildDir() + "/" + FD_INTERMEDIATES + "/classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    public File getPreDexOutputDir() {
        return new File(globalScope.getBuildDir() + "/" + FD_INTERMEDIATES + "/pre-dexed/" +
                getVariantConfiguration().getDirName());
    }

    @NonNull
    public File getProguardOutputFile() {
        return (variantData instanceof LibraryVariantData) ?
                new File(globalScope.getBuildDir() + "/" + FD_INTERMEDIATES + "/"  +
                        TaskManager.DIR_BUNDLES + "/"  +
                        getVariantConfiguration().getDirName() + "/classes.jar") :
                new File(globalScope.getBuildDir() + "/" + FD_INTERMEDIATES + "/"  +
                        "/classes-proguard/" +
                        getVariantConfiguration().getDirName() + "/classes.jar");
    }

    @NonNull
    public File getProguardComponentsJarFile() {
        return new File(
                globalScope.getBuildDir() + "/" + FD_INTERMEDIATES + "/multi-dex/" +
                        getVariantConfiguration().getDirName() + "/componentClasses.jar");
    }

    @NonNull
    public File getJarMergingOutputFile() {
        return new File(globalScope.getBuildDir() + "/"  + FD_INTERMEDIATES + "/multi-dex/"  +
                getVariantConfiguration().getDirName() + "/allclasses.jar");
    }

    @NonNull
    public File getManifestKeepListFile() {
        return new File(globalScope.getBuildDir() + "/"  + FD_INTERMEDIATES + "/multi-dex/"  +
                getVariantConfiguration().getDirName() + "/manifest_keep.txt");
    }

    @NonNull
    public File getMainDexListFile() {
        return new File(globalScope.getBuildDir() + "/"  + FD_INTERMEDIATES  + "/multi-dex/"
                + getVariantConfiguration().getDirName()  + "/maindexlist.txt");
    }

    // Tasks getters/setters.

    @Nullable
    public AndroidTask getDexTask() {
        return dexTask;
    }

    public void setDexTask(@NonNull AndroidTask dexTask) {
        this.dexTask = dexTask;
    }

    @Nullable
    public AndroidTask getJavaCompileTask() {
        return javaCompileTask;
    }

    public void setJavaCompileTask(@NonNull AndroidTask javaCompileTask) {
        this.javaCompileTask = javaCompileTask;
    }

    @Nullable
    public AndroidTask getJacocoIntrumentTask() {
        return jacocoIntrumentTask;
    }

    public void setJacocoIntrumentTask(
            @NonNull AndroidTask jacocoIntrumentTask) {
        this.jacocoIntrumentTask = jacocoIntrumentTask;
    }
}
