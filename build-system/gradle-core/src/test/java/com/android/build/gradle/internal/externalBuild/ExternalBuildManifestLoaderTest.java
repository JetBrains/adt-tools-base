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
import static org.mockito.Mockito.when;

import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
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
import java.util.List;

/**
 * Tests for the {@link ExternalBuildManifestLoader}
 */
public class ExternalBuildManifestLoaderTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Mock
    ExternalBuildExtension mExternalBuildExtension;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void manifestReadingTest() throws IOException {
        File apk_manifest_test = new File(tmpFolder.getRoot(), "apk_manifest_test");
        try (OutputStream os = new BufferedOutputStream( new FileOutputStream(apk_manifest_test))) {
            ExternalBuildApkManifest.ApkManifest.newBuilder()
                    .setAndroidSdk(ExternalBuildApkManifest.AndroidSdk.newBuilder()
                            .setAapt("/path/to/aapt"))
                    .addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                            .setExecRootPath("tmp/one"))
                    .addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                            .setExecRootPath("tmp/two"))
                    .build()
                    .writeTo(os);
        }
        ExternalBuildContext externalBuildContext =
                new ExternalBuildContext(mExternalBuildExtension);
        ExternalBuildManifestLoader.loadAndPopulateContext(apk_manifest_test,
                tmpFolder.getRoot(), externalBuildContext);

        // assert build context population.
        assertThat(externalBuildContext.getBuildManifest()).isNotNull();
        ExternalBuildApkManifest.ApkManifest buildManifest = externalBuildContext
                .getBuildManifest();

        assertThat(buildManifest.getJarsCount()).isEqualTo(2);
        assertThat(buildManifest.getAndroidSdk().getAapt()).isEqualTo("/path/to/aapt");
        assertThat(externalBuildContext.getInputJarFiles()).containsAllOf(
                new File(tmpFolder.getRoot(), "tmp/one"),
                new File(tmpFolder.getRoot(), "tmp/two"));
    }
}
