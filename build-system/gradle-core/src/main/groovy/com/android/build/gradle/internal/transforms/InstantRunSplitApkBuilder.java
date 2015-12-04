/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext.FileType;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.ide.common.signing.KeytoolException;
import com.google.common.io.Files;

import org.gradle.api.Action;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Tasks to generate M+ style pure splits APKs with dex files.
 */
public class InstantRunSplitApkBuilder extends BaseTask {

    private Set<File> dexFolders;
    private File outputDirectory;
    private SigningConfig signingConf;
    private String applicationId;
    private int versionCode;
    private String versionName;
    private InstantRunBuildContext instantRunBuildContext;

    @Input
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    @Input
    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    @InputFiles
    public Set<File> getDexFolders() {
        return dexFolders;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void run(IncrementalTaskInputs inputs)
            throws IOException, DuplicateFileException, KeytoolException, PackagerException {
        if (inputs.isIncremental()) {

            inputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    try {
                        generateSplitApk(new DexFile(
                                inputFileDetails.getFile(),
                                inputFileDetails.getFile().getParentFile().getName()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            inputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    String outputFileName = new DexFile(
                            inputFileDetails.getFile(),
                            inputFileDetails.getFile().getParentFile().getName()).encodeName();
                    new File(getOutputDirectory(), outputFileName).delete();
                }
            });
        } else {
            List<DexFile> allFiles = new ArrayList<DexFile>();
            for (File dexFolder : getDexFolders()) {
                if (dexFolder.isDirectory()) {
                    File[] files = dexFolder.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            allFiles.add(new DexFile(file, dexFolder.getName()));
                        }
                    }
                }
            }
            for (DexFile file : allFiles) {
                // generate a split APK for each.
                generateSplitApk(file);
            }
        }
    }

    private void generateSplitApk(DexFile file)
            throws IOException, DuplicateFileException, KeytoolException, PackagerException {

        File outputLocation = new File(getOutputDirectory(), file.encodeName());
        Files.createParentDirs(outputLocation);
        File manifestFile = generateSplitApkManifest(file.encodeName());
        getBuilder().packageCodeSplitApk(
                file.dexFile, signingConf, manifestFile, outputLocation.getAbsolutePath());
        instantRunBuildContext.addChangedFile(FileType.SPLIT, outputLocation);
        manifestFile.delete();
    }

    private File generateSplitApkManifest(String uniqueName) throws IOException {

        String versionNameToUse = getVersionName();
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(getVersionCode());
        }

        File tmpFile = File.createTempFile("AndroidManifest_" + uniqueName, ".xml");
        OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8");
        try {
            fileWriter.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                    + "      package=\"" + getApplicationId() + "\"\n"
                    + "      android:versionCode=\"" + getVersionCode() + "\"\n"
                    + "      android:versionName=\"" + versionNameToUse + "\"\n"
                    + "      split=\"lib_" + uniqueName + "\">\n"
                    + "       <uses-sdk android:minSdkVersion=\"23\"/>\n" + "</manifest> ");
            fileWriter.flush();
        } finally {
            fileWriter.close();
        }
        return tmpFile;
    }

    private static class DexFile {
        private final File dexFile;
        private final String dexFolderName;

        private DexFile(@NonNull File dexFile, @NonNull String dexFolderName) {
            this.dexFile = dexFile;
            this.dexFolderName = dexFolderName;
        }

        private String encodeName() {
            return dexFolderName + ".apk";
        }
    }

    public static class ConfigAction implements TaskConfigAction<InstantRunSplitApkBuilder> {

        private final VariantScope variantScope;

        public ConfigAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("instantRun", "PureSplitBuilder");
        }

        @NonNull
        @Override
        public Class<InstantRunSplitApkBuilder> getType() {
            return InstantRunSplitApkBuilder.class;
        }

        @Override
        public void execute(@NonNull InstantRunSplitApkBuilder task) {

            final GradleVariantConfiguration config = variantScope.getVariantConfiguration();

            task.outputDirectory = variantScope.getInstantRunSplitApkOutputFolder();
            task.signingConf = config.getSigningConfig();
            task.setApplicationId(config.getApplicationId());
            task.setVersionCode(config.getVersionCode());
            task.setVersionName(config.getVersionName());
            task.setVariantName(
                    variantScope.getVariantConfiguration().getFullName());
            task.setAndroidBuilder(variantScope.getGlobalScope().getAndroidBuilder());
            task.instantRunBuildContext = variantScope.getInstantRunBuildContext();

            ConventionMappingHelper
                    .map(task, "dexFolders", new Callable<Set<File>>() {
                        @Override
                        public  Set<File> call() {
                            if (config.getUseJack()) {
                                throw new IllegalStateException(
                                        "InstantRun does not support Jack compiler yet.");
                            }

                            return variantScope.getTransformManager()
                                    .getPipelineOutput(PackageApplication.sDexFilter).keySet();
                        }
                    });

        }
    }
}
