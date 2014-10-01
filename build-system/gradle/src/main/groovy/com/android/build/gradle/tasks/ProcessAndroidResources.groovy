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

import com.android.build.gradle.api.ApkOutput
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dsl.AaptOptionsImpl
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.core.VariantConfiguration
import com.google.gson.Gson
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

import java.util.regex.Matcher
import java.util.regex.Pattern

public class ProcessAndroidResources extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @InputFile
    File manifestFile

    @InputDirectory
    File resDir

    @InputDirectory @Optional
    File assetsDir

    @OutputDirectory @Optional
    File sourceOutputDir

    @OutputDirectory @Optional
    File textSymbolOutputDir

    @OutputFile @Optional
    File packageOutputFile

    @OutputFile @Optional
    File proguardOutputFile

    @OutputFile @Optional
    File splitInfoOutputFile

    @Input
    Collection<String> resourceConfigs

    // ----- PRIVATE TASK API -----

    @Nested @Optional
    List<SymbolFileProviderImpl> libraries

    @Input @Optional
    String packageForR

    @Nested @Optional
    Collection<String> splits

    @Input
    boolean enforceUniquePackageName

    // this doesn't change from one build to another, so no need to annotate
    VariantConfiguration.Type type

    @Input
    boolean debuggable

    @Nested
    AaptOptionsImpl aaptOptions

    @Override
    protected void doFullTaskAction() {
        // we have to clean the source folder output in case the package name changed.
        File srcOut = getSourceOutputDir()
        if (srcOut != null) {
            emptyFolder(srcOut)
        }

        File resOutBaseNameFile = getPackageOutputFile()
        getBuilder().processResources(
                getManifestFile(),
                getResDir(),
                getAssetsDir(),
                getLibraries(),
                getPackageForR(),
                srcOut?.absolutePath,
                getTextSymbolOutputDir()?.absolutePath,
                resOutBaseNameFile?.absolutePath,
                getProguardOutputFile()?.absolutePath,
                getType(),
                getDebuggable(),
                getAaptOptions(),
                getResourceConfigs(),
                getEnforceUniquePackageName(),
                getSplits()
        )

        if (resOutBaseNameFile != null && splits!=null) {
            File resOutBaseDirectory = resOutBaseNameFile.getParentFile();
            String resOutputBaseName = resOutBaseNameFile.getName();
            final Pattern pattern = Pattern.compile("${resOutputBaseName}_([h|x|d|p|i|m]*)(.*)")

            List<ApkOutput> variantOutputList = new ArrayList<ApkOutput>();
            for (File f : resOutBaseDirectory.listFiles()) {

                Matcher matcher = pattern.matcher(f.getName());
                if (matcher.matches()) {
                    ApkOutput variantOutput = new ApkOutput.SplitApkOutput(
                            ApkOutput.OutputType.SPLIT,
                            ApkOutput.SplitType.DENSITY,
                            matcher.group(1),
                            matcher.group(2),
                            f)
                    variantOutputList.add(variantOutput)
                }
            }
            Gson gson = new Gson();
            println gson.toJson(variantOutputList);
            FileWriter fileWriter = new FileWriter(getSplitInfoOutputFile());
            fileWriter.append(gson.toJson(variantOutputList));
            fileWriter.close()
        }

    }
}
