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
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.ClassField
import com.android.builder.model.Variant
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.ModelHelper
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
 * Test for BuildConfig field declared in build type, flavors, and variant and how they
 * override each other
 */
class BuildConfigTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().create()

    private static AndroidProject model

    @BeforeClass
    static void setup() {
        new HelloWorldApp().writeSources(project.testDir)
        project.getBuildFile() << """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                defaultConfig {
                    buildConfigField "int", "VALUE_DEFAULT", "1"
                    buildConfigField "int", "VALUE_DEBUG",   "1"
                    buildConfigField "int", "VALUE_FLAVOR",  "1"
                    buildConfigField "int", "VALUE_VARIANT", "1"
                }

                buildTypes {
                    debug {
                        buildConfigField "int", "VALUE_DEBUG",   "100"
                        buildConfigField "int", "VALUE_VARIANT", "100"
                    }
                }

                productFlavors {
                    flavor1 {
                        buildConfigField "int", "VALUE_DEBUG",   "10"
                        buildConfigField "int", "VALUE_FLAVOR",  "10"
                        buildConfigField "int", "VALUE_VARIANT", "10"
                    }
                    flavor2 {
                        buildConfigField "int", "VALUE_DEBUG",   "20"
                        buildConfigField "int", "VALUE_FLAVOR",  "20"
                        buildConfigField "int", "VALUE_VARIANT", "20"
                    }
                }

                applicationVariants.all { variant ->
                    if (variant.buildType.name == "debug") {
                        variant.buildConfigField "int", "VALUE_VARIANT", "1000"
                    }
                }
            }
            """.stripIndent()

        model = project.executeAndReturnModel(
                'clean',
                'generateFlavor1DebugBuildConfig',
                'generateFlavor1ReleaseBuildConfig',
                'generateFlavor2DebugBuildConfig',
                'generateFlavor2ReleaseBuildConfig')
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void builFlavor1Debug() {
        String expected =
"""/**
 * Automatically generated file. DO NOT MODIFY
 */
package com.example.helloworld;

public final class BuildConfig {
  public static final boolean DEBUG = Boolean.parseBoolean("true");
  public static final String APPLICATION_ID = "com.example.helloworld";
  public static final String BUILD_TYPE = "debug";
  public static final String FLAVOR = "flavor1";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "";
  // Fields from the variant
  public static final int VALUE_VARIANT = 1000;
  // Fields from build type: debug
  public static final int VALUE_DEBUG = 100;
  // Fields from product flavor: flavor1
  public static final int VALUE_FLAVOR = 10;
  // Fields from default config.
  public static final int VALUE_DEFAULT = 1;
}
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
"""/**
 * Automatically generated file. DO NOT MODIFY
 */
package com.example.helloworld;

public final class BuildConfig {
  public static final boolean DEBUG = Boolean.parseBoolean("true");
  public static final String APPLICATION_ID = "com.example.helloworld";
  public static final String BUILD_TYPE = "debug";
  public static final String FLAVOR = "flavor2";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "";
  // Fields from the variant
  public static final int VALUE_VARIANT = 1000;
  // Fields from build type: debug
  public static final int VALUE_DEBUG = 100;
  // Fields from product flavor: flavor2
  public static final int VALUE_FLAVOR = 20;
  // Fields from default config.
  public static final int VALUE_DEFAULT = 1;
}
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
                """/**
 * Automatically generated file. DO NOT MODIFY
 */
package com.example.helloworld;

public final class BuildConfig {
  public static final boolean DEBUG = false;
  public static final String APPLICATION_ID = "com.example.helloworld";
  public static final String BUILD_TYPE = "release";
  public static final String FLAVOR = "flavor1";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "";
  // Fields from product flavor: flavor1
  public static final int VALUE_DEBUG = 10;
  public static final int VALUE_FLAVOR = 10;
  public static final int VALUE_VARIANT = 10;
  // Fields from default config.
  public static final int VALUE_DEFAULT = 1;
}
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
                """/**
 * Automatically generated file. DO NOT MODIFY
 */
package com.example.helloworld;

public final class BuildConfig {
  public static final boolean DEBUG = false;
  public static final String APPLICATION_ID = "com.example.helloworld";
  public static final String BUILD_TYPE = "release";
  public static final String FLAVOR = "flavor2";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = "";
  // Fields from product flavor: flavor2
  public static final int VALUE_DEBUG = 20;
  public static final int VALUE_FLAVOR = 20;
  public static final int VALUE_VARIANT = 20;
  // Fields from default config.
  public static final int VALUE_DEFAULT = 1;
}
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
                "build/generated/source/buildConfig/$variantDir/com/example/helloworld/BuildConfig.java")
        Assert.assertTrue("Missing file: " + outputFile, outputFile.isFile());
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

        Map<String, ClassField> value = artifact.getBuildConfigFields()
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
