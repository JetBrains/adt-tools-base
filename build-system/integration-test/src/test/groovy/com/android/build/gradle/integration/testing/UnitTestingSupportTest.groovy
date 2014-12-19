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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import org.junit.ClassRule
import org.junit.Test

/**
 * Meta-level tests for the app-level unit testing support.
 */
class UnitTestingSupportTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("unitTesting")
            .create()

    @Test
    void testTask() {
        project.execute("test")

        def testSuite = new XmlParser().parse(
                project.file("build/test-results/TEST-com.android.tests.UnitTest.xml"))
        def getTest = { name -> testSuite.testcase.find { it.@name == name } }

        def ignored = ["thisIsIgnored"]
        def passed = [ "referenceProductionCode", "mockFinalClass", "mockFinalMethod" ]

        // TODO: Migrate to Truth.
        assert testSuite.testcase.@name as Set == (ignored + passed) as Set
        assert getTest(ignored.first()).skipped
        passed.each { name -> assert getTest(name).children().isEmpty() }
    }
}
