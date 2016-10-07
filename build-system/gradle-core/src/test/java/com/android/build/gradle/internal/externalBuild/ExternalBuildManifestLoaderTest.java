/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.externalBuild;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.android.utils.FileUtils;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import org.gradle.api.Project;
import org.gradle.util.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Tests for the {@link ExternalBuildManifestLoader}
 */
public class ExternalBuildManifestLoaderTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Mock
    ExternalBuildExtension mExternalBuildExtension;

    @Mock
    Project mProject;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // TODO: set up mProject
    }


    @Test
    public void manifestReadingTest() throws IOException {
        File apk_manifest_test = new File(mTemporaryFolder.getRoot(), "apk_manifest_test");
        when(mProject.getPath()).thenReturn(mTemporaryFolder.getRoot().getPath());

        File dx = new File(mTemporaryFolder.getRoot(), "dx.jar");
        when(mProject.file("dx.jar")).thenReturn(dx);

        File aapt = new File(mTemporaryFolder.getRoot(), "aapt");
        when(mProject.file("aapt")).thenReturn(aapt);

        File jarOne = new File(mTemporaryFolder.getRoot(), "tmp/one");
        when(mProject.file("tmp/one")).thenReturn(jarOne);

        File jarTwo = new File(mTemporaryFolder.getRoot(), "tmp/two");
        when(mProject.file("tmp/two")).thenReturn(jarTwo);

        FileUtils.createFile(dx, "dx.jar content");
        try (OutputStream os = new BufferedOutputStream( new FileOutputStream(apk_manifest_test))) {
            ExternalBuildApkManifest.ApkManifest.newBuilder()
                    .setAndroidSdk(ExternalBuildApkManifest.AndroidSdk.newBuilder()
                            .setDx("dx.jar")
                            .setAapt("aapt"))
                    .addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                            .setExecRootPath("tmp/one"))
                    .addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                            .setExecRootPath("tmp/two"))
                    .build()
                    .writeTo(os);
        }
        ExternalBuildContext externalBuildContext =
                new ExternalBuildContext(mExternalBuildExtension);
        ExternalBuildManifestLoader.loadAndPopulateContext(
                mTemporaryFolder.getRoot(),
                apk_manifest_test,
                mProject, externalBuildContext);

        // assert build context population.
        assertThat(externalBuildContext.getBuildManifest()).isNotNull();
        ExternalBuildApkManifest.ApkManifest buildManifest = externalBuildContext
                .getBuildManifest();

        assertThat(buildManifest.getJarsCount()).isEqualTo(2);
        assertThat(buildManifest.getAndroidSdk().getAapt()).isEqualTo("aapt");
        assertThat(externalBuildContext.getInputJarFiles()).containsAllOf(
                new File(mTemporaryFolder.getRoot(), "tmp/one"),
                new File(mTemporaryFolder.getRoot(), "tmp/two"));
    }
}
