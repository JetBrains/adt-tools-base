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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.builder.model.AndroidProject;

import org.junit.AfterClass
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import groovy.transform.CompileStatic

import static org.junit.Assert.assertNotNull;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * Tests that ensure that java resources files accessed with a relative or absolute path are
 * packaged correctly.
 */
@CompileStatic
public class MinifyLibAndAppWithJavaResTest {

    static Map<String, AndroidProject> models

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minifyLibWithJavaRes")
            .create()

    @BeforeClass
    static void setUp() {
        models = project.executeAndReturnMultiModel("clean", "assemble")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void testDebugPackaging() {
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
        Assume.assumeFalse("Ignore until Jack fixed proguard confusion", GradleTestProject.USE_JACK)
        File releaseApk = project.getSubproject("app").getApk("release")
        assertNotNull(releaseApk)
        ApkSubject apkSubject = assertThatApk(releaseApk);
        // check that resources with relative path lookup code have a matching obfuscated package
        // name.
        apkSubject.contains("com/android/tests/b/resources.properties")
        apkSubject.contains("com/android/tests/a/resources.properties")
        // check that resources with absolute path lookup remain in the original package name.
        apkSubject.contains("com/android/tests/util/another.properties")
        apkSubject.contains("com/android/tests/other/some.xml")
        apkSubject.contains("com/android/tests/other/another.properties")
    }
}
