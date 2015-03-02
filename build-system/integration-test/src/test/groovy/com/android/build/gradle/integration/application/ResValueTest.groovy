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

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.ClassField
import com.android.builder.model.Variant
import com.google.common.base.Charsets
import com.google.common.collect.Maps
import com.google.common.io.Files
import junit.framework.Assert
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static junit.framework.Assert.assertEquals
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Test for Res Values declared in build type, flavors, and variant and how they
 * override each other
 */
class ResValueTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldApp())
            .create()

    private static AndroidProject model

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                defaultConfig {
                    resValue "string", "VALUE_DEFAULT", "1"
                    resValue "string", "VALUE_DEBUG",   "1"
                    resValue "string", "VALUE_FLAVOR",  "1"
                    resValue "string", "VALUE_VARIANT", "1"
                }

                buildTypes {
                    debug {
                        resValue "string", "VALUE_DEBUG",   "100"
                        resValue "string", "VALUE_VARIANT", "100"
                    }
                }

                productFlavors {
                    flavor1 {
                        resValue "string", "VALUE_DEBUG",   "10"
                        resValue "string", "VALUE_FLAVOR",  "10"
                        resValue "string", "VALUE_VARIANT", "10"
                    }
                    flavor2 {
                        resValue "string", "VALUE_DEBUG",   "20"
                        resValue "string", "VALUE_FLAVOR",  "20"
                        resValue "string", "VALUE_VARIANT", "20"
                    }
                }

                applicationVariants.all { variant ->
                    if (variant.buildType.name == "debug") {
                        variant.resValue "string", "VALUE_VARIANT", "1000"
                    }
                }
            }
            """.stripIndent()

        model = project.executeAndReturnModel(
                'clean',
                'generateFlavor1DebugResValue',
                'generateFlavor1ReleaseResValue',
                'generateFlavor2DebugResValue',
                'generateFlavor2ReleaseResValue')
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void builFlavor1Debug() {
        String expected =
"""<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Automatically generated file. DO NOT MODIFY -->

    <!-- Values from the variant -->
    <string name="VALUE_VARIANT">1000</string>
    <!-- Values from build type: debug -->
    <string name="VALUE_DEBUG">100</string>
    <!-- Values from product flavor: flavor1 -->
    <string name="VALUE_FLAVOR">10</string>
    <!-- Values from default config. -->
    <string name="VALUE_DEFAULT">1</string>

</resources>
"""
        checkBuildConfig(expected, 'flavor1/debug')
    }

    @Test
    void modelFlavor1Debug() {
        Map<String, String> map = Maps.newHashMap()
        map.put('VALUE_DEFAULT', '1')
        map.put('VALUE_FLAVOR', '10')
        map.put('VALUE_DEBUG', '100')
        map.put('VALUE_VARIANT', '1000')
        checkVariant(model.getVariants(), 'flavor1Debug', map)
    }

    @Test
    void buildFlavor2Debug() {
        String expected =
"""<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Automatically generated file. DO NOT MODIFY -->

    <!-- Values from the variant -->
    <string name="VALUE_VARIANT">1000</string>
    <!-- Values from build type: debug -->
    <string name="VALUE_DEBUG">100</string>
    <!-- Values from product flavor: flavor2 -->
    <string name="VALUE_FLAVOR">20</string>
    <!-- Values from default config. -->
    <string name="VALUE_DEFAULT">1</string>

</resources>
"""
        checkBuildConfig(expected, 'flavor2/debug')
    }

    @Test
    void modelFlavor2Debug() {
        Map<String, String> map = Maps.newHashMap()
        map.put('VALUE_DEFAULT', '1')
        map.put('VALUE_FLAVOR', '20')
        map.put('VALUE_DEBUG', '100')
        map.put('VALUE_VARIANT', '1000')
        checkVariant(model.getVariants(), 'flavor2Debug', map)
    }

    @Test
    void buildFlavor1Release() {
        String expected =
"""<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Automatically generated file. DO NOT MODIFY -->

    <!-- Values from product flavor: flavor1 -->
    <string name="VALUE_DEBUG">10</string>
    <string name="VALUE_FLAVOR">10</string>
    <string name="VALUE_VARIANT">10</string>
    <!-- Values from default config. -->
    <string name="VALUE_DEFAULT">1</string>

</resources>
"""
        checkBuildConfig(expected, 'flavor1/release')
    }

    @Test
    void modelFlavor1Release() {
        Map<String, String> map = Maps.newHashMap()
        map.put('VALUE_DEFAULT', '1')
        map.put('VALUE_FLAVOR', '10')
        map.put('VALUE_DEBUG', '10')
        map.put('VALUE_VARIANT', '10')
        checkVariant(model.getVariants(), 'flavor1Release', map)
    }

    @Test
    void buildFlavor2Release() {
        String expected =
"""<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Automatically generated file. DO NOT MODIFY -->

    <!-- Values from product flavor: flavor2 -->
    <string name="VALUE_DEBUG">20</string>
    <string name="VALUE_FLAVOR">20</string>
    <string name="VALUE_VARIANT">20</string>
    <!-- Values from default config. -->
    <string name="VALUE_DEFAULT">1</string>

</resources>
"""
        checkBuildConfig(expected, 'flavor2/release')
    }

    @Test
    void modelFlavor2Release() {
        Map<String, String> map = Maps.newHashMap()
        map.put('VALUE_DEFAULT', '1')
        map.put('VALUE_FLAVOR', '20')
        map.put('VALUE_DEBUG', '20')
        map.put('VALUE_VARIANT', '20')
        checkVariant(model.getVariants(), 'flavor2Release', map)
    }

    private static void checkBuildConfig(@NonNull String expected, @NonNull String variantDir) {
        File outputFile = new File(project.getTestDir(),
                "build/generated/res/generated/$variantDir/values/generated.xml")
        Assert.assertTrue("Missing file: " + outputFile, outputFile.isFile())
        assertEquals(expected, Files.asByteSource(outputFile).asCharSource(Charsets.UTF_8).read())
    }

    private static void checkVariant(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName,
            @Nullable Map<String, String> valueMap) {
        Variant variant = ModelHelper.findVariantByName(variants, variantName)
        assertNotNull("${variantName} variant null-check", variant)

        AndroidArtifact artifact = variant.getMainArtifact()
        assertNotNull("${variantName} main artifact null-check", artifact)

        Map<String, ClassField> value = artifact.getResValues()
        assertNotNull(value)

        // check the map against the expected one.
        assertEquals(valueMap.keySet(), value.keySet())
        for (String key : valueMap.keySet()) {
            ClassField field = value.get(key)
            assertNotNull("${variantName}: expected field ${key}", field)
            assertEquals(
                    "${variantName}: check Value of ${key}",
                    valueMap.get(key),
                    field.getValue())
        }
    }
}
