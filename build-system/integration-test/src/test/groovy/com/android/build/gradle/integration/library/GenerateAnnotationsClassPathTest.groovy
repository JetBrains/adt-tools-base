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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.assertFalse

@CompileStatic
class GenerateAnnotationsClassPathTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("extractAnnotations")
            .create()

    @BeforeClass
    static public void setUpProject() {
        File use = project.file(
                "src/main/java/com/android/tests/extractannotations/HelloWorld.java")

        use.parentFile.mkdirs()
        use << """
import com.example.helloworld.GeneratedClass;

public class HelloWorld {

    public void go() {
        GeneratedClass genC = new GeneratedClass();
        genC.method();
    }
}"""

        project.getBuildFile().append('''
import com.google.common.base.Joiner

android.libraryVariants.all { variant ->
    def outDir = project.file("$project.buildDir/generated/source/testplugin/$variant.name");
        def task = project.task(
                "generateJavaFromPlugin${variant.name.capitalize()}",
                dependsOn: [variant.mergeResources],
                type: JavaGeneratingTask) {
            outputDirectory = outDir
        }
        variant.registerJavaGeneratingTask(task, outDir)
}

android.testVariants.all { variant ->
    def outDir = project.file("$project.buildDir/generated/source/testplugin/$variant.name");
        def task = project.task(
                "generateJavaFromPlugin${variant.name.capitalize()}",
                type: JavaGeneratingTask) {
            suffix = "AndroidTest"
            outputDirectory = outDir
        }
        variant.registerJavaGeneratingTask(task, outDir)
}

public class JavaGeneratingTask extends DefaultTask {
    @Input
    String suffix = "";

    @OutputDirectory
    File outputDirectory

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        System.err.println('Plugin executed on ' + inputs)
        File outputFile = new File(outputDirectory, Joiner.on(File.separatorChar).join(
                "com", "example", "helloworld", "GeneratedClass${suffix}.java"))
        System.err.println("creating file " + outputFile)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.getParentFile().mkdirs()

        outputFile << """
package com.example.helloworld;

public class GeneratedClass${suffix} {
    public void method() {
        System.out.println("Executed generated method");
    }
}
    """
    }
}


''')
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    /**
     * Check variant.registerJavaGeneratingTask() adds output directory to the class path of
     * the generate annotations task
     */
    @Test
    public void "check javaGeneratingTask adds output dir to generate annotations classpath"() {
        project.execute("clean", "assembleDebug")
        assertFalse(
                "Extract annotation should get generated class on path.",
                project.getStdout().contains("Not extracting annotations (compilation problems encountered)"))
        assertThat(project.file("build/generated/source/testplugin/debug/com/example/helloworld/GeneratedClass.java")).exists()
    }

    @Test
    public void "check generating java class works for test variant"() {
        project.execute("compileDebugAndroidTestSource")
        assertThat(project.file("build/generated/source/testplugin/debugAndroidTest/com/example/helloworld/GeneratedClassAndroidTest.java")).exists()


    }
}
