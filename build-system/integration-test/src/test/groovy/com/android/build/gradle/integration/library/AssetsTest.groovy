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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.RunGradleTasks
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.google.common.io.Files
import com.google.common.io.Resources
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.nio.charset.Charset

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.builder.core.BuilderConstants.DEBUG

/**
 * Assemble tests for assets.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class AssetsTest {
    byte[] simpleJarDataA
    byte[] simpleJarDataB
    byte[] simpleJarDataC
    byte[] simpleJarDataD
    File assetsDir;
    File resRawDir;
    File resourcesDir;
    File libsDir;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return RunGradleTasks.Packaging.getParameters();
    }

    @Parameterized.Parameter
    public RunGradleTasks.Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("assets")
            .create()

    private void execute(String... tasks) {
        project.executor().withPackaging(mPackaging).run(tasks)
    }

    @Before
    public void setUp() {
        simpleJarDataA = Resources.toByteArray(Resources.getResource(AssetsTest.class,
                "/jars/simple-jar-with-A_DoIExist-class.jar"))
        simpleJarDataB = Resources.toByteArray(Resources.getResource(AssetsTest.class,
                "/jars/simple-jar-with-B_DoIExist-class.jar"))
        simpleJarDataC = Resources.toByteArray(Resources.getResource(AssetsTest.class,
                "/jars/simple-jar-with-C_DoIExist-class.jar"))
        simpleJarDataD = Resources.toByteArray(Resources.getResource(AssetsTest.class,
                "/jars/simple-jar-with-D_DoIExist-class.jar"))

        // Make directories where we will place jars.
        assetsDir = project.file("lib/src/main/assets")
        assetsDir.mkdirs()
        resRawDir = project.file("lib/src/main/res/raw")
        resRawDir.mkdirs()
        resourcesDir = project.file("lib/src/main/resources")
        resourcesDir.mkdirs()
        libsDir = project.file("lib/libs")
        libsDir.mkdirs()

        // Add the libs dependency in the library build file.
        Files.append("\ndependencies {\ncompile fileTree(dir: 'libs', include: '*.jar')\n}\n"
                .replaceAll("\n", System.getProperty("line.separator")),
                project.file("lib/build.gradle"), Charset.defaultCharset());

        // Create some jars.
        Files.write(simpleJarDataA, new File(libsDir, "a1.jar"))
        Files.write(simpleJarDataB, new File(assetsDir, "b1.jar"))
        Files.write(simpleJarDataC, new File(resourcesDir, "c1.jar"))
        Files.write(simpleJarDataD, new File(resRawDir, "d1.jar"))

        // Run the project.
        execute("clean", "assembleDebug")
    }

    @Test
    void checkJarLocations() {
        // Obtain the apk file.
        File apk = project.getSubproject("app").getApk(DEBUG);
        assertThat(apk).isNotNull()

        // a1.jar was placed in libs so it should have been merged into the dex.
        assertThatApk(apk).doesNotContain("jars/a1.jar");
        assertThatApk(apk).containsClass("LA_DoIExist;");

        // b1.jar was placed into assets and should be in assets.
        assertThatApk(apk).containsFileWithContent("assets/b1.jar", simpleJarDataB);
        assertThatApk(apk).doesNotContainClass("LB_DoIExist;");

        // c1.jar was placed into resources and should be in the root.
        assertThatApk(apk).containsFileWithContent("c1.jar", simpleJarDataC);
        assertThatApk(apk).doesNotContainClass("LC_DoIExist;");

        // d1.jar was placed into res/raw and should be in the root.
        assertThatApk(apk).containsFileWithContent("res/raw/d1.jar", simpleJarDataD);
        assertThatApk(apk).doesNotContainClass("LD_DoIExist;");
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executor().withPackaging(mPackaging).executeConnectedCheck()
    }
}
