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
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.builder.model.SyncIssue
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * General Model tests
 */
@CompileStatic
class AaptOptionsTest {

    public static final AndroidTestApp helloWorldApp = new HelloWorldApp();
    static {
        helloWorldApp.addFile(new TestSourceFile("src/main/res/raw", "data", "test"));
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
        .fromTestApp(helloWorldApp)
        .create()

    @Before
    public void setUp() {

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

}
"""
    }

    @Test
    public void "test aaptOptions flags"() {
        project.getBuildFile() << """
android {
    aaptOptions {
        additionalParameters "--ignore-assets", "!data"
    }
}
"""
        project.execute("assembleDebug")
        assertThatZip(project.getApk("debug")).doesNotContain("res/raw/data")
    }
}
