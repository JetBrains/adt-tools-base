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

package com.android.build.gradle.integration.testing
/**
 * Helper class for inspecting JUnit XML files.
 */
class JUnitResults {
    enum Outcome {
        PASSED, FAILED, SKIPPED
    }

    private final testSuite

    JUnitResults(File xmlFile) {
        testSuite = new XmlParser().parse(xmlFile)
    }

    def find(String name) {
        testSuite.testcase.find { it.@name == name }
    }

    def getAllTestCases() {
        return testSuite.testcase.@name as Set
    }

    def outcome(String name) {
        def testCase = find(name)
        if (testCase.children().isEmpty()) {
            Outcome.PASSED
        } else if (testCase.skipped) {
            Outcome.SKIPPED
        } else {
            Outcome.FAILED
        }
    }

    String getStdErr() {
        return testSuite.'system-err'.text()
    }

    String getStdOut() {
        return testSuite.'system-out'.text()
    }
}
