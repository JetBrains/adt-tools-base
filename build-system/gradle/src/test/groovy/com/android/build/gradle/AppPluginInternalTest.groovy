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

import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.test.BaseTest
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.model.SigningConfig
import com.android.ide.common.signing.KeystoreHelper
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder

/**
 * Tests for the internal workings of the app plugin ("android")
 */
public class AppPluginInternalTest extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        BasePlugin.TEST_SDK_DIR = new File("foo")
    }

    public void testBasic() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(true /*force*/)

        assertEquals(2, plugin.variantManager.buildTypes.size())
        assertNotNull(plugin.variantManager.buildTypes.get(BuilderConstants.DEBUG))
        assertNotNull(plugin.variantManager.buildTypes.get(BuilderConstants.RELEASE))
        assertEquals(0, plugin.variantManager.productFlavors.size())


        List<BaseVariantData> variants = plugin.variantDataList
        assertEquals(3, variants.size()) // includes the test variant(s)

        findNamedItem(variants, "debug", "variantData")
        findNamedItem(variants, "release", "variantData")
        findNamedItem(variants, "debugTest", "variantData")
    }

    public void testDefaultConfig() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"

            signingConfigs {
                fakeConfig {
                    storeFile project.file("aa")
                    storePassword "bb"
                    keyAlias "cc"
                    keyPassword "dd"
                }
            }

            defaultConfig {
                versionCode 1
                versionName "2.0"
                minSdkVersion 2
                targetSdkVersion 3

                signingConfig signingConfigs.fakeConfig
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(true /*force*/)

        assertEquals(1, plugin.extension.defaultConfig.versionCode)
        assertNotNull(plugin.extension.defaultConfig.minSdkVersion)
        assertEquals(2, plugin.extension.defaultConfig.minSdkVersion.apiLevel)
        assertNotNull(plugin.extension.defaultConfig.targetSdkVersion)
        assertEquals(3, plugin.extension.defaultConfig.targetSdkVersion.apiLevel)
        assertEquals("2.0", plugin.extension.defaultConfig.versionName)

        assertEquals(new File(project.projectDir, "aa"),
                plugin.extension.defaultConfig.signingConfig.storeFile)
        assertEquals("bb", plugin.extension.defaultConfig.signingConfig.storePassword)
        assertEquals("cc", plugin.extension.defaultConfig.signingConfig.keyAlias)
        assertEquals("dd", plugin.extension.defaultConfig.signingConfig.keyPassword)
    }

    public void testBuildTypes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"
            testBuildType "staging"

            buildTypes {
                staging {
                    signingConfig signingConfigs.debug
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(true /*force*/)

        assertEquals(3, plugin.variantManager.buildTypes.size())

        List<BaseVariantData> variants = plugin.variantDataList
        assertEquals(4, variants.size()) // includes the test variant(s)

        String[] variantNames = [
                "debug", "release", "staging"]

        for (String variantName : variantNames) {
            findNamedItem(variants, variantName, "variantData")
        }

        BaseVariantData testVariant = findNamedItem(variants, "stagingTest", "variantData")
        assertEquals("staging", testVariant.variantConfiguration.buildType.name)
    }

    public void testFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"

            productFlavors {
                flavor1 {

                }
                flavor2 {

                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(true /*force*/)

        assertEquals(2, plugin.variantManager.productFlavors.size())

        List<BaseVariantData> variants = plugin.variantDataList
        assertEquals(6, variants.size()) // includes the test variant(s)

        String[] variantNames = [
                "flavor1Debug", "flavor1Release", "flavor1DebugTest",
                "flavor2Debug", "flavor2Release", "flavor2DebugTest"]

        for (String variantName : variantNames) {
            findNamedItem(variants, variantName, "variantData")
        }
    }

    public void testMultiFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"

            flavorDimensions   "dimension1", "dimension2"

            productFlavors {
                f1 {
                    flavorDimension "dimension1"
                }
                f2 {
                    flavorDimension "dimension1"
                }

                fa {
                    flavorDimension "dimension2"
                }
                fb {
                    flavorDimension "dimension2"
                }
                fc {
                    flavorDimension "dimension2"
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(true /*force*/)

        assertEquals(5, plugin.variantManager.productFlavors.size())

        List<BaseVariantData> variants = plugin.variantDataList
        assertEquals(18, variants.size())   // includes the test variant(s)

        String[] variantNames = [
                "f1FaDebug",
                "f1FbDebug",
                "f1FcDebug",
                "f2FaDebug",
                "f2FbDebug",
                "f2FcDebug",
                "f1FaRelease",
                "f1FbRelease",
                "f1FcRelease",
                "f2FaRelease",
                "f2FbRelease",
                "f2FcRelease",
                "f1FaDebugTest",
                "f1FbDebugTest",
                "f1FcDebugTest",
                "f2FaDebugTest",
                "f2FbDebugTest",
                "f2FcDebugTest"];

        for (String variantName : variantNames) {
            findNamedItem(variants, variantName, "variantData");
        }
    }

    public void testSigningConfigs() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"

            signingConfigs {
                one {
                    storeFile project.file("a1")
                    storePassword "b1"
                    keyAlias "c1"
                    keyPassword "d1"
                }
                two {
                    storeFile project.file("a2")
                    storePassword "b2"
                    keyAlias "c2"
                    keyPassword "d2"
                }
                three {
                    storeFile project.file("a3")
                    storePassword "b3"
                    keyAlias "c3"
                    keyPassword "d3"
                }
            }

            defaultConfig {
                versionCode 1
                versionName "2.0"
                minSdkVersion 2
                targetSdkVersion 3
            }

            buildTypes {
                debug {
                }
                staging {
                }
                release {
                    signingConfig owner.signingConfigs.three
                }
            }

            productFlavors {
                flavor1 {
                }
                flavor2 {
                    signingConfig owner.signingConfigs.one
                }
            }

        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        plugin.createAndroidTasks(true /*force*/)

        List<BaseVariantData> variants = plugin.variantDataList
        assertEquals(8, variants.size())   // includes the test variant(s)

        BaseVariantData variant
        SigningConfig signingConfig

        variant = findNamedItem(variants, "flavor1Debug", "variantData")
        signingConfig = variant.variantConfiguration.signingConfig
        assertNotNull(signingConfig)
        assertEquals(KeystoreHelper.defaultDebugKeystoreLocation(), signingConfig.storeFile?.absolutePath)

        variant = findNamedItem(variants, "flavor1Staging", "variantData")
        signingConfig = variant.variantConfiguration.signingConfig
        assertNull(signingConfig)

        variant = findNamedItem(variants, "flavor1Release", "variantData")
        signingConfig = variant.variantConfiguration.signingConfig
        assertNotNull(signingConfig)
        assertEquals(new File(project.projectDir, "a3"), signingConfig.storeFile)

        variant = findNamedItem(variants, "flavor2Debug", "variantData")
        signingConfig = variant.variantConfiguration.signingConfig
        assertNotNull(signingConfig)
        assertEquals(KeystoreHelper.defaultDebugKeystoreLocation(), signingConfig.storeFile?.absolutePath)

        variant = findNamedItem(variants, "flavor2Staging", "variantData")
        signingConfig = variant.variantConfiguration.signingConfig
        assertNotNull(signingConfig)
        assertEquals(new File(project.projectDir, "a1"), signingConfig.storeFile)

        variant = findNamedItem(variants, "flavor2Release", "variantData")
        signingConfig = variant.variantConfiguration.signingConfig
        assertNotNull(signingConfig)
        assertEquals(new File(project.projectDir, "a3"), signingConfig.storeFile)
    }

    /**
     * test that debug build type maps to the SigningConfig object as the signingConfig container
     * @throws Exception
     */
    public void testDebugSigningConfig() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"

            signingConfigs {
                debug {
                    storePassword = "foo"
                }
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        // check that the debug buildType has the updated debug signing config.
        DefaultBuildType buildType = plugin.variantManager.buildTypes.get(BuilderConstants.DEBUG).buildType
        SigningConfig signingConfig = buildType.signingConfig
        assertEquals(plugin.variantManager.signingConfigs.get(BuilderConstants.DEBUG), signingConfig)
        assertEquals("foo", signingConfig.storePassword)
    }

    public void testSigningConfigInitWith() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'

        project.android {
            compileSdkVersion 15

            signingConfigs {
                foo.initWith(owner.signingConfigs.debug)
            }
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        SigningConfig debugSC = plugin.variantManager.signingConfigs.get(BuilderConstants.DEBUG)
        SigningConfig fooSC = plugin.variantManager.signingConfigs.get("foo")

        assertNotNull(fooSC);

        assertEquals(debugSC.getStoreFile(), fooSC.getStoreFile());
        assertEquals(debugSC.getStorePassword(), fooSC.getStorePassword());
        assertEquals(debugSC.getKeyAlias(), fooSC.getKeyAlias());
        assertEquals(debugSC.getKeyPassword(), fooSC.getKeyPassword());
    }

    public void testPluginDetection() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_SAMPLES}/basic")).build()

        project.apply plugin: 'com.android.application'
        project.apply plugin: 'java'

        project.android {
            compileSdkVersion 15
            buildToolsVersion "19"
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)
        Exception recordedException = null;
        try {
            plugin.createAndroidTasks(true /*force*/)
        } catch (Exception e) {
            recordedException = e;
        }

        assertNotNull(recordedException)
        assertEquals(BadPluginException.class, recordedException.getClass())
    }

    public void testPathNormalization() {
        ModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier(
                "group", "name", "1.2");
        assertEquals("app", BasePlugin.normalize(moduleVersionIdentifier, "app"));
        assertEquals(".app", BasePlugin.normalize(moduleVersionIdentifier, ".app"))
        assertEquals("app@@@", BasePlugin.normalize(moduleVersionIdentifier, "app..."))
        assertEquals("app@@@", BasePlugin.normalize(moduleVersionIdentifier, "app. ."))
        assertEquals(".app@@", BasePlugin.normalize(moduleVersionIdentifier, ".app%%"))
        assertEquals("app.txt", BasePlugin.normalize(moduleVersionIdentifier, "app.txt"))
        assertEquals("app@@@txt", BasePlugin.normalize(moduleVersionIdentifier, "app%*?txt"))
    }
}