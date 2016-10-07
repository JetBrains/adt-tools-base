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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.SupplierTask;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Checks that the manifest file has not changed since the last instant run build.
 */
public class CheckManifestInInstantRunMode extends DefaultAndroidTask {

    private static final Logger LOG = Logging.getLogger(CheckManifestInInstantRunMode.class);

    private InstantRunBuildContext instantRunBuildContext;
    private File instantRunSupportDir;
    private File packageOutputFile;
    private File instantRunManifestFile;

    public File getInstantRunManifestFile() {
        return instantRunManifestFile;
    }

    @SuppressWarnings("unused")
    public void setInstantRunManifestFile(File instantRunManifestFile) {
        this.instantRunManifestFile = instantRunManifestFile;
    }

    public File getPackageOutputFile() {
        return packageOutputFile;
    }

    @SuppressWarnings("unused")
    public void setPackageOutputFile(File packageOutputFile) {
        this.packageOutputFile = packageOutputFile;
    }

    @TaskAction
    public void checkManifestChanges() throws IOException {

        // If we are NOT instant run mode, this is an error, this task should not be running.
        if (!instantRunBuildContext.isInInstantRunMode()) {
            LOG.warn("CheckManifestInInstantRunMode configured in non instant run build,"
                    + " please file a bug.");
            return;
        }

        // always do both, we should make sure that we are not keeping stale data for the previous
        // instance.
        File instantRunManifestFile = getInstantRunManifestFile();
        LOG.info("CheckManifestInInstantRunMode : Merged manifest %1$s", instantRunManifestFile);
        runManifestChangeVerifier(instantRunBuildContext, instantRunSupportDir,
                instantRunManifestFile);

        File resourcesApk = getPackageOutputFile();
        LOG.info("CheckManifestInInstantRunMode : Resource APK %1$s", resourcesApk);
        if (resourcesApk != null && resourcesApk.exists()) {
            runManifestBinaryChangeVerifier(instantRunBuildContext, instantRunSupportDir,
                    getPackageOutputFile());
        }
    }


    @VisibleForTesting
    static void runManifestChangeVerifier(InstantRunBuildContext instantRunBuildContext,
            File instantRunSupportDir,
            @NonNull File manifestFileToPackage) throws IOException {
        File previousManifestFile = new File(instantRunSupportDir, "manifest.xml");

        if (previousManifestFile.exists()) {
            String currentManifest =
                    Files.asCharSource(manifestFileToPackage, Charsets.UTF_8).read();
            String previousManifest =
                    Files.asCharSource(previousManifestFile, Charsets.UTF_8).read();
            if (!currentManifest.equals(previousManifest)) {
                // TODO: Deeper comparison, call out just a version change.
                instantRunBuildContext.setVerifierResult(
                        InstantRunVerifierStatus.MANIFEST_FILE_CHANGE);
                Files.copy(manifestFileToPackage, previousManifestFile);
            }
        } else {
            Files.createParentDirs(previousManifestFile);
            Files.copy(manifestFileToPackage, previousManifestFile);
            // we don't have a back up of the manifest file, better be safe and force the APK build.
            instantRunBuildContext.setVerifierResult(InstantRunVerifierStatus.INITIAL_BUILD);
        }
    }

    @VisibleForTesting
    static void runManifestBinaryChangeVerifier(
            InstantRunBuildContext instantRunBuildContext,
            File instantRunSupportDir,
            @NonNull File resOutBaseNameFile)
            throws IOException {
        // get the new manifest file CRC
        String currentIterationCRC = null;
        try (JarFile jarFile = new JarFile(resOutBaseNameFile)) {
            ZipEntry entry = jarFile.getEntry(SdkConstants.ANDROID_MANIFEST_XML);
            if (entry != null) {
                currentIterationCRC = String.valueOf(entry.getCrc());
            }
        }

        File crcFile = new File(instantRunSupportDir, "manifest.crc");
        // check the manifest file binary format.
        if (crcFile.exists() && currentIterationCRC != null) {
            // compare its content with the new binary file crc.
            String previousIterationCRC = Files.readFirstLine(crcFile, Charsets.UTF_8);
            if (!currentIterationCRC.equals(previousIterationCRC)) {
                instantRunBuildContext.setVerifierResult(
                        InstantRunVerifierStatus.BINARY_MANIFEST_FILE_CHANGE);
            }
        } else {
            // we don't have a back up of the crc file, better be safe and force the APK build.
            instantRunBuildContext.setVerifierResult(InstantRunVerifierStatus.INITIAL_BUILD);
        }

        if (currentIterationCRC != null) {
            // write the new manifest file CRC.
            Files.createParentDirs(crcFile);
            Files.write(currentIterationCRC, crcFile, Charsets.UTF_8);
        }
    }

    public static class ConfigAction implements TaskConfigAction<CheckManifestInInstantRunMode> {

        @NonNull
        protected final TransformVariantScope transformVariantScope;
        @NonNull
        protected final InstantRunVariantScope instantRunVariantScope;
        @NonNull
        protected final SupplierTask<File> instantRunMergedManifest;
        @NonNull
        protected final SupplierTask<File> processedResourcesOutputFile;


        public ConfigAction(
                @NonNull TransformVariantScope transformVariantScope,
                @NonNull InstantRunVariantScope instantRunVariantScope,
                @NonNull SupplierTask<File> instantRunMergedManifest,
                @NonNull SupplierTask<File> processedResourcesOutputFile) {
            this.transformVariantScope = transformVariantScope;
            this.instantRunVariantScope = instantRunVariantScope;
            this.instantRunMergedManifest = instantRunMergedManifest;
            this.processedResourcesOutputFile = processedResourcesOutputFile;
        }

        @NonNull
        @Override
        public String getName() {
            return transformVariantScope.getTaskName("checkManifestChanges");
        }

        @NonNull
        @Override
        public Class<CheckManifestInInstantRunMode> getType() {
            return CheckManifestInInstantRunMode.class;
        }

        @Override
        public void execute(@NonNull CheckManifestInInstantRunMode task) {

            ConventionMappingHelper.map(task, "packageOutputFile",
                    processedResourcesOutputFile::get);

            ConventionMappingHelper.map(task, "instantRunManifestFile",
                    instantRunMergedManifest::get);

            task.instantRunBuildContext = instantRunVariantScope.getInstantRunBuildContext();
            task.instantRunSupportDir = instantRunVariantScope.getInstantRunSupportDir();
            task.setVariantName(transformVariantScope.getFullVariantName());
        }
    }
}
