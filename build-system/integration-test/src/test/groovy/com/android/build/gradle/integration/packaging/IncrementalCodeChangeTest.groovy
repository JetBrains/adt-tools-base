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
import com.android.build.gradle.integration.common.fixture.Packaging
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

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
@RunWith(FilterableParameterized)
class IncrementalCodeChangeTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()

    @Test
    void "check non-multi-dex"() {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);
        project.getSubproject('app').getBuildFile() << """

dependencies {
    compile project(':library')
}
"""
        project.executor().withPackaging(mPackaging).run("clean", ":app:assembleDebug")

        TestFileUtils.replaceLine(
                project.file("library/src/main/java/com/example/android/multiproject/library/PersonView.java"),
                9,
                "        setTextSize(30);")

        project.executor().withPackaging(mPackaging).run(":app:assembleDebug")

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
        project.executor().withPackaging(mPackaging).run("clean", ":app:assembleDebug")

        TestFileUtils.replaceLine(
                project.file("library/src/main/java/com/example/android/multiproject/library/PersonView.java"),
                9,
                "        setTextSize(30);")

        project.executor().withPackaging(mPackaging).run(":app:assembleDebug")

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
        project.executor().withPackaging(mPackaging).run("clean", ":app:assembleDebug")

        TestFileUtils.replaceLine(
                project.file("library/src/main/java/com/example/android/multiproject/library/PersonView.java"),
                9,
                "        setTextSize(30);")

        project.executor().withPackaging(mPackaging).run(":app:assembleDebug")

        // class from :library
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;")

        // class from :app
        assertThatApk(project.getSubproject('app').getApk("debug"))
                .containsClass("Lcom/example/android/multiproject/MainActivity;")
    }

}
