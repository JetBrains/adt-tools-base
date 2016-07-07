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
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.profile.ProcessRecorder;
import com.android.ide.common.res2.FileStatus;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.tooling.BuildException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Task to package an Android application (APK).
 */
@ParallelizableTask
public class PackageApplication extends PackageAndroidArtifact {
    /**
     * If {@code true}, the tasks works with the old code.
     */
    private boolean inOldMode;

    @Override
    protected boolean isIncremental() {
        return !inOldMode;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        if (inOldMode) {
            doOldTask();
            recordMetrics();
            return;
        }
        super.doFullTaskAction();
        recordMetrics();
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        if (inOldMode) {
            doFullTaskAction();
            recordMetrics();
            return;
        }
        super.doIncrementalTaskAction(changedInputs);
        recordMetrics();
    }

    /**
     * Old packaging code.
     */
    private void doOldTask() {
        try {

            ImmutableSet.Builder<File> dexFoldersForApk = ImmutableSet.builder();
            ImmutableList.Builder<File> javaResourcesForApk = ImmutableList.builder();

            Collection<File> javaResourceFiles = getJavaResourceFiles();
            if (javaResourceFiles != null) {
                javaResourcesForApk.addAll(javaResourceFiles);
            }
            switch(dexPackagingPolicy) {
                case INSTANT_RUN_SHARDS_IN_SINGLE_APK:
                    File zippedDexes = zipDexesForInstantRun(getDexFolders(), dexFoldersForApk);
                    javaResourcesForApk.add(zippedDexes);
                    break;
                case INSTANT_RUN_MULTI_APK:
                    for (File dexFolder : getDexFolders()) {
                        if (dexFolder.getName().contains(InstantRunSlicer.MAIN_SLICE_NAME)) {
                            dexFoldersForApk.add(dexFolder);
                        }
                    }
                    break;
                case STANDARD:
                    dexFoldersForApk.addAll(getDexFolders());
                    break;
                default:
                    throw new RuntimeException(
                            "Unhandled DexPackagingPolicy : " + getDexPackagingPolicy());
            }

            getBuilder().oldPackageApk(
                    getResourceFile().getAbsolutePath(),
                    dexFoldersForApk.build(),
                    javaResourcesForApk.build(),
                    getJniFolders(),
                    getAssets(),
                    getAbiFilters(),
                    getJniDebugBuild(),
                    getSigningConfig(),
                    getOutputFile(),
                    getMinSdkVersion(),
                    getNoCompressPredicate());
        } catch (DuplicateFileException e) {
            Logger logger = getLogger();
            logger.error("Error: duplicate files during packaging of APK " + getOutputFile()
                    .getAbsolutePath());
            logger.error("\tPath in archive: " + e.getArchivePath());
            int index = 1;
            for (File file : e.getSourceFiles()) {
                logger.error("\tOrigin " + (index++) + ": " + file);
            }
            logger.error("You can ignore those files in your build.gradle:");
            logger.error("\tandroid {");
            logger.error("\t  packagingOptions {");
            logger.error("\t    exclude \'" + e.getArchivePath() + "\'");
            logger.error("\t  }");
            logger.error("\t}");
            throw new BuildException(e.getMessage(), e);
        } catch (Exception e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Throwable rootCause = Throwables.getRootCause(e);
            if (rootCause instanceof NoSuchAlgorithmException) {
                throw new BuildException(
                        rootCause.getMessage() + ": try using a newer JVM to build your application.",
                        rootCause);
            }
            throw new BuildException(e.getMessage(), e);
        }

        // mark this APK production, this will eventually be saved when instant-run is enabled.
        // this might get overridden if the package is signed/aligned.
        try {
            instantRunContext.addChangedFile(instantRunFileType, getOutputFile());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    private File zipDexesForInstantRun(Iterable<File> dexFolders,
            ImmutableSet.Builder<File> dexFoldersForApk)
            throws IOException {

        File tmpZipFile = new File(instantRunSupportDir, "classes.zip");
        Files.createParentDirs(tmpZipFile);
        ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmpZipFile)));
        // no need to compress a zip, the APK itself gets compressed.
        zipFile.setLevel(0);

        try {
            for (File dexFolder : dexFolders) {
                if (dexFolder.getName().contains(INSTANT_RUN_PACKAGES_PREFIX)) {
                    dexFoldersForApk.add(dexFolder);
                } else {
                    for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dexFolder)) {
                        if (file.isFile() && file.getName().endsWith(SdkConstants.DOT_DEX)) {
                            // There are several pieces of code in the runtime library which depends on
                            // this exact pattern, so it should not be changed without thorough testing
                            // (it's basically part of the contract).
                            String entryName = file.getParentFile().getName() + "-" + file.getName();
                            zipFile.putNextEntry(new ZipEntry(entryName));
                            try {
                                Files.copy(file, zipFile);
                            } finally {
                                zipFile.closeEntry();
                            }
                        }

                    }
                }
            }
        } finally {
            zipFile.close();
        }

        // now package that zip file as a zip since this is what the packager is expecting !
        File finalResourceFile = new File(instantRunSupportDir, "resources.zip");
        zipFile = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(finalResourceFile)));
        try {
            zipFile.putNextEntry(new ZipEntry("instant-run.zip"));
            try {
                Files.copy(tmpZipFile, zipFile);
            } finally {
                zipFile.closeEntry();
            }
        } finally {
            zipFile.close();
        }

        return finalResourceFile;
    }

    private void recordMetrics() {
        long metricsStartTime = System.nanoTime();
        AndroidStudioStats.GradleBuildProjectMetrics.Builder metrics =
                AndroidStudioStats.GradleBuildProjectMetrics.newBuilder();

        Long apkSize = getSize(getOutputFile());
        if (apkSize != null) {
            metrics.setApkSize(apkSize);
        }

        Long resourcesApSize = getSize(getResourceFile());
        if (resourcesApSize != null) {
            metrics.setResourcesApSize(resourcesApSize);
        }

        metrics.setMetricsTimeNs(System.nanoTime() - metricsStartTime);

        ProcessRecorder.getProject(getProject().getPath()).setMetrics(metrics);
    }

    @Nullable
    private static Long getSize(@Nullable File file) {
        if (file == null) {
            return null;
        }
        try {
            return java.nio.file.Files.size(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    // ----- ConfigAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all
     * files that should end up in the APK.
     */
    public static class StandardConfigAction
            extends PackageAndroidArtifact.ConfigAction<PackageApplication> {

        public StandardConfigAction(
                @NonNull PackagingScope scope,
                @Nullable InstantRunPatchingPolicy patchingPolicy) {
            super(scope, patchingPolicy);
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void execute(@NonNull final PackageApplication packageApplication) {
            ConventionMappingHelper.map(
                    packageApplication, "outputFile", packagingScope::getPackageApk);

            packageApplication.inOldMode =
                    AndroidGradleOptions.useOldPackaging(packagingScope.getProject());

            super.execute(packageApplication);
        }
    }

    /**
     * Configures the task to only package resources and assets.
     */
    public static class InstantRunResourcesConfigAction
            extends PackageAndroidArtifact.ConfigAction<PackageApplication> {

        @NonNull
        private final File mOutputFile;

        public InstantRunResourcesConfigAction(
                @NonNull File outputFile,
                @NonNull PackagingScope scope,
                @Nullable InstantRunPatchingPolicy patchingPolicy) {
            super(scope, patchingPolicy);
            mOutputFile = outputFile;
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("packageInstantRunResources");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void execute(@NonNull final PackageApplication packageApplication) {
            packageApplication.setOutputFile(mOutputFile);

            packageApplication.inOldMode =
                    AndroidGradleOptions.useOldPackaging(packagingScope.getProject());

            packageApplication.instantRunFileType = InstantRunBuildContext.FileType.RESOURCES;

            super.execute(packageApplication);

            // Don't try to add any special dex files to this zip.
            packageApplication.dexPackagingPolicy = DexPackagingPolicy.STANDARD;

            // Skip files which are not needed for hot/cold swap.
            for (String prop : ImmutableList.of("dexFolders", "jniFolders", "javaResourceFiles")) {
                ConventionMappingHelper.map(
                        packageApplication, prop, Collections::<File>emptySet);
            }

            // Don't sign.
            packageApplication.setSigningConfig(null);
        }
    }
}
