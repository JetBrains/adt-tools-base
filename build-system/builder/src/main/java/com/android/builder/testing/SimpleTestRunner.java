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
import com.android.builder.internal.InstallUtils;
import com.android.builder.internal.testing.CustomTestRunListener;
import com.android.builder.internal.testing.ShardedTestCallable;
import com.android.builder.internal.testing.SimpleTestCallable;
import com.android.builder.testing.api.DeviceConfigProviderImpl;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.TestException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic {@link TestRunner} running tests on all devices.
 */
public class SimpleTestRunner implements TestRunner {

    @Nullable
    private final File mSplitSelectExec;
    @NonNull
    private final ProcessExecutor mProcessExecutor;

    private final boolean mEnableSharding;

    @Nullable
    private final Integer mNumShards;

    public SimpleTestRunner(@Nullable File splitSelectExec,
            @NonNull ProcessExecutor processExecutor,
            boolean enableSharding, @Nullable Integer numShards) {
        mSplitSelectExec = splitSelectExec;
        mProcessExecutor = processExecutor;
        mEnableSharding = enableSharding;
        mNumShards = numShards;
    }

    @Override
    public boolean runTests(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull File testApk,
            @NonNull TestData testData,
            @NonNull List<? extends DeviceConnector> deviceList,
                     int maxThreads,
                     int timeoutInMs,
            @NonNull Collection<String> installOptions,
            @NonNull File resultsDir,
            @NonNull File coverageDir,
            @NonNull ILogger logger) throws TestException, NoAuthorizedDeviceFoundException, InterruptedException {
        int threadPoolSize = maxThreads;
        if (mEnableSharding) {
            threadPoolSize = deviceList.size();
        }
        WaitableExecutor<Boolean> executor = WaitableExecutor.useNewFixedSizeThreadPool(threadPoolSize);

        int totalDevices = deviceList.size();
        int unauthorizedDevices = 0;
        final Map<DeviceConnector, ImmutableList<File>> availableDevices = new HashMap<DeviceConnector, ImmutableList<File>>();
        for (final DeviceConnector device : deviceList) {
            if (device.getState() != IDevice.DeviceState.UNAUTHORIZED) {
                if (InstallUtils.checkDeviceApiLevel(
                        device, testData.getMinSdkVersion(), logger, projectName, variantName)) {

                    final DeviceConfigProvider deviceConfigProvider;
                    try {
                        deviceConfigProvider = new DeviceConfigProviderImpl(device);
                    } catch (DeviceException e) {
                        throw new TestException(e);
                    }

                    // now look for a matching output file
                    ImmutableList<File> testedApks = ImmutableList.of();
                    if (!testData.isLibrary()) {
                        try {
                            testedApks = testData.getTestedApks(
                                    mProcessExecutor,
                                    mSplitSelectExec,
                                    deviceConfigProvider,
                                    logger);
                        } catch (ProcessException e) {
                            throw new TestException(e);
                        }

                        if (testedApks.isEmpty()) {
                            logger.info("Skipping device '%1$s' for '%2$s:%3$s': No matching output file",
                                    device.getName(), projectName, variantName);
                            continue;
                        }
                    }
                    availableDevices.put(device, testedApks);
                }
            } else {
                unauthorizedDevices++;
            }
        }

        if (totalDevices == 0 || availableDevices.isEmpty()) {
            CustomTestRunListener fakeRunListener = new CustomTestRunListener(
                    "TestRunner", projectName, variantName, logger);
            fakeRunListener.setReportDir(resultsDir);

            // create a fake test output
            Map<String, String> emptyMetrics = Collections.emptyMap();
            TestIdentifier fakeTest = new TestIdentifier(variantName,
                    totalDevices == 0 ? ": No devices connected." : ": No compatible devices connected.");
            fakeRunListener.testStarted(fakeTest);
            fakeRunListener.testFailed(
                    fakeTest,
                    String.format("Found %d connected device(s), %d of which were compatible.",
                            totalDevices, availableDevices.size()));
            fakeRunListener.testEnded(fakeTest, emptyMetrics);

            // end the run to generate the XML file.
            fakeRunListener.testRunEnded(0, emptyMetrics);

            return false;
        } else {
            if (unauthorizedDevices > 0) {
                CustomTestRunListener fakeRunListener = new CustomTestRunListener(
                        "TestRunner", projectName, variantName, logger);
                fakeRunListener.setReportDir(resultsDir);

                // create a fake test output
                Map<String, String> emptyMetrics = Collections.emptyMap();
                TestIdentifier fakeTest = new TestIdentifier(variantName, ": found unauthorized devices.");
                fakeRunListener.testStarted(fakeTest);
                fakeRunListener.testFailed(fakeTest , String.format("Found %d unauthorized device(s).", unauthorizedDevices));
                fakeRunListener.testEnded(fakeTest, emptyMetrics);

                // end the run to generate the XML file.
                fakeRunListener.testRunEnded(0, emptyMetrics);
            }

            if (mEnableSharding) {
                final int numShards;
                if (mNumShards == null) {
                    numShards = availableDevices.size();
                } else {
                    numShards = mNumShards;
                }
                final AtomicInteger currentShard = new AtomicInteger(-1);
                final ShardedTestCallable.ProgressListener progressListener
                        = new ShardedTestCallable.ProgressListener(numShards, logger);
                final ShardedTestCallable.ShardProvider shardProvider
                        = new ShardedTestCallable.ShardProvider() {
                    @Nullable
                    @Override
                    public Integer getNextShard() {
                        int shard = currentShard.incrementAndGet();
                        return shard < numShards ? shard : null;
                    }

                    @Override
                    public int getTotalShards() {
                        return numShards;
                    }
                };
                logger.info("will shard tests into %d shards", numShards);
                for (Map.Entry<DeviceConnector, ImmutableList<File>> runners : availableDevices
                        .entrySet()) {
                    ShardedTestCallable shardedTestCallable = new ShardedTestCallable(
                            runners.getKey(), projectName, variantName, testApk, runners.getValue(),
                            testData, resultsDir, coverageDir, timeoutInMs, logger, shardProvider);
                    shardedTestCallable.setProgressListener(progressListener);
                    executor.execute(shardedTestCallable);
                }
            } else {
                for (Map.Entry<DeviceConnector, ImmutableList<File>> runners : availableDevices
                        .entrySet()) {
                    SimpleTestCallable testCallable = new SimpleTestCallable(
                            runners.getKey(), projectName, variantName, testApk, runners.getValue(),
                            testData,
                            resultsDir, coverageDir, timeoutInMs, installOptions, logger);
                    executor.execute(testCallable);
                }
            }


            List<WaitableExecutor.TaskResult<Boolean>> results = executor.waitForAllTasks();

            boolean success = unauthorizedDevices == 0;

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
}
