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
import groovy.util.slurpersupport.GPathResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * General DSL tests
 */
class DslTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().create();

    @Before
    public void setUp() {
        new HelloWorldApp().writeSources(project.testDir)
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



    @Test
    public void extraPropTest() {
        project.getBuildFile() << """
android {
    buildTypes {
        debug {
            ext.foo = "bar"
        }
    }

    applicationVariants.all { variant ->
        if (variant.buildType.name == "debug") {
            def foo = variant.buildType.foo
            if (!foo.equals("bar")) {
                throw new RuntimeException("direct access to dynamic property failed, got " + foo)
            }
            def hasProperty = variant.buildType.hasProperty("foo")
            if (!hasProperty) {
                throw new RuntimeException("hasProperty not returning property value, got " + hasProperty)
            }
        }
    }
}
"""
        // no need to do a full build. Let's just run the tasks.
        project.execute("tasks")

    }

    @Test
    public void buildConfigEncoding() {
        project.getBuildFile() << """
android {
  defaultConfig {
    buildConfigField 'String', 'test2', '"\\u0105"'
  }
}
"""

        project.execute("generateDebugBuildConfig")

        String expected =
"""/**
 * Automatically generated file. DO NOT MODIFY
 */
package com.example.helloworld;

public final class BuildConfig {
  public static final boolean DEBUG = Boolean.parseBoolean("true");
  public static final String APPLICATION_ID = "com.example.helloworld";
  public static final String BUILD_TYPE = "debug";
  public static final String FLAVOR = "";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "";
  // Fields from default config.
  public static final String test2 = "ą";
}
"""
        BuildConfigTest.checkBuildConfig(project, expected, 'debug')
    }
}
