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

package com.android.build.gradle.tasks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class ProcessAndroidResourcesTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private static void writeManifestFile(@NonNull File resFile, @NonNull String value)
            throws IOException {
        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(resFile)))) {
            out.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            out.write(value.getBytes(Charsets.UTF_8));
            out.closeEntry();
        }
    }

    @Test
    public void runManifestChangeVerifier() throws IOException {
        File instantRunSupportDir =
                new File(mTemporaryFolder.newFolder(), "instant-run-support/debug");
        File manifestFileToPackage = mTemporaryFolder.newFile();

        // Initial build
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            Files.write("Original Manifest", manifestFileToPackage, Charsets.UTF_8);
            ProcessAndroidResources.runManifestChangeVerifier(
                    context, instantRunSupportDir, manifestFileToPackage);
            verifyNoMoreInteractions(context);
        }

        // No changes
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            Files.write("Original Manifest", manifestFileToPackage, Charsets.UTF_8);
            ProcessAndroidResources.runManifestChangeVerifier(
                    context, instantRunSupportDir, manifestFileToPackage);
            verifyNoMoreInteractions(context);
        }

        // Change
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            Files.write("Changed Manifest", manifestFileToPackage, Charsets.UTF_8);
            ProcessAndroidResources.runManifestChangeVerifier(
                    context, instantRunSupportDir, manifestFileToPackage);
            verify(context).setVerifierResult(InstantRunVerifierStatus.MANIFEST_FILE_CHANGE);
        }

        // No changes
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            Files.write("Changed Manifest", manifestFileToPackage, Charsets.UTF_8);
            ProcessAndroidResources.runManifestChangeVerifier(
                    context, instantRunSupportDir, manifestFileToPackage);
            verifyNoMoreInteractions(context);
        }
    }

    @Test
    public void runManifestBinaryChangeVerifier() throws IOException {
        File instantRunSupportDir =
                new File(mTemporaryFolder.newFolder(), "instant-run-support/debug");
        File resOutBaseNameFile = mTemporaryFolder.newFile();

        writeManifestFile(resOutBaseNameFile, "Initial binary manifest");

        // Initial build
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            ProcessAndroidResources.runManifestBinaryChangeVerifier(
                    context, instantRunSupportDir, resOutBaseNameFile);
            verifyNoMoreInteractions(context);
        }

        // No changes
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            writeManifestFile(resOutBaseNameFile, "Initial binary manifest");
            ProcessAndroidResources.runManifestBinaryChangeVerifier(
                    context, instantRunSupportDir, resOutBaseNameFile);
            verifyNoMoreInteractions(context);
        }

        // Change
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            writeManifestFile(resOutBaseNameFile, "Changed binary manifest");
            ProcessAndroidResources.runManifestBinaryChangeVerifier(
                    context, instantRunSupportDir, resOutBaseNameFile);
            verify(context).setVerifierResult(
                    InstantRunVerifierStatus.BINARY_MANIFEST_FILE_CHANGE);
        }

        // No changes
        {
            InstantRunBuildContext context = mock(InstantRunBuildContext.class);
            writeManifestFile(resOutBaseNameFile, "Changed binary manifest");
            ProcessAndroidResources.runManifestBinaryChangeVerifier(
                    context, instantRunSupportDir, resOutBaseNameFile);
            verifyNoMoreInteractions(context);
        }
    }
}
