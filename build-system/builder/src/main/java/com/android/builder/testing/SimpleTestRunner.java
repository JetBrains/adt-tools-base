/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.builder.internal.InstallUtils;
import com.android.builder.internal.testing.SimpleTestCallable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.TestException;
import com.android.ddmlib.IDevice;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.ILogger;

import java.io.File;
import java.util.List;

/**
 * Basic {@link TestRunner} running tests on all devices.
 */
public class SimpleTestRunner implements TestRunner {

    File mAdbExec;

    public SimpleTestRunner(File adbExec) {
        mAdbExec = adbExec;
    }


    @Override
    public boolean runTests(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull File testApk,
            @NonNull TestData testData,
            @NonNull List<? extends DeviceConnector> deviceList,
                     int maxThreads,
                     int timeout,
            @NonNull File resultsDir,
            @NonNull File coverageDir,
            @NonNull ILogger logger) throws TestException, NoAuthorizedDeviceFoundException, InterruptedException {

        WaitableExecutor<Boolean> executor = new WaitableExecutor<Boolean>(maxThreads);

        boolean foundAtLeastOneAuthorizedDevice = false;
        for (DeviceConnector device : deviceList) {
            if (device.getState() != IDevice.DeviceState.UNAUTHORIZED) {
                foundAtLeastOneAuthorizedDevice = true;
                if (InstallUtils.checkDeviceApiLevel(
                        device, testData.getMinSdkVersion(), logger, projectName, variantName)) {

                    // now look for a matching output file
                    File testedApk = null;
                    if (!testData.isLibrary()) {
                        testedApk = testData.getTestedApk(device.getDensity(), device.getAbis());

                        if (testedApk == null) {
                            logger.info("Skipping device '%1$s' for '%2$s:%3$s': No matching output file",
                                    device.getName(), projectName, variantName);
                            continue;
                        }
                    }

                    executor.execute(new SimpleTestCallable(device, projectName, variantName,
                            testApk, testedApk, testData.getSplitApks(), mAdbExec, testData,
                            resultsDir, coverageDir, timeout, logger));
                }
            }
        }

        if (!foundAtLeastOneAuthorizedDevice) {
            throw new NoAuthorizedDeviceFoundException();
        }
        List<WaitableExecutor.TaskResult<Boolean>> results = executor.waitForAllTasks();

        boolean success = true;

        // check if one test failed or if there was an exception.
        for (WaitableExecutor.TaskResult<Boolean> result : results) {
            if (result.value != null) {
                success &= result.value;
            } else {
                success = false;
                logger.error(result.exception, null);
            }
        }

        return success;
    }

}
