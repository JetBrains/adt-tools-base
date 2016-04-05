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

package com.android.build.gradle.integration.library
import com.android.annotations.NonNull
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import com.google.common.collect.Iterators
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
/**
 * Test for the jarjar integration.
 */
@CompileStatic
public class JarJarLibTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("jarjarIntegrationLib")
            .create()

    @Before
    void setUp() {
    }

    @After
    void cleanUp() {
        project = null
    }

    @Test
    void "check repackaged gson library"() {
        project.getBuildFile() << """
android {
    registerTransform(new com.android.test.jarjar.JarJarTransform(false /*broken transform*/))
}
"""

        AndroidProject model = project.executeAndReturnModel("clean", "assembleDebug")

        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 2, variants.size())

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainArtifact)

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs()
        assertNotNull(debugOutputs)
        assertEquals(1, debugOutputs.size())

        // make sure the Gson library has been renamed and the original one is not present.
        File outputFile = Iterators.getOnlyElement(debugOutputs.iterator()).mainOutputFile.
                getOutputFile()
        assertThatAar(outputFile).containsClass('Lcom/android/tests/basic/Main;');

        // libraries do not include their dependencies unless they are local (which is not
        // the case here), so neither versions of Gson should be present here).
        assertThatAar(outputFile).doesNotContainClass("Lcom/google/repacked/gson/Gson;");
        assertThatAar(outputFile).doesNotContainClass("Lcom/google/gson/Gson;")

        // check we do not have the R class of the library in there.
        assertThatAar(outputFile).doesNotContainClass('Lcom/android/tests/basic/R;')
        assertThatAar(outputFile).doesNotContainClass('Lcom/android/tests/basic/R$drawable;')

        // check the content of the Main class.
        File jarFile = project.file(
                "build/" +
                        "$AndroidProject.FD_INTERMEDIATES/" +
                        "bundles/" +
                        "debug/" +
                        "classes.jar")
        checkClassFile(jarFile)
    }

    @Test
    void "check broken transform"() {
        project.getBuildFile() << """
android {
    registerTransform(new com.android.test.jarjar.JarJarTransform(true /*broken transform*/))
}
"""

        AndroidProject model = project.getSingleModelIgnoringSyncIssues()

        assertThat(model).hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_GENERIC)
    }

    @CompileDynamic
    static def checkClassFile(@NonNull File jarFile) {
        ZipFile zipFile = new ZipFile(jarFile);
        try {
            ZipEntry entry = zipFile.getEntry("com/android/tests/basic/Main.class")
            assertThat(entry).named("Main.class entry").isNotNull()
            ClassReader classReader = new ClassReader(zipFile.getInputStream(entry))
            ClassNode mainTestClassNode = new ClassNode(Opcodes.ASM5)
            classReader.accept(mainTestClassNode, 0)

            // Make sure bytecode got rewritten to point to renamed classes.

            // search for the onCrate method.
            //noinspection GroovyUncheckedAssignmentOfMemberOfRawType
            MethodNode onCreateMethod = mainTestClassNode.methods.find { it.name == "onCreate" };
            assertThat(onCreateMethod).named("onCreate method").isNotNull();

            // find the new instruction, and verify it's using the repackaged Gson.
            TypeInsnNode newInstruction = null;
            int count = onCreateMethod.instructions.size()
            for (int i = 0; i < count ; i++) {
                AbstractInsnNode instruction = onCreateMethod.instructions.get(i);
                if (instruction.opcode == Opcodes.NEW) {
                    newInstruction = (TypeInsnNode) instruction;
                    break;
                }
            }

            assertThat(newInstruction).named("new instruction").isNotNull();
            assertThat(newInstruction.desc).isEqualTo("com/google/repacked/gson/Gson");

        } finally {
            zipFile.close();
        }
    }

}
