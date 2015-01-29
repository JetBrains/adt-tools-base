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

import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.core.AaptPackageProcessBuilder
import com.android.builder.core.VariantType
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask

@ParallelizableTask
public class ProcessAndroidResources extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @InputFile
    File manifestFile

    @InputDirectory
    @OutputDirectory
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

    @Input
    Collection<String> resourceConfigs

    @Input @Optional
    String preferredDensity

    // ----- PRIVATE TASK API -----
    @Input
    String getBuildToolsVersion() {
        getBuildTools().getRevision()
    }

    @Nested @Optional
    List<SymbolFileProviderImpl> libraries

    @Input @Optional
    String packageForR

    @Nested @Optional
    Collection<String> splits

    @Input
    boolean enforceUniquePackageName

    // this doesn't change from one build to another, so no need to annotate
    VariantType type

    @Input
    boolean debuggable

    @Input
    boolean pseudoLocalesEnabled

    @Nested
    AaptOptions aaptOptions

    private boolean isSplitPackage(File file, File resBaseName) {
        if (file.getName().startsWith(resBaseName.getName())) {
            for (String split : splits) {
                if (file.getName().contains(split)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doFullTaskAction() {
        // we have to clean the source folder output in case the package name changed.
        File srcOut = getSourceOutputDir()
        if (srcOut != null) {
            emptyFolder(srcOut)
        }

        File resOutBaseNameFile = getPackageOutputFile()

        // we have to check the resource output folder in case some splits were removed, we should
        // manually remove them.
        File packageOutputFolder = getResDir()
        if (resOutBaseNameFile != null) {
            for (File file : packageOutputFolder.listFiles()) {
                if (!isSplitPackage(file, resOutBaseNameFile)) {
                    file.delete();
                }
            }
        }

        AaptPackageProcessBuilder aaptPackageCommandBuilder =
                new AaptPackageProcessBuilder(getManifestFile(), getAaptOptions())
                    .setAssetsFolder(getAssetsDir())
                    .setResFolder(getResDir())
                    .setLibraries(getLibraries())
                    .setPackageForR(getPackageForR())
                    .setSourceOutputDir(srcOut?.absolutePath)
                    .setSymbolOutputDir(getTextSymbolOutputDir()?.absolutePath)
                    .setResPackageOutput(resOutBaseNameFile?.absolutePath)
                    .setProguardOutput(getProguardOutputFile()?.absolutePath)
                    .setType(getType())
                    .setDebuggable(getDebuggable())
                    .setPseudoLocalesEnabled(getPseudoLocalesEnabled())
                    .setResourceConfigs(getResourceConfigs())
                    .setSplits(getSplits())
                    .setPreferredDensity(getPreferredDensity())

        getBuilder().processResources(
                aaptPackageCommandBuilder,
                getEnforceUniquePackageName())
    }
}
