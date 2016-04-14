/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * Tests for PNG generation in case of libraries.
 */
@CompileStatic
class VectorDrawableTest_Library {

    public static final String VECTOR_XML = """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:height="256dp"
            android:width="256dp"
            android:viewportWidth="32"
            android:viewportHeight="32">

            <path
                android:fillColor="#ff0000"
                android:pathData="M20.5,9.5
                                c-1.965,0,-3.83,1.268,-4.5,3
                                c-0.17,-1.732,-2.547,-3,-4.5,-3
                                C8.957,9.5,7,11.432,7,14
                                c0,3.53,3.793,6.257,9,11.5
                                c5.207,-5.242,9,-7.97,9,-11.5
                                C25,11.432,23.043,9.5,20.5,9.5z" />
        </vector>
        """

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new MultiModuleTestProject([
                    ":app": HelloWorldApp.forPlugin("com.android.application"),
                    ":lib": new EmptyAndroidTestApp("com.example.lib")
            ]))
            .create()

    @Before
    public void checkBuildTools() {
        GradleTestProject.assumeBuildToolsAtLeast(21)
    }

    @Before
    public void setUpApp() {
        def app = project.getSubproject(":app")
        app.buildFile << "dependencies { compile project(':lib') }"
        Files.createParentDirs(app.file("src/main/res/drawable/app_vector.xml"))
        app.file("src/main/res/drawable/app_vector.xml") << VECTOR_XML
    }

    @Before
    public void setUpLib() {
        def lib = project.getSubproject(":lib")
        lib.buildFile << """
        apply plugin: "com.android.library"

        android {
            compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
            buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        }
        """

        Files.createParentDirs(lib.file("src/main/res/drawable/lib_vector.xml"))
        lib.file("src/main/res/drawable/lib_vector.xml") << VECTOR_XML
    }

    @Test
    public void "Lib uses support library, app does not"() throws Exception {
        project.getSubproject(":lib").buildFile << """
                android.defaultConfig.vectorDrawables {
                    useSupportLibrary = true
                }
        """

        project.execute(":app:assembleDebug")
        File apk = project.getSubproject(":app").getApk("debug")

        assertThatApk(apk).containsResource("drawable-anydpi-v21/app_vector.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/app_vector.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/app_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable/app_vector.xml")

        assertThatApk(apk).containsResource("drawable/lib_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v21/lib_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/lib_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/lib_vector.png")
    }

    @Test
    public void "App uses support library, lib does not"() throws Exception {
        project.getSubproject(":app").buildFile << """
                android.defaultConfig.vectorDrawables {
                    useSupportLibrary = true
                }
        """

        project.execute(":app:assembleDebug")
        File apk = project.getSubproject(":app").getApk("debug")

        assertThatApk(apk).containsResource("drawable-anydpi-v21/lib_vector.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/lib_vector.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/lib_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable/lib_vector.xml")

        assertThatApk(apk).containsResource("drawable/app_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v21/app_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/app_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/app_vector.png")
    }

    @Test
    public void "Both use support library"() throws Exception {
        project.getSubproject(":app").buildFile << """
                android.defaultConfig.vectorDrawables {
                    useSupportLibrary = true
                }
        """

        project.getSubproject(":lib").buildFile << """
                android.defaultConfig.vectorDrawables {
                    useSupportLibrary = true
                }
        """

        project.execute(":app:assembleDebug")
        File apk = project.getSubproject(":app").getApk("debug")

        assertThatApk(apk).containsResource("drawable/app_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v21/app_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/app_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/app_vector.png")

        assertThatApk(apk).containsResource("drawable/lib_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v21/lib_vector.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/lib_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/lib_vector.png")
    }

    @Test
    public void "None use support library"() throws Exception {
        project.execute(":app:assembleDebug")
        File apk = project.getSubproject(":app").getApk("debug")

        assertThatApk(apk).containsResource("drawable-anydpi-v21/app_vector.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/app_vector.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/app_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable/app_vector.xml")

        assertThatApk(apk).containsResource("drawable-anydpi-v21/lib_vector.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/lib_vector.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/lib_vector.png")
        assertThatApk(apk).doesNotContainResource("drawable/lib_vector.xml")
    }
}
