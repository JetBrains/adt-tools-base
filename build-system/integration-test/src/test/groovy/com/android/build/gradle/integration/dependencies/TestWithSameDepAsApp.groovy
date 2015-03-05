/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Tests the handling of test dependency.
 */
class TestWithSameDepAsApp {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("testDependency")
            .create()

    @BeforeClass
    public static void setUp() {
        project.getBuildFile() << """
dependencies {
    androidTestCompile 'com.google.guava:guava:17.0'
}
"""

        project.execute("clean", "assembleDebugAndroidTest")
    }

    @AfterClass
    public static void cleanUp() {
        project = null
    }

    @Test
    public void "Test with same dep version than Tested does NOT embed dependency"() {
        assertThatApk(project.getApk("debug", "androidTest", "unaligned"))
                .doesNotContainClass("Lcom/google/common/io/Files;")
    }

    @Test
    @Category(DeviceTests.class)
    void "run tests on devices"() {
        project.execute("connectedCheck")
    }
}
