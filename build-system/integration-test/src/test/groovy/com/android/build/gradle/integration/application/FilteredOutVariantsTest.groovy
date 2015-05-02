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
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse

/**
 * Assemble tests for filteredOutVariants.
 */
@CompileStatic
class FilteredOutVariantsTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("filteredOutVariants")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check filtered out variant isn't in model"() {
        Collection<Variant> variants = model.getVariants()
        // check we have the right number of variants:
        // arm/cupcake, arm/gingerbread, x86/gingerbread, mips/gingerbread
        // all 4 in release and debug
        assertEquals("Variant Count", 8, variants.size())

        for (Variant variant : variants) {
            List<String> flavors = variant.getProductFlavors()
            assertFalse("check ignored x86/cupcake",
                    flavors.contains("x68") && flavors.contains("cupcake"))
            assertFalse("check ignored mips/cupcake",
                    flavors.contains("mips") && flavors.contains("cupcake"))
        }
    }

    @Test
    void lint() {
        project.execute("lint")
    }
}
