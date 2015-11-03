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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.FileHelper
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Tests the handling of test dependency.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class TestWithSameDepAsApp {

    @Rule
    public GradleTestProject project

    public String plugin
    public String appDependency
    public String testDependency
    public String className
    public String appUsage
    public String testUsage

    @Parameterized.Parameters(name = "{0}: {1}, {2}")
    public static Collection<Object[]> data() {
        def parameters = []

        for (plugin in ['com.android.application', 'com.android.library']) {
            // Check two JARs.
            parameters.add([
                    plugin,
                    'org.hamcrest:hamcrest-core:1.3',
                    'org.hamcrest:hamcrest-core:1.3',
                    'Lorg/hamcrest/Matcher;',
                    'org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is("foo");',
                    'org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is("foo");',
            ] as Object[])

            // Check two JARs, indirect conflict.
            parameters.add([
                    plugin,
                    'org.hamcrest:hamcrest-core:1.3',
                    'junit:junit:4.12',
                    'Lorg/hamcrest/Matcher;',
                    'org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is("foo");',
                    'org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is("foo");',
            ] as Object[])

            // Check two AARs.
            parameters.add([
                    plugin,
                    'com.android.support:support-v4:23.0.1',
                    'com.android.support:support-v4:23.0.1',
                    'Landroid/support/v4/widget/Space;',
                    'new android.support.v4.widget.Space(this);',
                    'new android.support.v4.widget.Space(getActivity());',
            ] as Object[])

            // Check two AARs, indirect conflict.
            parameters.add([
                    plugin,
                    'com.android.support:support-v4:23.0.1',
                    'com.android.support:recyclerview-v7:23.0.1',
                    'Landroid/support/v4/widget/Space;',
                    'new android.support.v4.widget.Space(this);',
                    'new android.support.v4.widget.Space(getActivity());',
            ] as Object[])
        }

        return parameters
    }

    public TestWithSameDepAsApp(
            String plugin,
            String appDependency,
            String testDependency,
            String className,
            String appUsage,
            String testUsage) {
        this.plugin = plugin
        this.appDependency = appDependency
        this.testDependency = testDependency
        this.className = className
        this.appUsage = appUsage
        this.testUsage = testUsage

        this.project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin(plugin))
            .create()
    }

    @Before
    public void setUp() {
        project.getBuildFile() << """
                dependencies {
                    compile '${this.appDependency}'
                    androidTestCompile '${this.testDependency}'
                }

                android.defaultConfig.minSdkVersion 16
                """

        FileHelper.addMethod(
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                """
                public void useDependency() {
                    ${this.appUsage}
                }
                """
        )

        FileHelper.addMethod(
                project.file("src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                """
                public void testDependency() {
                    ${this.testUsage}
                }
                """
        )
    }

    @Test
    public void "Test with same dep version than Tested does NOT embed dependency"() {
        project.execute("assembleDebug", "assembleDebugAndroidTest")

        if (plugin.contains("application")) {
            assertThatApk(project.getApk("debug")).containsClass(this.className)
            assertThatApk(project.getTestApk("debug")).doesNotContainClass(this.className)
        } else {
            // External dependencies are not packaged in AARs.
            assertThatAar(project.getAar("debug")).doesNotContainClass(this.className)
            // But should be in the test APK.
            assertThatApk(project.getTestApk("debug")).containsClass(this.className)
        }
    }

    @Test
    @Category(DeviceTests.class)
    void "run tests on devices"() {
        project.executeConnectedCheck()
    }
}
