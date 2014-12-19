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
package com.android.build.gradle.tasks
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.android.build.gradle.internal.variant.ApkVariantOutputData
import com.android.builder.core.VariantConfiguration
import com.android.manifmerger.ManifestMerger2
import com.google.common.collect.Lists
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
/**
 * A task that processes the manifest
 */
public class MergeManifests extends ManifestProcessorTask {

    // ----- PRIVATE TASK API -----
    @InputFile
    File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @InputFiles
    List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    @Input @Optional
    String getPackageOverride() {
        return variantConfiguration.getIdOverride();
    }

    @Input
    int getVersionCode() {
        if (variantOutputData!= null) {
            return variantOutputData.versionCode
        }

        return variantConfiguration.versionCode;
    }

    @Input @Optional
    String getVersionName() {
        if (variantOutputData!= null) {
            return variantOutputData.versionName
        }
        return variantConfiguration.getVersionName();
    }

    @Input @Optional
    String minSdkVersion

    @Input @Optional
    String targetSdkVersion

    @Input @Optional
    Integer maxSdkVersion

    @Input @Optional
    File reportFile

    /**
     * Return a serializable version of our map of key value pairs for placeholder substitution.
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input @Optional
    String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    VariantConfiguration variantConfiguration
    ApkVariantOutputData variantOutputData
    List<ManifestDependencyImpl> libraries

    /**
     * since libraries above can't return it's input files (@Nested doesn't
     * work on lists), so do a method that will gather them and return them.
     */
    @InputFiles
    List<File> getLibraryManifests() {
        List<ManifestDependencyImpl> libs = getLibraries()
        if (libs == null || libs.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> files = Lists.newArrayListWithCapacity(libs.size() * 2)
        for (ManifestDependencyImpl mdi : libs) {
            files.addAll(mdi.getAllManifests())
        }

        return files;
    }

    @Override
    protected void doFullTaskAction() {

        getBuilder().mergeManifests(
                getMainManifest(),
                getManifestOverlays(),
                getLibraries(),
                getPackageOverride(),
                getVersionCode(),
                getVersionName(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getMaxSdkVersion(),
                getManifestOutputFile().absolutePath,
                // no appt friendly merged manifest file necessary for applications.
                null /* aaptFriendlyManifestOutputFile */ ,
                ManifestMerger2.MergeType.APPLICATION,
                variantConfiguration.getManifestPlaceholders(),
                getReportFile())
    }
}
