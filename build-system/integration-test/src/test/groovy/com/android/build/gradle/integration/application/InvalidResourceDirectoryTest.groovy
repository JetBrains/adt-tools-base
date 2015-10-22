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
import com.google.common.base.Throwables
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildException
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

@CompileStatic
class InvalidResourceDirectoryTest {

    public static AndroidTestApp app = HelloWorldApp.noBuildFile()

    static {
        app.addFile(new TestSourceFile(INVALID_LAYOUT_FOLDER, "main.xml",
                """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="fill_parent"
    android:layout_height="fill_parent"
    >
<TextView
    android:layout_width="fill_parent"
    android:layout_height="wrap_content"
    android:text="hello invalid layout world!"
    android:id="@+id/text"
    />
</LinearLayout>
"""));
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().fromTestApp(app).create()

    public static final String INVALID_LAYOUT_FOLDER = "src/main/res/layout-hdpi-land"


    @BeforeClass
    public static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void "check build failure on invalid resource directory"() {
        try {
            project.execute("assembleRelease");
        } catch (BuildException e) {
            Throwable rootCause = Throwables.getRootCause(e);
            assert rootCause.message.contains(
                    new File(project.testDir, INVALID_LAYOUT_FOLDER).absolutePath)
        }
    }
}
