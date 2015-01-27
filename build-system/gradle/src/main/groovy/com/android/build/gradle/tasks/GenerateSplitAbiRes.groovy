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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.builder.core.AaptPackageProcessBuilder
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

/**
 * Generates all metadata (like AndroidManifest.xml) necessary for a ABI dimension split APK.
 */
class GenerateSplitAbiRes extends BaseTask {

    @Input
    String applicationId

    @Input
    int versionCode

    @Input
    @Optional
    String versionName

    @Input
    String outputBaseName

    @Input
    Set<String> splits

    File outputDirectory

    @OutputFiles
    List<File> getOutputFiles() {
        List<File> outputFiles = new ArrayList<>();
        for (String split : getSplits()) {
            outputFiles.add(getOutputFileForSplit(split))
        }
        return outputFiles;
    }

    @Input
    boolean debuggable

    @Nested
    AaptOptions aaptOptions

    @TaskAction
    protected void doFullTaskAction() {

        for (String split : getSplits()) {
            String resPackageFileName = getOutputFileForSplit(split).getAbsolutePath()

            File tmpDirectory = new File(getOutputDirectory(), "${getOutputBaseName()}")
            tmpDirectory.mkdirs()

            File tmpFile = new File(tmpDirectory, "AndroidManifest.xml")

            OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8");
            String versionNameToUse = getVersionName();
            if (versionNameToUse == null) {
                versionNameToUse = String.valueOf(getVersionCode())
            }
            try {
                fileWriter.append(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                "      package=\"" + getApplicationId() + "\"\n" +
                                "      android:versionCode=\"" + getVersionCode() + "\"\n" +
                                "      android:versionName=\"" + versionNameToUse + "\"\n" +
                                "      split=\"lib_${getOutputBaseName()}\">\n" +
                                "       <uses-sdk android:minSdkVersion=\"21\"/>\n" +
                                "</manifest> ")
                fileWriter.flush()
            } finally {
                fileWriter.close()
            }

            AaptPackageProcessBuilder aaptPackageCommandBuilder =
                    new AaptPackageProcessBuilder(tmpFile, getAaptOptions())
                        .setDebuggable(getDebuggable())
                        .setResPackageOutput(resPackageFileName);

            getBuilder().processResources(aaptPackageCommandBuilder, false /* enforceUniquePackageName */)
        }
    }

    private File getOutputFileForSplit(String split) {
        return new File(getOutputDirectory(), "resources-${getOutputBaseName()}-${split}.ap_")
    }
}
