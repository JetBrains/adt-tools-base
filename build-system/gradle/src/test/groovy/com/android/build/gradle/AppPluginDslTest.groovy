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
import org.gradle.api.JavaVersion
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
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(3, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(2, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("debug", "debugTest", variants, testVariants)
        checkNonTestedVariant("release", variants)
    }

    /**
     * Same as Basic but with a slightly different DSL.
     */
    public void testBasic2() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion = 15
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(3, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(2, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("debug", "debugTest", variants, testVariants)
        checkNonTestedVariant("release", variants)
    }

    public void testBasicWithStringTarget() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion "android-15"
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(3, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(2, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("debug", "debugTest", variants, testVariants)
        checkNonTestedVariant("release", variants)
    }

    public void testMultiRes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/multires")).build()

        project.apply plugin: 'com.android.application'

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
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            testBuildType "staging"

            buildTypes {
                staging {
                    signingConfig signingConfigs.debug
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(4, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(3, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("staging", "stagingTest", variants, testVariants)

        checkNonTestedVariant("debug", variants)
        checkNonTestedVariant("release", variants)
    }

    public void testFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15

            productFlavors {
                flavor1 {

                }
                flavor2 {

                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(6, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(4, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(2, testVariants.size())

        checkTestedVariant("flavor1Debug", "flavor1DebugTest", variants, testVariants)
        checkTestedVariant("flavor2Debug", "flavor2DebugTest", variants, testVariants)

        checkNonTestedVariant("flavor1Release", variants)
        checkNonTestedVariant("flavor2Release", variants)
    }

    public void testMultiFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15

            flavorDimensions   "dimension1", "dimension2"

            productFlavors {
                f1 {
                    flavorDimension   "dimension1"
                }
                f2 {
                    flavorDimension   "dimension1"
                }

                fa {
                    flavorDimension   "dimension2"
                }
                fb {
                    flavorDimension   "dimension2"
                }
                fc {
                    flavorDimension   "dimension2"
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(18, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(12, variants.size())

        Set<TestVariant> testVariants = project.android.testVariants
        assertEquals(6, testVariants.size())

        checkTestedVariant("f1FaDebug", "f1FaDebugTest", variants, testVariants)
        checkTestedVariant("f1FbDebug", "f1FbDebugTest", variants, testVariants)
        checkTestedVariant("f1FcDebug", "f1FcDebugTest", variants, testVariants)
        checkTestedVariant("f2FaDebug", "f2FaDebugTest", variants, testVariants)
        checkTestedVariant("f2FbDebug", "f2FbDebugTest", variants, testVariants)
        checkTestedVariant("f2FcDebug", "f2FcDebugTest", variants, testVariants)

        checkNonTestedVariant("f1FaRelease", variants)
        checkNonTestedVariant("f1FbRelease", variants)
        checkNonTestedVariant("f1FcRelease", variants)
        checkNonTestedVariant("f2FaRelease", variants)
        checkNonTestedVariant("f2FbRelease", variants)
        checkNonTestedVariant("f2FcRelease", variants)
    }

    public void testSourceSetsApi() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

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

    public void testObfuscationMappingFile() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15

            buildTypes {
                release {
                    minifyEnabled true
                    proguardFile getDefaultProguardFile('proguard-android.txt')
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(3, plugin.variantDataList.size())

        // we can now call this since the variants/tasks have been created

        // does not include tests
        Set<ApplicationVariant> variants = project.android.applicationVariants
        assertEquals(2, variants.size())

        for (ApplicationVariant variant : variants) {
            if ("release".equals(variant.getBuildType().getName())) {
                assertNotNull(variant.getMappingFile())
            } else {
                assertNull(variant.getMappingFile())
            }
        }
    }

    public void testSettingLanguageLevelFromCompileSdk() {
        def testLanguageLevel = { version, expectedLanguageLevel, useJack ->
            Project project = ProjectBuilder.builder().withProjectDir(
                    new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

            project.apply plugin: 'com.android.application'
            project.android {
                compileSdkVersion version
            }

            AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
            plugin.createAndroidTasks(false)

            assertEquals(
                    "target compatibility for ${version}",
                    expectedLanguageLevel.toString(),
                    project.compileReleaseJava.targetCompatibility)
            assertEquals(
                    "source compatibility for ${version}",
                    expectedLanguageLevel.toString(),
                    project.compileReleaseJava.sourceCompatibility)
        }

        for (useJack in [true, false]) {
            testLanguageLevel(15, JavaVersion.VERSION_1_6, useJack)
            testLanguageLevel(21, JavaVersion.VERSION_1_7, useJack)
            testLanguageLevel('android-21', JavaVersion.VERSION_1_7, useJack)
            testLanguageLevel('Google:GoogleInc:22', JavaVersion.VERSION_1_7, useJack)
        }
    }

    public void testSettingLanguageLevelFromCompileSdk_dontOverride() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'
        project.android {
            compileSdkVersion 21
            compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_6
                targetCompatibility JavaVersion.VERSION_1_6
            }
        }
        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(false)

        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                project.compileReleaseJava.targetCompatibility)
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                project.compileReleaseJava.sourceCompatibility)
    }

    public void testSettingLanguageLevelFromCompileSdk_unknownVersion() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_REGULAR}/basic")).build()

        project.apply plugin: 'com.android.application'
        project.android {
            compileSdkVersion 'foo'
        }
        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(false)

        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                project.compileReleaseJava.targetCompatibility)
        assertEquals(
                JavaVersion.VERSION_1_6.toString(),
                project.compileReleaseJava.sourceCompatibility)
    }

    private static void checkTestedVariant(@NonNull String variantName,
                                           @NonNull String testedVariantName,
                                           @NonNull Collection<ApplicationVariant> variants,
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
            if (!isTestVariant && variant.buildType.zipAlignEnabled) {
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