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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkManager
import com.android.utils.NullLogger
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test for BuildConfig field declared in build type, flavors, and variant and how they
 * override each other
 */
class OptionalLibraryTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldApp())
            .create()

    @After
    void cleanUp() {
        project = null
    }

    @Test
    void "test unknown useLibrary trigger sync issue"() {
        Assume.assumeNotNull("Next platform missing", System.getenv("ANDROID_NEXT_PLATFORM"));

        project.getBuildFile() << """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                useLibrary 'foo'

            }
            """.stripIndent()

        AndroidProject project = project.getSingleModelIgnoringSyncIssues()

        assertThat(project).issues().hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND,
                'foo');
    }

    @Test
    void "test using optional library"() {
        Assume.assumeNotNull("Next platform missing", System.getenv("ANDROID_NEXT_PLATFORM"));

        project.getBuildFile() << """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion 23
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                useLibrary 'org.apache.http.legacy'

            }
            """.stripIndent()

        AndroidProject project = project.getSingleModel()

        // get the SDK folder
        File sdkLocation = new File(System.getenv("ANDROID_HOME"))
        SdkManager sdkManager = SdkManager.createManager(
                sdkLocation.getAbsolutePath(),
                new NullLogger())
        IAndroidTarget target = sdkManager.getTargetFromHashString('android-23')

        File targetLocation = new File(target.getLocation())

        assertThat(project).bootClasspath().containsExactly(
                new File(targetLocation, FN_FRAMEWORK_LIBRARY).getAbsolutePath(),
                new File(targetLocation, "optional/org.apache.http.legacy.jar").getAbsolutePath())
    }

    @Test
    void "test not using optional library"() {
        Assume.assumeNotNull("Next platform missing", System.getenv("ANDROID_NEXT_PLATFORM"));

        project.getBuildFile() << """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion 23
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
            }
            """.stripIndent()

        AndroidProject project = project.getSingleModel()

        // get the SDK folder
        File sdkLocation = new File(System.getenv("ANDROID_HOME"))
        SdkManager sdkManager = SdkManager.createManager(
                sdkLocation.getAbsolutePath(),
                new NullLogger())
        IAndroidTarget target = sdkManager.getTargetFromHashString('android-23')

        File targetLocation = new File(target.getLocation())

        assertThat(project).bootClasspath().containsExactly(
                new File(targetLocation, FN_FRAMEWORK_LIBRARY).getAbsolutePath())
    }

}
