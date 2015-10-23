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

package com.android.build.gradle.integration.test

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Created by jedo on 9/1/15.
 */
class SeparateTestModuleWithAppDependenciesTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModule")
            .create()

    static Map<String, AndroidProject> models

    @BeforeClass
    static void setup() {
        project.getSubproject("app").getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion

    publishNonDefault true

    defaultConfig {
        minSdkVersion 9
    }
}
dependencies {
    compile 'com.google.android.gms:play-services-base:7.5.0'
}
        """

        models = project.executeAndReturnMultiModel("clean", "assemble")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check model"() throws Exception {
        // check the content of the test model.
    }
}
