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

package com.android.build.gradle.integration.packaging
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * test for incremental code change.
 *
 * It's a simple two project setup, with an app and a library. Only the library
 * gets changed and after the compilation of the app, we check code from both app and library
 * is present in the dex file of the app.
 *
 * 3 cases:
 * - no multi-dex
 * - native multi-dex
 * - legacy multi-dex
 */
@CompileStatic
class IncrementalCodeChangeTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    @Before
    void setUp() {
    }

    @After
    void cleanUp() {
        project = null
    }

    @Test
    void "check non-multi-dex"() {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);
        project.getSubproject('app').getBuildFile() << """

dependencies {
    compile project(':library')
}
"""
        project.execute("clean", ":app:assembleDebug")

        project.replaceLine(
                "library/src/main/java/com/example/android/multiproject/library/PersonView.java",
                9,
                "        setTextSize(30);")

        project.execute(":app:assembleDebug")

        // class from :library
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;")

        // class from :app
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;")
    }

    @Test
    void "check legacy multi-dex"() {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);
        project.getSubproject('app').getBuildFile() << """

android {
    defaultConfig {
        multiDexEnabled = true
    }
}
dependencies {
    compile project(':library')
}
"""
        project.execute("clean", ":app:assembleDebug")

        project.replaceLine(
                "library/src/main/java/com/example/android/multiproject/library/PersonView.java",
                9,
                "        setTextSize(30);")

        project.execute(":app:assembleDebug")

        // class from :library
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;")

        // class from :app
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;")

        // class from legacy multi-dex lib
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Landroid/support/multidex/MultiDex;")
    }

    @Test
    void "check native multi-dex"() {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);
        project.getSubproject('app').getBuildFile() << """

android {
    defaultConfig {
        minSdkVersion 21
        multiDexEnabled = true
    }
}
dependencies {
    compile project(':library')
}
"""
        project.execute("clean", ":app:assembleDebug")

        project.replaceLine(
                "library/src/main/java/com/example/android/multiproject/library/PersonView.java",
                9,
                "        setTextSize(30);")

        project.execute(":app:assembleDebug")

        // class from :library
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;")

        // class from :app
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;")
    }

}
