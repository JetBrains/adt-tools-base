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

import static com.android.sdklib.BuildToolInfo.PathId.ZIP_ALIGN;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext.FileType;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.io.Files;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tasks to generate M+ style pure splits APKs with dex files.
 */
public class InstantRunSplitApkBuilder extends BaseTask {

    private Set<File> dexFolders;
    private File outputDirectory;
    private SigningConfig signingConf;
    private String applicationId;
    private InstantRunBuildContext instantRunBuildContext;
    private File supportDir;
    private File incrementalDir;
    private int versionCode;
    private String versionName;
    private Aapt aapt;

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

    @Input
    @Optional
    public String getVersionName() {
        return versionName;
    }


    @InputFiles
    public Set<File> getDexFolders() {
        return dexFolders;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    // Method is here to allow being overridden through convention mapping.
    @Input
    public File getZipAlignExe() {
        return null;
    }

    @TaskAction
    public void run(IncrementalTaskInputs inputs)
            throws IOException, KeytoolException, PackagerException,
            ProcessException, InterruptedException {
        if (inputs.isIncremental()) {

            inputs.outOfDate(inputFileDetails -> {
                try {
                    // we generate APKs for all slices but the main slice which will get
                    // packaged in the main APK.
                    if (!inputFileDetails.getFile().getName().contains(
                            InstantRunSlicer.MAIN_SLICE_NAME)) {
                        generateSplitApk(new DexFile(
                                inputFileDetails.getFile(),
                                inputFileDetails.getFile().getParentFile().getName()));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            inputs.removed(inputFileDetails -> {
                DexFile dexFile = new DexFile(
                        inputFileDetails.getFile(),
                        inputFileDetails.getFile().getParentFile().getName());

                String outputFileName = dexFile.encodeName() + "_unaligned.apk";
                new File(getOutputDirectory(), outputFileName).delete();
                outputFileName = dexFile.encodeName() + ".apk";
                new File(getOutputDirectory(), outputFileName).delete();
            });
        } else {
            List<DexFile> allFiles = new ArrayList<>();
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
                if (!file.dexFile.getParentFile().getName().contains(
                        InstantRunSlicer.MAIN_SLICE_NAME)) {
                    generateSplitApk(file);
                }
            }
        }
    }

    private void generateSplitApk(DexFile file)
            throws IOException, DuplicateFileException, KeytoolException, PackagerException,
            InterruptedException, ProcessException {

        final File outputLocation = new File(getOutputDirectory(), file.encodeName()
                + "_unaligned.apk");
        Files.createParentDirs(outputLocation);
        File resPackageFile = generateSplitApkManifest(file.encodeName());
        getBuilder().packageCodeSplitApk(resPackageFile.getAbsolutePath(),
                file.dexFile, signingConf, outputLocation);
        // zip align it.
        final File alignedOutput = new File(getOutputDirectory(), file.encodeName() + ".apk");
        ProcessInfoBuilder processInfoBuilder = new ProcessInfoBuilder();
        processInfoBuilder.setExecutable(getZipAlignExe());
        processInfoBuilder.addArgs("-f", "4");
        processInfoBuilder.addArgs(outputLocation.getAbsolutePath());
        processInfoBuilder.addArgs(alignedOutput.getAbsolutePath());

        getBuilder().executeProcess(processInfoBuilder.createProcess(),
                new LoggedProcessOutputHandler(getILogger()));

        instantRunBuildContext.addChangedFile(FileType.SPLIT, alignedOutput);
        resPackageFile.delete();
    }

    // todo, move this to a sub task, as it is reusable between invocations.
    private File generateSplitApkManifest(String uniqueName)
            throws IOException, ProcessException, InterruptedException {

        String versionNameToUse = getVersionName();
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(getVersionCode());
        }

        File sliceSupportDir = new File(supportDir, uniqueName);
        sliceSupportDir.mkdirs();
        File androidManifest = new File(sliceSupportDir, "AndroidManifest.xml");
        try (OutputStreamWriter fileWriter =
                     new OutputStreamWriter(new FileOutputStream(androidManifest), "UTF-8")) {
            fileWriter.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                    + "      package=\"" + getApplicationId() + "\"\n"
                    + "      android:versionCode=\"" + getVersionCode() + "\"\n"
                    + "      android:versionName=\"" + versionNameToUse + "\"\n"
                    + "      split=\"lib_" + uniqueName + "_apk\">\n"
                    //+ "       <uses-sdk android:minSdkVersion=\"21\"/>\n" + "</manifest>\n");
                    + "</manifest>\n");
            fileWriter.flush();
        }

        File resFilePackageFile = new File(supportDir, "resources_ap");

        AaptPackageConfig.Builder aaptConfig = new AaptPackageConfig.Builder()
                .setManifestFile(androidManifest)
                .setOptions(getAaptOptions())
                .setDebuggable(true)
                .setVariantType(VariantType.DEFAULT)
                .setResourceOutputApk(resFilePackageFile);

        getBuilder().processResources(
                aapt,
                aaptConfig,
                false /* enforceUniquePackageName */);

        return resFilePackageFile;
    }

    // Method is here to allow being overridden through convention mapping.
    public AaptOptions getAaptOptions() {
        return null;
    }

    private static class DexFile {
        private final File dexFile;
        private final String dexFolderName;

        private DexFile(@NonNull File dexFile, @NonNull String dexFolderName) {
            this.dexFile = dexFile;
            this.dexFolderName = dexFolderName;
        }

        private String encodeName() {
            return dexFolderName.replace('-', '_');
        }
    }

    public static class ConfigAction implements TaskConfigAction<InstantRunSplitApkBuilder> {

        private final PackagingScope packagingScope;

        public ConfigAction(@NonNull PackagingScope packagingScope) {
            this.packagingScope = packagingScope;
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("buildInstantRunPureSplits");
        }

        @NonNull
        @Override
        public Class<InstantRunSplitApkBuilder> getType() {
            return InstantRunSplitApkBuilder.class;
        }

        @Override
        public void execute(@NonNull InstantRunSplitApkBuilder task) {
            AndroidBuilder androidBuilder = packagingScope.getAndroidBuilder();

            task.outputDirectory = packagingScope.getInstantRunSplitApkOutputFolder();
            task.signingConf = packagingScope.getSigningConfig();
            task.setApplicationId(packagingScope.getApplicationId());
            task.setVariantName(packagingScope.getFullVariantName());
            task.setAndroidBuilder(androidBuilder);
            task.instantRunBuildContext = packagingScope.getInstantRunBuildContext();
            task.supportDir = packagingScope.getInstantRunSupportDir();
            task.incrementalDir = packagingScope.getIncrementalDir(task.getName());
            task.versionCode = packagingScope.getVersionCode();
            task.versionName = packagingScope.getVersionName();

            task.aapt =
                    AaptGradleFactory.make(
                            androidBuilder,
                            true,
                            true,
                            packagingScope.getProject(),
                            packagingScope.getVariantType(),
                            FileUtils.mkdirs(new File(
                                    packagingScope.getIncrementalDir(getName()),
                                    "aapt-temp")),
                            0);

            ConventionMappingHelper.map(task, "zipAlignExe", () -> {
                final TargetInfo info = androidBuilder.getTargetInfo();
                if (info == null) {
                    return null;
                }
                String path1 = info.getBuildTools().getPath(ZIP_ALIGN);
                if (path1 == null) {
                    return null;
                }
                return new File(path1);
            });

            ConventionMappingHelper.map(task, "dexFolders", packagingScope::getDexFolders);
            ConventionMappingHelper.map(task, "aaptOptions", packagingScope::getAaptOptions);
        }
    }
}
