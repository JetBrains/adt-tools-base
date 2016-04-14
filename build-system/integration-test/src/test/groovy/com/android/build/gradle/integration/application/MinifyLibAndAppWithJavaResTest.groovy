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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static org.junit.Assert.assertNotNull
import static org.junit.Assume.assumeFalse
/**
 * Tests that ensure that java resources files accessed with a relative or absolute path are
 * packaged correctly.
 */
@CompileStatic
@RunWith(FilterableParameterized)
public class MinifyLibAndAppWithJavaResTest {

    @Parameterized.Parameters(name = "useProguard = {0}")
    public static Collection<Object[]> data() {
        return [
                [true] as Object[],
                [false] as Object[],
        ]
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minifyLibWithJavaRes")
            .create()

    @Parameterized.Parameter(0)
    public boolean useProguard

    @Before
    void setUp() {
        project.getSubproject("app").buildFile << "android.buildTypes.release.useProguard $useProguard"
    }

    @Test
    void testDebugPackaging() {
        project.execute(":app:assembleDebug")
        File debugApk = project.getSubproject("app").getApk("debug")
        assertNotNull(debugApk)
        ApkSubject apkSubject = assertThatApk(debugApk);
        // check that resources with relative path lookup code have a matching obfuscated package
        // name.
        apkSubject.contains("com/android/tests/util/resources.properties")
        apkSubject.contains("com/android/tests/other/resources.properties")
        // check that resources with absolute path lookup remain in the original package name.
        apkSubject.contains("com/android/tests/util/another.properties")
        apkSubject.contains("com/android/tests/other/some.xml")
        apkSubject.contains("com/android/tests/other/another.properties")
    }

    @Test
    void testReleasePackaging() {
        assumeFalse("Ignore until Jack fixed proguard confusion", GradleTestProject.USE_JACK)

        project.execute(":app:assembleRelease")
        File releaseApk = project.getSubproject("app").getApk("release")
        assertNotNull(releaseApk)
        ApkSubject apkSubject = assertThatApk(releaseApk);
        // check that resources with absolute path lookup remain in the original package name.
        apkSubject.contains("com/android/tests/util/another.properties")
        apkSubject.contains("com/android/tests/other/some.xml")
        apkSubject.contains("com/android/tests/other/another.properties")

        if (useProguard) {
            // check that resources with relative path lookup code have a matching obfuscated package
            // name.
            apkSubject.contains("com/android/tests/b/resources.properties")
            apkSubject.contains("com/android/tests/a/resources.properties")
        }
    }
}
