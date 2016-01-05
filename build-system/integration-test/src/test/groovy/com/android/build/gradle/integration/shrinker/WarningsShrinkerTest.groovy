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

package com.android.build.gradle.integration.shrinker
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import groovy.transform.CompileStatic
import org.gradle.tooling.GradleConnectionException
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.utils.GradleExceptionsHelper.getTaskFailureMessage
/**
 * Tests for -dontwarn handling
 */
@CompileStatic
class WarningsShrinkerTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .captureStdOut(true)
            .create()

    private File rules
    private File activity
    private int changeCounter

    @Before
    public void enableShrinking() throws Exception {
        rules = project.file('proguard-rules.pro')

        project.buildFile << """
            android {
                buildTypes.debug {
                    minifyEnabled true
                    useProguard false
                    proguardFiles getDefaultProguardFile('proguard-android.txt'), '${rules.name}'
                }
            }
        """

        rules << """
            # Empty rules for now.
        """
    }

    @Before
    public void addGuavaDep() throws Exception {
        project.buildFile << """
            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """
    }

    @Before
    public void prepareForChanges() throws Exception {
        activity = project.file("src/main/java/com/example/helloworld/HelloWorld.java")

        TestFileUtils.addMethod(
                activity,
                """
                    @Override
                    protected void onStop() {
                        android.util.Log.i("MainActivity", "CHANGE0");
                        super.onStop();
                    }
                """)
    }

    @Test
    public void "Warnings stop build"() throws Exception {
        project.executeExpectingFailure("assembleDebug")

        String output = project.stdout.toString()
        assertThat(output).contains("references unknown")
        assertThat(output).contains("Unsafe")
        assertThat(output).contains("Nullable")
        assertThat(output).contains("com/google/common/cache")

        changeCode()

        project.stdout.reset()
        project.executeExpectingFailure("assembleDebug")

        output = project.stdout.toString()
        assertThat(output).contains("references unknown")
        assertThat(output).contains("Unsafe")
        assertThat(output).contains("Nullable")
        assertThat(output).contains("com/google/common/cache")
    }

    @Test
    public void "-dontwarn applies only to relevant classes"() throws Exception {
        rules << """
            -dontwarn sun.misc.Unsafe
        """

        project.executeExpectingFailure("assembleDebug")

        String output = project.stdout.toString()
        assertThat(output).contains("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).contains("Nullable")
        assertThat(output).contains("com/google/common/cache")
    }

    @Test
    public void "-dontwarn without arguments"() throws Exception {
        rules << "-dontwarn"
        project.execute("assembleDebug")

        String output = project.stdout.toString()
        assertThat(output).doesNotContain("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).doesNotContain("Nullable")
        assertThat(output).doesNotContain("com/google/common/cache")

        changeCode()

        project.stdout.reset()
        project.execute("assembleDebug")

        output = project.stdout.toString()
        assertThat(output).doesNotContain("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).doesNotContain("Nullable")
        assertThat(output).doesNotContain("com/google/common/cache")
    }

    @Test
    public void "-dontwarn on caller"() throws Exception {
        rules << "-dontwarn com.google.common.**"
        project.execute("assembleDebug")

        String output = project.stdout.toString()
        assertThat(output).doesNotContain("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).doesNotContain("Nullable")
        assertThat(output).doesNotContain("com/google/common/cache")

        changeCode()

        project.stdout.reset()
        project.execute("assembleDebug")

        output = project.stdout.toString()
        assertThat(output).doesNotContain("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).doesNotContain("Nullable")
        assertThat(output).doesNotContain("com/google/common/cache")
    }

    @Test
    public void "-dontwarn on callee"() throws Exception {
        rules << """
            -dontwarn sun.misc.Unsafe
            -dontwarn javax.annotation.**
        """

        project.execute("assembleDebug")

        String output = project.stdout.toString()
        assertThat(output).doesNotContain("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).doesNotContain("Nullable")
        assertThat(output).doesNotContain("com/google/common/cache")

        changeCode()

        project.stdout.reset()
        project.execute("assembleDebug")

        output = project.stdout.toString()
        assertThat(output).doesNotContain("references unknown")
        assertThat(output).doesNotContain("Unsafe")
        assertThat(output).doesNotContain("Nullable")
        assertThat(output).doesNotContain("com/google/common/cache")
    }

    @Test
    public void "Parser errors are properly reported"() throws Exception {
        rules << "-foo"

        GradleConnectionException failure = project.executeExpectingFailure("assembleDebug")
        assertThat(getTaskFailureMessage(failure)).contains("'-foo' expecting EOF")
    }

    private void changeCode() {
        TestFileUtils.searchAndReplace(activity, "CHANGE\\d+", "CHANGE${++changeCounter}")
    }
}
