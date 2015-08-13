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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.truth.ZipFileSubject
import com.android.builder.model.AndroidProject
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

import java.util.jar.JarFile

import static com.google.common.truth.Truth.assertThat
/**
 * Assemble tests for minify.
 */
@CompileStatic
class MinifyTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minify")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "assembleMinified",
                "assembleMinifiedAndroidTest", "jarDebugClasses")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }

    @Test
    void 'App APK is minified'() throws Exception {
        JarFile minifiedJar = new JarFile(project.file(
                "build/$AndroidProject.FD_INTERMEDIATES/classes-proguard/minified/classes.jar"))

        def appClassFiles = minifiedJar.entries().toSet().collect { it.name }
        // Ignore JaCoCo stuff.
        appClassFiles.removeAll { it =~ /org.jacoco/ }
        appClassFiles.removeAll(["about.html", "com/vladium/emma/rt/RT.class"])

        assertThat(appClassFiles).containsExactly(
                "com/android/tests/basic/a.class", // Renamed StringProvider.
                "com/android/tests/basic/Main.class",
                "com/android/tests/basic/IndirectlyReferencedClass.class", // Kept by ProGuard rules.
                // No entry for UnusedClass, it gets removed.
        )
    }

    @Test
    void 'Test APK is not minified, but mappings are applied'() throws Exception {
        JarFile minifiedJar = new JarFile(project.file(
                "build/$AndroidProject.FD_INTERMEDIATES/classes-proguard/androidTest/minified/classes.jar"))

        def testClassFiles = minifiedJar.entries()
                .toSet()
                .collect { it.name }
                .findAll { !it.startsWith("org/hamcrest") }

        assertThat(testClassFiles).containsExactly(
                "LICENSE.txt", // Comes from hamcrest, is not packaged.
                "com/android/tests/basic/MainTest.class",
                "com/android/tests/basic/UnusedTestClass.class",
                "com/android/tests/basic/UsedTestClass.class",
                "com/android/tests/basic/test/BuildConfig.class",
        )

        checkClassFile(minifiedJar)
    }

    @Test
    void 'Test classes.jar is present for non Jack enabled variants'() throws Exception {
        ZipFileSubject classes = TruthHelper.assertThatZip(project.file(
                "build/$AndroidProject.FD_INTERMEDIATES/packaged/debug/classes.jar"))

        classes.contains("com/android/tests/basic/Main.class")
        classes.doesNotContain("com/android/tests/basic/MainTest.class")
    }

    @CompileDynamic
    static def checkClassFile(JarFile minifiedJar) {
        def mainTestBytes = minifiedJar.getInputStream(
                minifiedJar.getEntry("com/android/tests/basic/MainTest.class"))
        def classReader = new ClassReader(mainTestBytes)
        def mainTestClassNode = new ClassNode(Opcodes.ASM5)
        classReader.accept(mainTestClassNode, 0)

        // Make sure bytecode got rewritten to point to renamed classes.
        FieldNode stringProviderField = mainTestClassNode.fields.find { it.name == "stringProvider" }
        assert Type.getType(stringProviderField.desc).className == "com.android.tests.basic.a"
    }
}
