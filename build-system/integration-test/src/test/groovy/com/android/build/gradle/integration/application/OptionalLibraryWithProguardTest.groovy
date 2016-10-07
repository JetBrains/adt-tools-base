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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.shrinker.ShrinkerTestUtils
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
/**
 * Test for the new optional library mechanism when a library dependency uses sone now optional
 * classes and runs proguard, in which case proguard needs to see the optional classes.
 */
@RunWith(FilterableParameterized)
@CompileStatic
class OptionalLibraryWithProguardTest {

    @Parameterized.Parameters(name = "useProguard = {0}")
    public static Collection<Object[]> data() {
        return [
                [true] as Object[],
                [false] as Object[],
        ]
    }

    @Parameterized.Parameter(0)
    public boolean useProguard

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("optionalLibInLibWithProguard")
            .create()

    @Before
    public void chooseShrinker() throws Exception {
        if (!useProguard) {
            ShrinkerTestUtils.enableShrinker(project.getSubproject("app"), "debug")
        }
    }

    @Test
    void "test that proguard compiles with optional classes"() {
        project.execute("clean", "app:assembleDebug")
    }
}
