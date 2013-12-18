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

    File testApp
    File testedApp

    File reportsDir
    File resultsDir

    String flavorName

    DeviceProvider deviceProvider

    boolean ignoreFailures
    boolean testFailed

    @TaskAction
    protected void runTests() {
        assert variant instanceof TestVariantData

        File resultsOutDir = getResultsDir()

        // empty the folder.
        emptyFolder(resultsOutDir)

        File testApk = getTestApp()
        File testedApk = getTestedApp()

        String flavor = getFlavorName()

        TestRunner testRunner = new SimpleTestRunner();
        deviceProvider.init();

        boolean success = false;
        try {
            success = testRunner.runTests(project.name, flavor,
                    testApk, testedApk, variant.variantConfiguration,
                    deviceProvider.devices,
                    deviceProvider.getMaxThreads(),
                    deviceProvider.getTimeout(),
                    resultsOutDir, plugin.logger);
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
