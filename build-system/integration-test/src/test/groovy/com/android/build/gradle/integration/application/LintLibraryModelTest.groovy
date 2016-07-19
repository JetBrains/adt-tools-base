/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.build.gradle.tasks.Lint
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Assemble tests for lintLibraryModel.
 * <p>
 * To run just this test: ./gradlew :base:int:test -Dtest.single=LintLibraryModelTest
 */
@CompileStatic
class LintLibraryModelTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("lintLibraryModel")
            .create()

    @BeforeClass
    static void setup() {
        try {
            System.setProperty(Lint.MODEL_LIBRARIES_PROPERTY, "true");
            project.execute("clean", ":app:lintDebug");
        } finally {
            System.setProperty(Lint.MODEL_LIBRARIES_PROPERTY, "false");
        }
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check lint library model"() {
        String expected = """\
src/main/java/com/android/test/lint/lintmodel/mylibrary/MyLibrary.java:5: Warning: Assertions are unreliable. Use BuildConfig.DEBUG conditional checks instead. [Assert]
       assert arg > 5;
       ~~~~~~~~~~~~~~
src/main/java/com/android/test/lint/javalib/JavaLib.java:4: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
    public static final String SD_CARD = "/sdcard/something";
                                         ~~~~~~~~~~~~~~~~~~~
0 errors, 2 warnings"""
        def file = new File(project.getSubproject('app').getTestDir(), "lint-results.txt")
        assertThat(file).exists();
        /* Temporarily disabled: seems to pass locally but fail in the PSQ;
           check in test update separately
        assertThat(file).contentWithUnixLineSeparatorsIsExactly(expected);
         */
    }
}
