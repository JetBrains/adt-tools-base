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
import com.android.annotations.Nullable;
import com.android.builder.internal.testing.SimpleTestCallable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.TestException;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.ILogger;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Basic {@link TestRunner} running tests on all devices.
 */
public class SimpleTestRunner implements TestRunner {

    @Override
    public boolean runTests(
            @NonNull  String projectName,
            @NonNull  String variantName,
            @NonNull  File testApk,
            @Nullable File testedApk,
            @NonNull  TestData testData,
            @NonNull  List<? extends DeviceConnector> deviceList,
                      int maxThreads,
                      int timeout,
            @NonNull  File resultsDir,
            @NonNull  ILogger logger) throws TestException, InterruptedException {

        WaitableExecutor<Boolean> executor = new WaitableExecutor<Boolean>(maxThreads);

        for (DeviceConnector device : deviceList) {
            if (filterOutDevice(device, testData, logger, projectName, variantName)) {
                executor.execute(new SimpleTestCallable(device, projectName, variantName,
                        testApk, testedApk, testData,
                        resultsDir, timeout, logger));
            }
        }

        List<WaitableExecutor.TaskResult<Boolean>> results = executor.waitForAllTasks();

        boolean success = true;

        // check if one test failed or if there was an exception.
        for (WaitableExecutor.TaskResult<Boolean> result : results) {
            if (result.value != null) {
                // true means there are failed tests!
                success &= !result.value;
            } else {
                success = false;
                logger.error(result.exception, null);
            }
        }

        return success;
    }

    private boolean filterOutDevice(@NonNull DeviceConnector device, @NonNull TestData testData,
                                    @NonNull ILogger logger,
                                    @NonNull String projectName, @NonNull String variantName) {
        int deviceApiLevel = device.getApiLevel();
        if (deviceApiLevel == 0) {
            logger.info("Skipping device '%s' for '%s:%s': Unknown API Level",
                    device.getName(), projectName, variantName);
            return false;
        }

        if (testData.getMinSdkVersion() > deviceApiLevel) {
            logger.info("Skipping device '%s' for '%s:%s'",
                    device.getName(), projectName, variantName);

            return false;
        }

        Set<String> appAbis = testData.getSupportedAbis();
        if (appAbis != null) {
            List<String> deviceAbis = device.getAbis();
            if (deviceAbis == null || deviceAbis.isEmpty()) {
                logger.info("Skipping device '%s' for '%s:%s': Unknown ABI",
                        device.getName(), projectName, variantName);
                return false;
            }

            boolean compatibleAbi = false;
            for (String deviceAbi : deviceAbis) {
                if (appAbis.contains(deviceAbi)) {
                    compatibleAbi = true;
                }
            }

            if (!compatibleAbi) {
                return false;
            }
        }

        return true;
    }
}
