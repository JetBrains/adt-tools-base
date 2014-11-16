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



package com.android.test.application

import com.android.annotations.NonNull
import com.android.test.common.fixture.GradleTestProject
import com.android.test.common.fixture.app.HelloWorldApp
import com.google.common.base.Charsets
import com.google.common.io.Files
import junit.framework.Assert
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static junit.framework.Assert.assertEquals

/**
 * Assemble tests for basic.
 */
class BuildConfigTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().create();

    @BeforeClass
    static void setup() {
        new HelloWorldApp().writeSources(project.getSourceDir())
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

        project.execute(
                'clean',
                'generateFlavor1DebugBuildConfig',
                'generateFlavor1ReleaseBuildConfig',
                'generateFlavor2DebugBuildConfig',
                'generateFlavor2ReleaseBuildConfig')
    }

    @Test
    void flavor1Debug() {
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
    void flavor2Debug() {
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
    void flavor1Release() {
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
    void flavor2Release() {
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


    private static void checkBuildConfig(@NonNull String expected, @NonNull String variantDir) {
        File outputFile = new File(project.getTestDir(),
                "build/generated/source/buildConfig/$variantDir/com/example/helloworld/BuildConfig.java")
        Assert.assertTrue("Missing file: " + outputFile, outputFile.isFile());
        assertEquals(expected, Files.asByteSource(outputFile).asCharSource(Charsets.UTF_8).read())
    }
}
