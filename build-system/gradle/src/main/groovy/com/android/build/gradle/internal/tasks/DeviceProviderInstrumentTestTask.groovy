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
package com.android.build.gradle.internal.tasks
import com.android.build.gradle.internal.test.TestDataImpl
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.testing.SimpleTestRunner
import com.android.builder.testing.TestRunner
import com.android.builder.testing.api.DeviceProvider
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
/**
 * Run instrumentation tests for a given variant
 */
public class DeviceProviderInstrumentTestTask extends BaseTask implements AndroidTestTask {

    File reportsDir
    File resultsDir
    File coverageDir

    String flavorName

    DeviceProvider deviceProvider
    TestVariantData testVariantData

    File[] splitApks;
    File adbExec;

    boolean ignoreFailures
    boolean testFailed

    @TaskAction
    protected void runTests() {

        File resultsOutDir = getResultsDir()
        emptyFolder(resultsOutDir)

        File coverageOutDir = getCoverageDir()
        emptyFolder(coverageOutDir)

        if (testVariantData.outputs.size() > 1) {
            throw new RuntimeException("Multi-output in test variant not yet supported")
        }
        File testApk = testVariantData.outputs.get(0).getOutputFile()

        String flavor = getFlavorName()

        TestRunner testRunner = new SimpleTestRunner(getAdbExec());
        deviceProvider.init();

        boolean success = false;
        try {
            success = testRunner.runTests(project.name, flavor,
                    testApk, new TestDataImpl(testVariantData),
                    deviceProvider.devices,
                    deviceProvider.getMaxThreads(),
                    deviceProvider.getTimeout(),
                    resultsOutDir, coverageOutDir, plugin.logger);
        } finally {
            deviceProvider.terminate();
        }

        // run the report from the results.
        File reportOutDir = getReportsDir()
        emptyFolder(reportOutDir)

        TestReport report = new TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir)
        report.generateReport()

        if (!success) {
            testFailed = true
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(
                    new File(reportOutDir, "index.html"));
            String message = "There were failing tests. See the report at: " + reportUrl;
            if (getIgnoreFailures()) {
                getLogger().warn(message)

                return
            } else {
                throw new GradleException(message)
            }
        }

        testFailed = false
    }
}
