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

import com.android.build.SplitOutput
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.VariantConfiguration
import com.android.builder.internal.InstallUtils
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.ddmlib.IDevice
import com.android.ide.common.build.SplitOutputMatcher
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
public class InstallVariantTask extends BaseTask {
    @InputFile
    File adbExe

    int timeOut = 0

    BaseVariantData<? extends BaseVariantOutputData> variantData

    @TaskAction
    void install() {
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe())
        deviceProvider.init();

        VariantConfiguration variantConfig = variantData.variantConfiguration
        String variantName = variantConfig.fullName
        String projectName = plugin.project.name

        String serial = System.getenv("ANDROID_SERIAL");

        int foundDevice = 0;
        for (DeviceConnector device : deviceProvider.getDevices()) {
            if (serial != null && !serial.equals(device.getSerialNumber())) {
                continue;
            }

            if (device.getState() != IDevice.DeviceState.UNAUTHORIZED) {
                if (InstallUtils.checkDeviceApiLevel(
                        device, variantConfig.minSdkVersion, plugin.logger, projectName,
                        variantName)) {

                    // now look for a matching output file
                    SplitOutput output = SplitOutputMatcher.computeBestOutput(
                            variantData.outputs,
                            variantData.variantConfiguration.getSupportedAbis(),
                            device.getDensity(), device.getAbis())

                    if (output == null) {
                        System.out.println(
                                "Skipping device '${device.getName()}' for '${projectName}:${variantName}': No matching output file.");
                    } else {
                        System.out.println(
                                "Installing '${output.baseName}' on '${device.getName()}'.");
                        File apkFile = output.getOutputFile();
                        device.installPackage(apkFile, getTimeOut(), plugin.logger)
                        foundDevice++
                    }
                }
            }
        }

        if (foundDevice == 0) {
            System.out.println("Found no authorized devices")
        }
    }
}
