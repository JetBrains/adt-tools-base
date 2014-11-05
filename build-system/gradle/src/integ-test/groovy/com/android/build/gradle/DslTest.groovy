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



package com.android.build.gradle

import com.android.build.gradle.internal.test.fixture.GradleProjectTestRule
import com.android.build.gradle.internal.test.fixture.app.HelloWorldApp
import groovy.util.slurpersupport.GPathResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class DslTest {

    @Rule
    public GradleProjectTestRule fixture = new GradleProjectTestRule();

    @Before
    public void setup() {
        new HelloWorldApp().writeSources(fixture.getSourceDir())
        fixture.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleProjectTestRule.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleProjectTestRule.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void versionNameSuffix() {
        fixture.getBuildFile() << """
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
        fixture.execute("processDebugManifest")

        File manifestFile = fixture.file(
                "build/intermediates/manifests/full/debug/AndroidManifest.xml")

        GPathResult xml = new XmlSlurper().parse(manifestFile).declareNamespace(
                android: 'http://schemas.android.com/apk/res/android')

        String versionName = xml.'@android:versionName'.text()

        assertNotNull(versionName)
        assertEquals("foo-suffix", versionName);
    }
}
