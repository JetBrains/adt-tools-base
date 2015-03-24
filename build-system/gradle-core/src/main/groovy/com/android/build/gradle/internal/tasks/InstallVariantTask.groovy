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

import com.android.annotations.NonNull
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.SplitSelectTool
import com.android.builder.core.VariantConfiguration
import com.android.builder.internal.InstallUtils
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConfig
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.ddmlib.IDevice
import com.android.ide.common.build.SplitOutputMatcher
import com.android.ide.common.process.BaseProcessOutputHandler
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessOutput
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
public class InstallVariantTask extends BaseTask {

    @InputFile
    File adbExe

    @InputFile
    @Optional
    File splitSelectExe

    ProcessExecutor processExecutor;

    String projectName

    @Input
    int timeOutInMs = 0

    @Input @Optional
    Collection<String> installOptions;

    BaseVariantData<? extends BaseVariantOutputData> variantData
    InstallVariantTask() {
        this.getOutputs().upToDateWhen {
            logger.debug("Install task is always run.");
            false;
        }
    }

    @TaskAction
    void install() {
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe())
        deviceProvider.init()

        VariantConfiguration variantConfig = variantData.variantConfiguration
        String variantName = variantConfig.fullName

        String serial = System.getenv("ANDROID_SERIAL");

        int successfulInstallCount = 0;

        for (DeviceConnector device : deviceProvider.getDevices()) {
            if (serial != null && !serial.equals(device.getSerialNumber())) {
                continue;
            }

            if (device.getState() != IDevice.DeviceState.UNAUTHORIZED) {
                if (InstallUtils.checkDeviceApiLevel(
                        device, variantConfig.minSdkVersion, getILogger(), projectName,
                        variantName)) {

                    // build the list of APKs.
                    List<String> splitApksPath = new ArrayList<>();
                    OutputFile mainApk;
                    for (VariantOutput output : variantData.outputs) {
                        for (OutputFile outputFile : output.getOutputs()) {
                            if (outputFile.getOutputFile().getAbsolutePath() !=
                                output.getMainOutputFile().getOutputFile().getAbsolutePath()) {

                                splitApksPath.add(outputFile.outputFile.getAbsolutePath())
                            }
                        }
                        mainApk = output.getMainOutputFile()
                    }

                    List<File> apkFiles = new ArrayList<>();
                    if (getSplitSelectExe() == null && splitApksPath.size() > 0) {
                        throw new GradleException(
                                "Pure splits installation requires build tools 22 or above");
                    }
                    if (mainApk == null) {
                        throw new GradleException(
                                "Cannot retrieve the main APK from variant outputs");
                    }
                    if (splitApksPath.size() > 0) {
                        DeviceConfig deviceConfig = device.getDeviceConfig();
                        Set<String> resultApksPath = new HashSet<String>();
                        for (String abi : device.getAbis()) {
                            resultApksPath.addAll(SplitSelectTool.splitSelect(
                                    processExecutor,
                                    getSplitSelectExe(),
                                    deviceConfig.getConfigFor(abi),
                                    mainApk.getOutputFile().getAbsolutePath(),
                                    splitApksPath));
                        }
                        for (String resultApkPath : resultApksPath) {
                            apkFiles.add(new File(resultApkPath));
                        }
                        // and add back the main APK.
                        apkFiles.add(mainApk.getOutputFile())
                    } else {
                        // now look for a matching output file
                        List<OutputFile> outputFiles = SplitOutputMatcher.computeBestOutput(
                                variantData.outputs,
                                variantData.variantConfiguration.getSupportedAbis(),
                                device.getDensity(),
                                device.getLanguage(),
                                device.getRegion(),
                                device.getAbis())

                        apkFiles = ((List<ApkOutputFile>) outputFiles)*.getOutputFile()
                    }

                    if (apkFiles.isEmpty()) {
                        logger.lifecycle(
                                "Skipping device '${device.getName()}' for '${projectName}:${variantName}': " +
                                        "Could not find build of variant which supports density ${device.getDensity()} " +
                                        "and an ABI in " + Joiner.on(", ").join(device.getAbis()));
                    } else {
                        logger.lifecycle("Installing APK '${Joiner.on(", ").join(apkFiles*.getName())}'" +
                                " on '${device.getName()}'")

                        List<String> extraArgs = installOptions == null ? ImmutableList.of() : installOptions;
                        if (apkFiles.size() > 1 || device.getApiLevel() >= 21) {
                            device.installPackages(apkFiles, extraArgs, getTimeOutInMs(), getILogger());
                            successfulInstallCount++
                        } else {
                            device.installPackage(apkFiles.get(0), extraArgs, getTimeOutInMs(), getILogger())
                            successfulInstallCount++
                        }
                    }
                } // When InstallUtils.checkDeviceApiLevel returns false, it logs the reason.
            } else {
                logger.lifecycle(
                        "Skipping device '${device.getName()}' for '${projectName}:${variantName}" +
                                "': Device not authorized, see http://developer.android.com/tools/help/adb.html#Enabling.");

            }
        }

        if (successfulInstallCount == 0) {
            if (serial != null) {
                throw new GradleException("Failed to find device with serial '${serial}'. " +
                        "Unset ANDROID_SERIAL to search for any device.")
            } else {
                throw new GradleException("Failed to install on any devices.")
            }
        } else {
            logger.quiet("Installed on ${successfulInstallCount} ${successfulInstallCount==1?'device':'devices'}.");
        }
    }
}
