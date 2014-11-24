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

package com.android.build.gradle.internal.tasks
import com.android.builder.core.AndroidBuilder
import com.google.common.io.Files
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE
import static com.android.SdkConstants.FD_RES_RAW
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK
/**
 * Task to generate micro app data res file.
 */
public class GenerateApkDataTask extends BaseTask {

    @Input
    String getBuildToolsVersion() {
        plugin.extension.buildToolsRevision
    }

    @InputFile
    File apkFile

    @OutputDirectory
    File resOutputDir

    @OutputFile
    File manifestFile

    @Input
    String mainPkgName

    @Input
    int minSdkVersion

    @TaskAction
    void generate() {
        // always empty output dir.
        File outDir = getResOutputDir()
        emptyFolder(outDir)

        File apk = getApkFile()
        // copy the file into the destination, by sanitizing the name first.
        File rawDir = new File(outDir, FD_RES_RAW)
        rawDir.mkdirs()

        File to = new File(rawDir, ANDROID_WEAR_MICRO_APK + DOT_ANDROID_PACKAGE)
        Files.copy(apk, to)

        // now create the matching XML and the manifest entry.
        AndroidBuilder builder = getBuilder();

        builder.generateApkData(apk, outDir, getMainPkgName(), ANDROID_WEAR_MICRO_APK)
        builder.generateApkDataEntryInManifest(getMinSdkVersion(), getManifestFile())
    }
}
