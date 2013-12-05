/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle
import com.android.annotations.NonNull
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.internal.test.BaseTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
/**
 * Tests for the public DSL of the App plugin ("android")
 */
public class AppPluginDslTest extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        BasePlugin.TEST_SDK_DIR = new File("foo")
    }

    public void testBasic() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15
        }

        project.afterEvaluate {
            Set<ApplicationVariant> variants = project.android.applicationVariants
            assertEquals(2, variants.size())

            Set<TestVariant> testVariants = project.android.testVariants
            assertEquals(1, testVariants.size())

            checkTestedVariant("Debug", "Test", variants, testVariants)
            checkNonTestedVariant("Release", variants)
        }
    }

    /**
     * Same as Basic but with a slightly different DSL.
     */
    public void testBasic2() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion = 15
        }

        project.afterEvaluate {
            Set<ApplicationVariant> variants = project.android.applicationVariants
            assertEquals(2, variants.size())

            Set<TestVariant> testVariants = project.android.testVariants
            assertEquals(1, testVariants.size())

            checkTestedVariant("Debug", "Test", variants, testVariants)
            checkNonTestedVariant("Release", variants)
        }
    }

    public void testBasicWithStringTarget() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion "android-15"
        }

        project.afterEvaluate {
            Set<ApplicationVariant> variants = project.android.applicationVariants
            assertEquals(2, variants.size())

            Set<TestVariant> testVariants = project.android.testVariants
            assertEquals(1, testVariants.size())

            checkTestedVariant("Debug", "Test", variants, testVariants)
            checkNonTestedVariant("Release", variants)
        }
    }

    public void testMultiRes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "multires")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15

            sourceSets {
                main {
                    res {
                        srcDirs 'src/main/res1', 'src/main/res2'
                    }
                }
            }
        }

        // nothing to be done here. If the DSL fails, it'll throw an exception
    }

    public void testBuildTypes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15
            testBuildType "staging"

            buildTypes {
                staging {
                    signingConfig signingConfigs.debug
                }
            }
        }

        project.afterEvaluate {
            // does not include tests
            Set<ApplicationVariant> variants = project.android.applicationVariants
            assertEquals(3, variants.size())

            Set<TestVariant> testVariants = project.android.testVariants
            assertEquals(1, testVariants.size())

            checkTestedVariant("Staging", "Test", variants, testVariants)

            checkNonTestedVariant("Debug", variants)
            checkNonTestedVariant("Release", variants)
        }
    }

    public void testFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15

            productFlavors {
                flavor1 {

                }
                flavor2 {

                }
            }
        }

        project.afterEvaluate {
            // does not include tests
            Set<ApplicationVariant> variants = project.android.applicationVariants
            assertEquals(4, variants.size())

            Set<TestVariant> testVariants = project.android.testVariants
            assertEquals(2, testVariants.size())

            checkTestedVariant("Flavor1Debug", "Flavor1Test", variants, testVariants)
            checkTestedVariant("Flavor2Debug", "Flavor2Test", variants, testVariants)

            checkNonTestedVariant("Flavor1Release", variants)
            checkNonTestedVariant("Flavor2Release", variants)
        }
    }

    public void testMultiFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15

            flavorGroups   "group1", "group2"

            productFlavors {
                f1 {
                    flavorGroup   "group1"
                }
                f2 {
                    flavorGroup   "group1"
                }

                fa {
                    flavorGroup   "group2"
                }
                fb {
                    flavorGroup   "group2"
                }
                fc {
                    flavorGroup   "group2"
                }
            }
        }

        project.afterEvaluate {
            // does not include tests
            Set<ApplicationVariant> variants = project.android.applicationVariants
            assertEquals(12, variants.size())

            Set<TestVariant> testVariants = project.android.testVariants
            assertEquals(6, testVariants.size())

            checkTestedVariant("F1FaDebug", "F1FaTest", variants, testVariants)
            checkTestedVariant("F1FbDebug", "F1FbTest", variants, testVariants)
            checkTestedVariant("F1FcDebug", "F1FcTest", variants, testVariants)
            checkTestedVariant("F2FaDebug", "F2FaTest", variants, testVariants)
            checkTestedVariant("F2FbDebug", "F2FbTest", variants, testVariants)
            checkTestedVariant("F2FcDebug", "F2FcTest", variants, testVariants)

            checkNonTestedVariant("F1FaRelease", variants)
            checkNonTestedVariant("F1FbRelease", variants)
            checkNonTestedVariant("F1FcRelease", variants)
            checkNonTestedVariant("F2FaRelease", variants)
            checkNonTestedVariant("F2FbRelease", variants)
            checkNonTestedVariant("F2FcRelease", variants)
        }
    }

    public void testSourceSetsApi() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15
        }

        // query the sourceSets, will throw if missing
        println project.android.sourceSets.main.java.srcDirs
        println project.android.sourceSets.main.resources.srcDirs
        println project.android.sourceSets.main.manifest.srcFile
        println project.android.sourceSets.main.res.srcDirs
        println project.android.sourceSets.main.assets.srcDirs
    }


    private static void checkTestedVariant(@NonNull String variantName,
                                           @NonNull String testedVariantName,
                                           @NonNull Set<ApplicationVariant> variants,
                                           @NonNull Set<TestVariant> testVariants) {
        ApplicationVariant variant = findNamedItem(variants, variantName, "variantData")
        assertNotNull(variant.testVariant)
        assertEquals(testedVariantName, variant.testVariant.name)
        assertEquals(variant.testVariant, findNamedItemMaybe(testVariants, testedVariantName))
        checkTasks(variant)
        checkTasks(variant.testVariant)
    }

    private static void checkNonTestedVariant(@NonNull String variantName,
                                              @NonNull Set<ApplicationVariant> variants) {
        ApplicationVariant variant = findNamedItem(variants, variantName, "variantData")
        assertNull(variant.testVariant)
        checkTasks(variant)
    }

    private static void checkTasks(@NonNull ApkVariant variant) {
        boolean isTestVariant = variant instanceof TestVariant;

        assertNotNull(variant.processManifest)
        assertNotNull(variant.aidlCompile)
        assertNotNull(variant.mergeResources)
        assertNotNull(variant.mergeAssets)
        assertNotNull(variant.processResources)
        assertNotNull(variant.generateBuildConfig)
        assertNotNull(variant.javaCompile)
        assertNotNull(variant.processJavaResources)
        assertNotNull(variant.dex)
        assertNotNull(variant.packageApplication)

        assertNotNull(variant.assemble)
        assertNotNull(variant.uninstall)

        if (variant.isSigningReady()) {
            assertNotNull(variant.install)

            // tested variant are never zipAligned.
            if (!isTestVariant && variant.buildType.zipAlign) {
                assertNotNull(variant.zipAlign)
            } else {
                assertNull(variant.zipAlign)
            }
        } else {
            assertNull(variant.install)
        }

        if (isTestVariant) {
            TestVariant testVariant = variant as TestVariant
            assertNotNull(testVariant.connectedInstrumentTest)
            assertNotNull(testVariant.testedVariant)
        }
    }
}