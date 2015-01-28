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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.google.common.collect.Lists
import com.google.common.io.Files
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
/**
 * Task doing test report aggregation.
 */
class AndroidReportTask extends BaseTask implements AndroidTestTask {

    private final List<AndroidTestTask> subTasks = Lists.newArrayList()

    ReportType reportType

    boolean ignoreFailures
    boolean testFailed

    @OutputDirectory
    File reportsDir

    @OutputDirectory
    File resultsDir

    public void addTask(AndroidTestTask task) {
        subTasks.add(task)
        dependsOn task
    }

    @InputFiles
    List<File> getResultInputs() {
        List<File> list = Lists.newArrayList()

        for (AndroidTestTask task : subTasks) {
            list.add(task.getResultsDir())
        }

        return list
    }

    /**
     * Sets that this current task will run and therefore needs to tell its children
     * class to not stop on failures.
     */
    public void setWillRun() {
        for (AndroidTestTask task : subTasks) {
            task.ignoreFailures = true
        }
    }

    @TaskAction
    protected void createReport() {
        File resultsOutDir = getResultsDir()
        File reportOutDir = getReportsDir()

        // empty the folders
        emptyFolder(resultsOutDir)
        emptyFolder(reportOutDir)

        // do the copy.
        copyResults(resultsOutDir)

        // create the report.
        TestReport report = new TestReport(reportType, resultsOutDir, reportOutDir)
        report.generateReport()

        // fail if any of the tasks failed.
        for (AndroidTestTask task : subTasks) {
            if (task.testFailed) {

                String reportUrl = new ConsoleRenderer().asClickableFileUrl(
                        new File(reportOutDir, "index.html"))
                String message = "There were failing tests. See the report at: " + reportUrl

                if (getIgnoreFailures()) {
                    getLogger().warn(message)
                } else {
                    throw new GradleException(message)
                }

                break
            }
        }
    }

    private void copyResults(File reportOutDir) {
        List<File> inputs = getResultInputs()

        for (File input : inputs) {
            File[] children = input.listFiles()
            if (children != null) {
                for (File child : children) {
                    copyFile(child, reportOutDir)
                }
            }
        }
    }

    private void copyFile(File from, File to) {
        to = new File(to, from.getName())
        if (from.isDirectory()) {
            if (!to.exists()) {
                to.mkdirs()
            }

            File[] children = from.listFiles()
            if (children != null) {
                for (File child : children) {
                    copyFile(child, to)
                }
            }
        } else if (from.isFile()) {
            Files.copy(from, to)
        }
    }
}
