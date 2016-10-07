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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * Integration tests for manifest merging.
 */
public class ManifestMergingTest {

    @Rule
    public GradleTestProject simpleManifestMergingTask = GradleTestProject.builder()
            .withName("simpleManifestMergingTask")
            .fromTestProject("simpleManifestMergingTask")
            .create();

    @Rule
    public GradleTestProject libsTest = GradleTestProject.builder()
            .withName("libsTest")
            .fromTestProject("libsTest")
            .create();

    @Rule
    public GradleTestProject flavors = GradleTestProject.builder()
            .withName("flavors")
            .fromTestProject("flavors")
            .create();

    @Test
    public void simpleManifestMerger() {
        simpleManifestMergingTask.execute("clean", "manifestMerger");
    }

    @Test
    public void checkManifestMergingForLibraries() {
        libsTest.execute("clean", "build");
        File fileOutput = libsTest.
                file("libapp/build/" + FD_INTERMEDIATES + "/bundles/release/AndroidManifest.xml");

        assertTrue(fileOutput.exists());
    }

    @Test
    public void checkManifestMergerReport() {
        flavors.execute("clean", "assemble");

        File logs = new File(flavors.getOutputFile("apk").getParentFile(), "logs");
        File[] reports = logs.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().startsWith("manifest-merger");
            }
        });
        assertEquals(8, reports.length);
    }

    @Test
    public void checkPreviewTargetUpdatesMinSdkVersion() throws IOException {
        GradleTestProject appProject = libsTest.getSubproject("app");
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "android{\n"
                        + "    compileSdkVersion 23\n"
                        + "    defaultConfig{\n"
                        + "        minSdkVersion 15\n"
                        + "        targetSdkVersion 'N'\n"
                        + "    }\n"
                        + "}");
        libsTest.execute("clean", ":app:build");
        assertThat(
                appProject.file("build/intermediates/manifests/full/debug/AndroidManifest.xml"))
                .containsAllOf(
                        "android:targetSdkVersion=\"N\"",
                        "android:minSdkVersion=\"N\"");
    }

    @Test
    public void checkPreviewMinUpdatesTargetSdkVersion() throws IOException {
        GradleTestProject appProject = libsTest.getSubproject("app");
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "android{\n"
                        + "    compileSdkVersion 23\n"
                        + "    defaultConfig{\n"
                        + "        minSdkVersion 'N'\n"
                        + "        targetSdkVersion 15\n"
                        + "    }\n"
                        + "}");
        libsTest.execute("clean", ":app:assembleDebug");
        assertThat(
                appProject.file("build/intermediates/manifests/full/debug/AndroidManifest.xml"))
                .containsAllOf(
                        "android:targetSdkVersion=\"N\"",
                        "android:minSdkVersion=\"N\"");
    }

}
