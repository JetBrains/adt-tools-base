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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
/**
 * A task that processes the manifest
 */
public class ProcessAppManifest extends ProcessManifest {

    // ----- PRIVATE TASK API -----

    @InputFile
    File mainManifest

    @InputFiles
    List<File> manifestOverlays

    @Nested
    List<ManifestDependencyImpl> libraries

    @Input @Optional
    String packageNameOverride

    @Input
    int versionCode

    @Input @Optional
    String versionName

    @Input
    int minSdkVersion

    @Input
    int targetSdkVersion

    @Override
    protected void doFullTaskAction() {
        getBuilder().processManifest(
                getMainManifest(),
                getManifestOverlays(),
                getLibraries(),
                getPackageNameOverride(),
                getVersionCode(),
                getVersionName(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getManifestOutputFile().absolutePath)
    }

}
