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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.build.gradle.internal.incremental.ColdswapMode
import com.android.sdklib.IAndroidTarget
import com.google.common.io.Files
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest.ApkManifest
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import static com.google.common.truth.Truth.assertThat
/**
 * Integration test for the ExternalBuildPlugin.
 */
public class ExternalBuildPluginTest {

    File manifestFile;

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("externalBuildPlugin")
            .create()

    @Before
    public void setUp() {

        IAndroidTarget target = SdkHelper.getTarget(23);
        assertThat(target).isNotNull();


        def resourceFile = project.file("resources.ap_")
        def manifest = project.file("AndroidManifest.xml")

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(resourceFile));
        zos.putNextEntry(new ZipEntry("AndroidManifest.xml"));
        Files.copy(manifest, zos);
        zos.closeEntry();
        zos.flush();
        zos.close();

        ApkManifest.Builder apkManifest =
                ApkManifest.newBuilder()
                        .setAndroidSdk(ExternalBuildApkManifest.AndroidSdk.newBuilder()
                                .setAndroidJar(
                                    target.getFile(IAndroidTarget.ANDROID_JAR).absolutePath)
                                // TODO: Start putting dx.jar in the proto
                                .setDx(SdkHelper.getDxJar().absolutePath))
                        .setResourceApk(ExternalBuildApkManifest.Artifact.newBuilder()
                                .setExecRootPath(resourceFile.absolutePath))
                        .setAndroidManifest(ExternalBuildApkManifest.Artifact.newBuilder()
                                .setExecRootPath(manifest.absolutePath))

        List<String> jarFiles = new FileNameFinder().getFileNames(
                project.getTestDir().getAbsolutePath(), "**/*.jar");
        for (String jarFile : jarFiles) {
            apkManifest.addJars(ExternalBuildApkManifest.Artifact.newBuilder()
                    .setExecRootPath(jarFile)
                    .setHash(ByteString.copyFromUtf8(String.valueOf(jarFile.hashCode()))))
        }

        manifestFile = project.file("apk_manifest.tmp")
        OutputStream os = new BufferedOutputStream(new FileOutputStream(manifestFile));
        try {
            CodedOutputStream cos =
                    CodedOutputStream.newInstance(os);
            apkManifest.build().writeTo(cos);
            cos.flush();
        } finally {
            os.close();
        }
    }

    @Test
    public void testBuild() {
        project.getBuildFile() << """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply plugin: 'base'
apply plugin: 'com.android.external.build'

externalBuild {
  buildManifestPath = \$/""" + manifestFile.getAbsolutePath() + """/\$
}
"""
        project.executor()
            .withInstantRun(23, ColdswapMode.AUTO)
            .run("clean", "process")
    }
}
