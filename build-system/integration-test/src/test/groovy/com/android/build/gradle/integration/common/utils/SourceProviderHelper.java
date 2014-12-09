/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;

import java.io.File;
import java.util.Collection;

public final class SourceProviderHelper {

    @NonNull
    private final String projectName;
    @NonNull private final String configName;
    @NonNull private final SourceProvider sourceProvider;
    @NonNull private final File projectDir;
    private String javaDir;
    private String resourcesDir;
    private String manifestFile;
    private String resDir;
    private String assetsDir;
    private String aidlDir;
    private String renderscriptDir;
    private String jniDir;

    public SourceProviderHelper(@NonNull String projectName, @NonNull File projectDir,
            @NonNull String configName, @NonNull SourceProvider sourceProvider) {
        this.projectName = projectName;
        this.projectDir = projectDir;
        this.configName = configName;
        this.sourceProvider = sourceProvider;
        // configure tester with default relative paths
        setJavaDir("src/" + configName + "/java");
        setResourcesDir("src/" + configName + "/resources");
        setManifestFile("src/" + configName + "/AndroidManifest.xml");
        setResDir("src/" + configName + "/res");
        setAssetsDir("src/" + configName + "/assets");
        setAidlDir("src/" + configName + "/aidl");
        setRenderscriptDir("src/" + configName + "/rs");
        setJniDir("src/" + configName + "/jni");
    }

    @NonNull
    public SourceProviderHelper setJavaDir(String javaDir) {
        this.javaDir = javaDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setResourcesDir(String resourcesDir) {
        this.resourcesDir = resourcesDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setManifestFile(String manifestFile) {
        this.manifestFile = manifestFile;
        return this;
    }

    @NonNull
    public SourceProviderHelper setResDir(String resDir) {
        this.resDir = resDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setAssetsDir(String assetsDir) {
        this.assetsDir = assetsDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setAidlDir(String aidlDir) {
        this.aidlDir = aidlDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setRenderscriptDir(String renderscriptDir) {
        this.renderscriptDir = renderscriptDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setJniDir(String jniDir) {
        this.jniDir = jniDir;
        return this;
    }

    public void test() {
        testSinglePathCollection("java", javaDir, sourceProvider.getJavaDirectories());
        testSinglePathCollection("resources", resourcesDir, sourceProvider.getResourcesDirectories());
        testSinglePathCollection("res", resDir, sourceProvider.getResDirectories());
        testSinglePathCollection("assets", assetsDir, sourceProvider.getAssetsDirectories());
        testSinglePathCollection("aidl", aidlDir, sourceProvider.getAidlDirectories());
        testSinglePathCollection("rs", renderscriptDir, sourceProvider.getRenderscriptDirectories());
        testSinglePathCollection("jni", jniDir, sourceProvider.getCDirectories());

        assertEquals("AndroidManifest",
                new File(projectDir, manifestFile).getAbsolutePath(),
                sourceProvider.getManifestFile().getAbsolutePath());
    }

    private void testSinglePathCollection(
            @NonNull String setName,
            @NonNull String referencePath,
            @NonNull Collection<File> pathSet) {
        assertEquals(1, pathSet.size());
        assertEquals(projectName + ": " + configName + "/" + setName,
                new File(projectDir, referencePath).getAbsolutePath(),
                pathSet.iterator().next().getAbsolutePath());
    }

}
