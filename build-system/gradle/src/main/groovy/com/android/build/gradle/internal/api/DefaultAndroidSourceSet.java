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

package com.android.build.gradle.internal.api;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.android.build.gradle.api.AndroidSourceFile;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.builder.model.SourceProvider;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import groovy.lang.Closure;

/**
 */
public class DefaultAndroidSourceSet implements AndroidSourceSet, SourceProvider {
    @NonNull
    private final String name;
    private final SourceDirectorySet javaSource;
    private final SourceDirectorySet allJavaSource;
    private final SourceDirectorySet javaResources;
    private final AndroidSourceFile manifest;
    private final AndroidSourceDirectorySet assets;
    private final AndroidSourceDirectorySet res;
    private final AndroidSourceDirectorySet aidl;
    private final AndroidSourceDirectorySet renderscript;
    private final AndroidSourceDirectorySet jni;
    private final AndroidSourceDirectorySet jniLibs;
    private final String displayName;
    private final SourceDirectorySet allSource;

    public DefaultAndroidSourceSet(@NonNull String name, @NonNull FileResolver fileResolver) {
        this.name = name;
        displayName = GUtil.toWords(this.name);

        String javaSrcDisplayName = String.format("%s Java source", displayName);

        javaSource = new DefaultSourceDirectorySet(javaSrcDisplayName, fileResolver);
        javaSource.getFilter().include("**/*.java");

        allJavaSource = new DefaultSourceDirectorySet(javaSrcDisplayName, fileResolver);
        allJavaSource.getFilter().include("**/*.java");
        allJavaSource.source(javaSource);

        String javaResourcesDisplayName = String.format("%s Java resources", displayName);
        javaResources = new DefaultSourceDirectorySet(javaResourcesDisplayName, fileResolver);
        javaResources.getFilter().exclude(new Spec<FileTreeElement>() {
            @Override
            public boolean isSatisfiedBy(FileTreeElement element) {
                return javaSource.contains(element.getFile());
            }
        });

        String allSourceDisplayName = String.format("%s source", displayName);
        allSource = new DefaultSourceDirectorySet(allSourceDisplayName, fileResolver);
        allSource.source(javaResources);
        allSource.source(javaSource);

        String manifestDisplayName = String.format("%s manifest", displayName);
        manifest = new DefaultAndroidSourceFile(manifestDisplayName, fileResolver);

        String assetsDisplayName = String.format("%s assets", displayName);
        assets = new DefaultAndroidSourceDirectorySet(assetsDisplayName, fileResolver);

        String resourcesDisplayName = String.format("%s resources", displayName);
        res = new DefaultAndroidSourceDirectorySet(resourcesDisplayName, fileResolver);

        String aidlDisplayName = String.format("%s aidl", displayName);
        aidl = new DefaultAndroidSourceDirectorySet(aidlDisplayName, fileResolver);

        String renderscriptDisplayName = String.format("%s renderscript", displayName);
        renderscript = new DefaultAndroidSourceDirectorySet(renderscriptDisplayName, fileResolver);

        String jniDisplayName = String.format("%s jni", displayName);
        jni = new DefaultAndroidSourceDirectorySet(jniDisplayName, fileResolver);

        String libsDisplayName = String.format("%s jniLibs", displayName);
        jniLibs = new DefaultAndroidSourceDirectorySet(libsDisplayName, fileResolver);
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("source set %s", getDisplayName());
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    @NonNull
    public String getCompileConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return "compile";
        } else {
            return String.format("%sCompile", name);
        }
    }

    @Override
    @NonNull
    public String getPackageConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return "apk";
        } else {
            return String.format("%sApk", name);
        }
    }

    @Override
    @NonNull
    public String getProvidedConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return "provided";
        } else {
            return String.format("%sProvided", name);
        }
    }

    @Override
    @NonNull
    public AndroidSourceFile getManifest() {
        return manifest;
    }

    @Override
    @NonNull
    public AndroidSourceSet manifest(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getManifest());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getRes() {
        return res;
    }

    @Override
    @NonNull
    public AndroidSourceSet res(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRes());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getAssets() {
        return assets;
    }

    @Override
    @NonNull
    public AndroidSourceSet assets(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getAssets());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getAidl() {
        return aidl;
    }

    @Override
    @NonNull
    public AndroidSourceSet aidl(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getAidl());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getRenderscript() {
        return renderscript;
    }

    @Override
    @NonNull
    public AndroidSourceSet renderscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRenderscript());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getJni() {
        return jni;
    }

    @Override
    @NonNull
    public AndroidSourceSet jni(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJni());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getJniLibs() {
        return jniLibs;
    }

    @Override
    @NonNull
    public AndroidSourceSet jniLibs(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJniLibs());
        return this;
    }

    @Override
    @NonNull
    public SourceDirectorySet getJava() {
        return javaSource;
    }

    @Override
    @NonNull
    public AndroidSourceSet java(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJava());
        return this;
    }

    @Override
    @NonNull
    public SourceDirectorySet getAllJava() {
        return allJavaSource;
    }

    @Override
    @NonNull
    public SourceDirectorySet getResources() {
        return javaResources;
    }

    @Override
    @NonNull
    public AndroidSourceSet resources(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getResources());
        return this;
    }

    @Override
    @NonNull
    public SourceDirectorySet getAllSource() {
        return allSource;
    }

    @Override
    @NonNull
    public AndroidSourceSet setRoot(String path) {
        javaSource.setSrcDirs(Collections.singletonList(path + "/java"));
        javaResources.setSrcDirs(Collections.singletonList(path + "/resources"));
        res.setSrcDirs(Collections.singletonList(path + "/" + SdkConstants.FD_RES));
        assets.setSrcDirs(Collections.singletonList(path + "/" + SdkConstants.FD_ASSETS));
        manifest.srcFile(path + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
        aidl.setSrcDirs(Collections.singletonList(path + "/aidl"));
        renderscript.setSrcDirs(Collections.singletonList(path + "/rs"));
        jni.setSrcDirs(Collections.singletonList(path + "/jni"));
        jniLibs.setSrcDirs(Collections.singletonList(path + "/jniLibs"));
        return this;
    }

    // --- SourceProvider

    @NonNull
    @Override
    public Set<File> getJavaDirectories() {
        return getJava().getSrcDirs();
    }

    @NonNull
    @Override
    public Set<File> getResourcesDirectories() {
        return getResources().getSrcDirs();
    }

    @Override
    @NonNull
    public File getManifestFile() {
        return getManifest().getSrcFile();
    }

    @Override
    @NonNull
    public Set<File> getAidlDirectories() {
        return getAidl().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getRenderscriptDirectories() {
        return getRenderscript().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getJniDirectories() {
        return getJni().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getResDirectories() {
        return getRes().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getAssetsDirectories() {
        return getAssets().getSrcDirs();
    }

    @NonNull
    @Override
    public Collection<File> getJniLibsDirectories() {
        return getJniLibs().getSrcDirs();
    }
}
