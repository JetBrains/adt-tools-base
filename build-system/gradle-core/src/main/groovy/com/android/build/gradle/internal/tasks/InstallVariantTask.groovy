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
import com.android.builder.core.VariantConfiguration
import com.android.builder.internal.InstallUtils
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConfig
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.ddmlib.IDevice
import com.android.ide.common.build.SplitOutputMatcher
import com.android.ide.common.process.BaseProcessOutputHandler
import com.android.ide.common.process.BaseProcessOutputHandler.BaseProcessOutput
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.process.ProcessOutput
import com.google.common.base.Joiner
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
                    List<String> apksPath = new ArrayList<>();
                    for (VariantOutput output : variantData.outputs) {
                        for (OutputFile outputFile : output.getOutputs()) {
                            apksPath.add(outputFile.outputFile.getAbsolutePath());
                        }
                    }

                    List<File> apkFiles = new ArrayList<>();
                    // starting in API 22, we can delegate to split-select the APK selection.
                    if (apksPath.size() > 1 && device.getApiLevel() >= 22) {
                        DeviceConfig deviceConfig = device.getDeviceConfig();

                        Set<String> resultApksPath = new HashSet<String>();
                        for (String abi : device.getAbis()) {
                            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
                            processBuilder.setExecutable(getSplitSelectExe());

                            processBuilder.addArgs("--target", deviceConfig.getConfigFor(abi));
                            for (String apkPath : apksPath) {
                                processBuilder.addArgs("--split", apkPath);
                            }
                            SplitSelectOutputHandler outputHandler =
                                    new SplitSelectOutputHandler(getLogger());

                            processExecutor.execute(processBuilder.createProcess(), outputHandler)
                                    .rethrowFailure()
                                    .assertNormalExitValue();

                            for (String apkPath : outputHandler.getResultApks()) {
                                resultApksPath.add(apkPath);
                            }
                        }
                        for (String resultApkPath : resultApksPath) {
                            apkFiles.add(new File(resultApkPath));
                        }
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
                        if (apkFiles.size() > 1 || device.getApiLevel() >= 21) {
                            device.installPackages(apkFiles, getTimeOutInMs(), getILogger());
                            successfulInstallCount++
                        } else {
                            device.installPackage(apkFiles.get(0), getTimeOutInMs(), getILogger())
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

    private static class SplitSelectOutputHandler extends BaseProcessOutputHandler {

        private final List<String> resultApks = new ArrayList<>();

        @NonNull
        private final Logger mLogger;

        public SplitSelectOutputHandler(@NonNull Logger logger) {
            mLogger = logger;
        }

        @NonNull
        public List<String> getResultApks() {
            return resultApks;
        }

        @Override
        public void handleOutput(@NonNull ProcessOutput processOutput) throws ProcessException {
            if (processOutput instanceof BaseProcessOutput) {
                BaseProcessOutput impl = (BaseProcessOutput) processOutput;
                String stdout = impl.getStandardOutputAsString();
                if (!stdout.isEmpty()) {
                    mLogger.info(stdout);
                    resultApks.addAll(stdout.readLines());
                }
                String stderr = impl.getErrorOutputAsString();
                if (!stderr.isEmpty()) {
                    mLogger.error(stderr);
                    resultApks.addAll(stderr.readLines());
                }
            } else {
                throw new IllegalArgumentException(
                        "processOutput was not created by this handler.");
            }
        }
    }
}
