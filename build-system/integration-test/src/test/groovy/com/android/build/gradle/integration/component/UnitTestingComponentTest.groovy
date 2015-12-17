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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.category.SmokeTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Unit tests for component plugin.
 */
@Category(SmokeTests.class)
class UnitTestingComponentTest {
    static AndroidTestApp app = HelloWorldApp.noBuildFile();
    static {
        app.addFile(new TestSourceFile("src/test/java/com/android/tests", "UnitTest.java", """
package com.android.tests;

import static org.junit.Assert.*;

import android.util.ArrayMap;
import android.os.Debug;
import org.junit.Test;

public class UnitTest {
    @Test
    public void defaultValues() {
        ArrayMap map = new ArrayMap();

        // Check different return types.
        map.clear();
        assertEquals(0, map.size());
        assertEquals(false, map.isEmpty());
        assertNull(map.keySet());

        // Check a static method as well.
        assertEquals(0, Debug.getGlobalAllocCount());
    }
}
"""))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(app)
            .forExperimentalPlugin(true)
            .withoutNdk()
            .create();

    @BeforeClass
    public static void setUp() {
        project.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        // We need an android.jar that contains Java 6 bytecode, since Jenkins runs on Java 6.
        compileSdkVersion = $GradleTestProject.LAST_JAVA6_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

        testOptions.unitTests.returnDefaultValues = true
    }
}

dependencies {
    testCompile 'junit:junit:4.12'
}
"""
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    public void testDebug() {
        project.execute("clean", "testDebug")
        assertThat(project.file("build/test-results/debug/TEST-com.android.tests.UnitTest.xml")).exists()
    }
}
