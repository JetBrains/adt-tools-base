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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Tests for integration of the new class shrinker with Gradle.
 */
@CompileStatic
class ClassShrinkerTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Before
    public void enableShrinking() throws Exception {
        project.buildFile << """
            android {
                buildTypes.debug {
                    minifyEnabled true
                    useProguard false
                    proguardFiles getDefaultProguardFile('proguard-android.txt')
                }
            }
        """
    }

    @Test
    public void "APK is correct"() throws Exception {
        project.execute("assembleDebug")
        checkShrinkerWasUsed()
        assertThatApk(project.getApk("debug")).containsClass("Lcom/example/helloworld/HelloWorld;")
    }

    @Test
    @Category(DeviceTests)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck()
        checkShrinkerWasUsed()
    }

    private void checkShrinkerWasUsed() {
        // Sanity check, to make sure we're testing the right thing.
        assertThat(project.file("build/intermediates/transforms/newClassShrinker")).exists()
    }
}
