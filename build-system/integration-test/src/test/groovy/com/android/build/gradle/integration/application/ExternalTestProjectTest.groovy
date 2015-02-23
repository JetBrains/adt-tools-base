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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildException
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail
/**
 * Check that a project can depend on a jar dependency published by another app project.
 */
@CompileStatic
class ExternalTestProjectTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().captureStdErr(true).create()

    private File app2BuildFile

    @Before
    public void setUp() {
        File rootFile = project.getTestDir()
        new File(rootFile, "settings.gradle") << """
include ':app1'
include ':app2'
"""
        // app1 module
        File app1 = new File(rootFile, "app1")
        new HelloWorldApp().write(app1, null)
        new File(app1, "build.gradle") << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

task testJar(type: Jar, dependsOn: 'assembleRelease') {

}

configurations {
    testLib
}

artifacts {
    testLib testJar
}

"""
        // app2 module
        File app2 = new File(rootFile, "app2")
        new HelloWorldApp().write(app2, null)
        app2BuildFile = new File(app2, "build.gradle")
    }

    @Test
    public void testExtraJarDependency() {
        app2BuildFile << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    compile project(path: ':app1', configuration: 'testLib')
}
"""

        project.execute('clean', 'app2:assembleDebug')
    }

    @Test
    void testApkDependencyInBuild() {
        app2BuildFile << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    compile project(path: ':app1')
}
"""
        try {
            project.execute('clean', 'app2:assembleDebug')
            fail('Broken build file did not throw exception')
        } catch (BuildException e) {
            Throwable t = e
            while (t.getCause() != null) {
                t = t.getCause()
            }

            // looks like we can't actually test the instance t against GradleException
            // due to it coming through the tooling API from a different class loader.
            assertEquals("org.gradle.api.GradleException", t.getClass().canonicalName)
            assertEquals("Dependency Error. See console for details", t.getMessage())
        }

        // check there is a version of the error, after the task name:
        ByteArrayOutputStream stderr = project.stderr
        String log = stderr.toString()

        assertTrue("stderr contains error", log.contains(
                "Dependency project:app1:unspecified on project app2 resolves to an APK archive which is not supported as a compilation dependency. File:"))

    }

    @Test
    void testApkDependencyInModel() {
        app2BuildFile << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    compile project(path: ':app1')
}
"""

        Map<String, AndroidProject> modelMap = project.getAllModels()

        AndroidProject model = modelMap.get(':app2')
        assertNotNull(model)

        SyncIssue issue = assertThat(model).issues().hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_DEPENDENCY_IS_APK,
                'project:app1:unspecified')

        String expectedMsg = "Dependency project:app1:unspecified on project app2 resolves to an APK archive which is not supported as a compilation dependency. File:"
        assertThat(issue.getMessage()).startsWith(expectedMsg)
    }
}
