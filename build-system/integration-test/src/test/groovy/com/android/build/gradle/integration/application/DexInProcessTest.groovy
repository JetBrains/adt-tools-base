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

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file
 * names
 */
@CompileStatic
class DexInProcessTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin('com.android.application'))
            .create()

    @Before
    void setDexInProcess() {
        project.buildFile << "System.setProperty('android.dexInProcess', 'true')"
    }

    @Test
    void testArtifactName() {
        project.execute("assembleDebug", "assembleAndroidTest")
    }

    @Test
    @Category(DeviceTests)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
