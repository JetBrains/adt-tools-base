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



package com.android.test.application

import com.android.test.common.fixture.GradleTestProject
import com.android.test.common.fixture.app.HelloWorldApp
import groovy.util.slurpersupport.GPathResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class DslTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().create();

    @Before
    public void setup() {
        new HelloWorldApp().writeSources(project.getSourceDir())
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void versionNameSuffix() {
        project.getBuildFile() << """
android {
    defaultConfig {
        versionName 'foo'
    }

    buildTypes {
        debug {
            versionNameSuffix '-suffix'
        }
    }
}
"""
        // no need to do a full build. Let's just run the manifest task.
        project.execute("processDebugManifest")

        File manifestFile = project.file(
                "build/intermediates/manifests/full/debug/AndroidManifest.xml")

        GPathResult xml = new XmlSlurper().parse(manifestFile).declareNamespace(
                android: 'http://schemas.android.com/apk/res/android')

        String versionName = xml.'@android:versionName'.text()

        assertNotNull(versionName)
        assertEquals("foo-suffix", versionName);
    }
}
