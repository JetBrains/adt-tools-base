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
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.*

import static org.junit.Assert.assertEquals

/**
 * Assemble tests for filteredOutBuildType.
 */
class FilteredOutBuildTypeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("filteredOutBuildType")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setup() {
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check filtered out variant isn't in model"() {
        // Load the custom model for the project
        assertEquals("Variant Count", 1, model.getVariants().size())
        Variant variant = model.getVariants().iterator().next()
        assertEquals("Variant name", "release", variant.getBuildType())
    }

    @Test
    void lint() {
        project.execute("lint")
    }
}
