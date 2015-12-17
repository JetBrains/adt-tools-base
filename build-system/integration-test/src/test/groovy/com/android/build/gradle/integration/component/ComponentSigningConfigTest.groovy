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

package com.android.build.gradle.integration.component

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.ide.common.signing.KeystoreHelper
import com.android.utils.StdLogger
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import java.security.KeyStore

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Integration test with signing config.
 */
class ComponentSigningConfigTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .forExperimentalPlugin(true)
            .create();

    @BeforeClass
    public static void setUp() {
        KeystoreHelper.createDebugStore(
                KeyStore.getDefaultType(),
                project.file("debug.keystore"),
                "android",
                "android",
                "androiddebugkey",
                new StdLogger(StdLogger.Level.INFO));
        // TODO: Let gradle resolve file when it is able to do that with File in a Managed type.
        // I have to do some os specific magic due to \ interpretation in groovy.
        String storeFile = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS ?
                """storeFile = new File(/${project.file("debug.keystore")}/)""" :
                """storeFile = new File("${project.file("debug.keystore")}")"""

        project.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.buildTypes {
        debug {
            buildConfigFields.with {
                create() {
                    type = "int"
                    name = "VALUE"
                    value = "1"
                }
            }
        }
        release {
            signingConfig = \$("android.signingConfigs.myConfig")
        }
    }

    android.signingConfigs {
        create("myConfig") {
""" + storeFile + """
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "jks"
        }
    }
}
"""
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void assembleRelease() {
        project.execute(["-Dorg.gradle.model.dsl=true"], "clean", "assembleRelease")
        assertThatZip(project.getApk("release")).contains("META-INF/CERT.RSA")
    }


}
