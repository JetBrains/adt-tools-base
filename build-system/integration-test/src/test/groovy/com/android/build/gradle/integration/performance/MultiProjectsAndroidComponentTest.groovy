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

package com.android.build.gradle.integration.performance
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.fixture.app.VariantBuildScriptGenerator
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.BUILD_FULL
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.EVALUATION

/**
 * Performance test on gradle experimental plugin with multiple sub-projects and multiple variants.
 */
class MultiProjectsAndroidComponentTest {
    public static AndroidTestApp app = HelloWorldApp.noBuildFile()
    static {
        VariantBuildScriptGenerator generator = new VariantBuildScriptGenerator(
                buildTypes: VariantBuildScriptGenerator.MEDIUM_NUMBER,
                productFlavors: VariantBuildScriptGenerator.MEDIUM_NUMBER,
                """
                apply plugin: "com.android.model.application"

                model {
                    android {
                        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                    }

                    android.buildTypes {
                        \${buildTypes}
                    }

                    android.productFlavors {
                        \${productFlavors}
                    }
                }
                """.stripIndent())
        generator.addPostProcessor("buildTypes") { return (String) "create(\"$it\")" }
        generator.addPostProcessor("productFlavors") { return (String) "create(\"$it\")" }

        app.addFile(new TestSourceFile("", "build.gradle", generator.createBuildScript()))
    }

    public static TestProject baseProject = new MultiModuleTestProject("app", app, 10)

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(baseProject)
            .forExperimentalPlugin(true)
            .create()

    @BeforeClass
    static void setUp() {
        // Execute before performance test to warm up the cache.
        project.execute("help");
    }

    @AfterClass
    static void cleanUp() {
        app = null;
        baseProject = null;
        project = null;
    }

    @Test
    void "performance test - projects"() {
        project.executeWithBenchmark("MultiProjectsAndroid", EVALUATION, "projects")
    }

    @Test
    void "performance test - single variant"() {
        project.executeWithBenchmark("MultiProjectsAndroid", BUILD_FULL, ":app0:assembleProductFlavor0BuildType0")
    }
}
