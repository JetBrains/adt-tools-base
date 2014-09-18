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

package com.android.builder.internal.testing;

import static com.android.sdklib.BuildToolInfo.PathId.ZIP_ALIGN;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.TestData;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.utils.ILogger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Basic Callable to run tests on a given {@link DeviceConnector} using
 * {@link RemoteAndroidTestRunner}.
 */
public class SimpleTestCallable implements Callable<Boolean> {

    public static final String FILE_COVERAGE_EC = "coverage.ec";

    @NonNull
    private final String projectName;
    @NonNull
    private final DeviceConnector device;
    @NonNull
    private final String flavorName;
    @NonNull
    private final TestData testData;
    @NonNull
    private final File resultsDir;
    @NonNull
    private final File coverageDir;
    @NonNull
    private final File testApk;
    @Nullable
    private final File testedApk;
    @Nullable
    private final File[] splitApks;
    @NonNull
    private final File adbExec;

    private final int timeout;
    @NonNull
    private final ILogger logger;

    public SimpleTestCallable(
            @NonNull  DeviceConnector device,
            @NonNull  String projectName,
            @NonNull  String flavorName,
            @NonNull  File testApk,
            @Nullable File testedApk,
            @Nullable File[] splitApks,
            @NonNull  File adbExec,
            @NonNull  TestData testData,
            @NonNull  File resultsDir,
            @NonNull  File coverageDir,
                      int timeout,
            @NonNull  ILogger logger) {
        this.projectName = projectName;
        this.device = device;
        this.flavorName = flavorName;
        this.resultsDir = resultsDir;
        this.coverageDir = coverageDir;
        this.testApk = testApk;
        this.testedApk = testedApk;
        this.testData = testData;
        this.splitApks = splitApks;
        this.adbExec = adbExec;
        this.timeout = timeout;
        this.logger = logger;
    }

    @Override
    public Boolean call() throws Exception {
        String deviceName = device.getName();
        boolean isInstalled = false;

        CustomTestRunListener runListener = new CustomTestRunListener(
                deviceName, projectName, flavorName, logger);
        runListener.setReportDir(resultsDir);

        long time = System.currentTimeMillis();
        boolean success = false;

        String coverageFile = "/data/data/" + testData.getTestedApplicationId() + "/" + FILE_COVERAGE_EC;

        try {
            device.connect(timeout, logger);

            if (testedApk != null) {
                logger.verbose("DeviceConnector '%s': installing %s", deviceName, testedApk);
                if (splitApks != null) {
                    List<String> args = new ArrayList<String>();
                    args.add(adbExec.getAbsolutePath());
                    args.add("install-multiple");
                    args.add("-r");
                    args.add(testedApk.getAbsolutePath());
                    // for now, do a simple java exec adb
                    for (File split : splitApks) {
                        args.add(split.getAbsolutePath());
                    }
                    ProcessBuilder processBuilder = new ProcessBuilder(args);
                    Process process = processBuilder.start();
                    //Read out dir output
                    InputStream is = process.getErrorStream();
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);
                    String line;
                    while ((line = br.readLine()) != null) {
                        logger.verbose("adb output is :" + line);
                    }

                    //Wait to get exit value
                    try {
                        int exitValue = process.waitFor();
                        logger.verbose("\n\nExit Value is " + exitValue);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } else {
                    device.installPackage(testedApk, timeout, logger);
                }
            }

            logger.verbose("DeviceConnector '%s': installing %s", deviceName, testApk);
            device.installPackage(testApk, timeout, logger);
            isInstalled = true;

            RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                    testData.getApplicationId(),
                    testData.getInstrumentationRunner(),
                    device);

            if (testData.isTestCoverageEnabled()) {
                runner.addInstrumentationArg("coverage", "true");
                runner.addInstrumentationArg("coverageFile", coverageFile);
            }

            runner.setRunName(deviceName);
            runner.setMaxtimeToOutputResponse(timeout);

            runner.run(runListener);

            boolean result = runListener.getRunResult().hasFailedTests();
            success = true;
            return result;
        } catch (Exception e) {
            Map<String, String> emptyMetrics = Collections.emptyMap();

            // create a fake test output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(baos, true);
            e.printStackTrace(pw);
            TestIdentifier fakeTest = new TestIdentifier(device.getClass().getName(), "runTests");
            runListener.testStarted(fakeTest);
            runListener.testFailed(ITestRunListener.TestFailure.ERROR, fakeTest , baos.toString());
            runListener.testEnded(fakeTest, emptyMetrics);

            // end the run to generate the XML file.
            runListener.testRunEnded(System.currentTimeMillis() - time, emptyMetrics);

            // and throw
            throw e;
        } finally {
            if (isInstalled) {
                // Get the coverage if needed.
                if (success && testData.isTestCoverageEnabled()) {
                    device.executeShellCommand(
                            "run-as " + testData.getTestedApplicationId() + " chmod 644 " + coverageFile,
                            new NullOutputReceiver(),
                            30, TimeUnit.SECONDS);
                    device.pullFile(
                            coverageFile,
                            new File(coverageDir, FILE_COVERAGE_EC).getPath());
                }

                // uninstall the apps
                // This should really not be null, because if it was the build
                // would have broken before.
                uninstall(testApk, testData.getApplicationId(), deviceName);

                if (testedApk != null) {
                   uninstall(testedApk, testData.getTestedApplicationId(), deviceName);
                }
            }

            device.disconnect(timeout, logger);
        }
    }

    private void uninstall(@NonNull File apkFile, @Nullable String packageName,
                           @NonNull String deviceName)
            throws DeviceException {
        if (packageName != null) {
            logger.verbose("DeviceConnector '%s': uninstalling %s", deviceName, packageName);
            device.uninstallPackage(packageName, timeout, logger);
        } else {
            logger.verbose("DeviceConnector '%s': unable to uninstall %s: unable to get package name",
                    deviceName, apkFile);
        }
    }
}
