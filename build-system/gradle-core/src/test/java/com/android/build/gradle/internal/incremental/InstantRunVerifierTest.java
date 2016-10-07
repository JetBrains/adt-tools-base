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

package com.android.build.gradle.internal.incremental;

import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.COMPATIBLE;
import static org.junit.Assert.assertEquals;

import com.android.build.gradle.internal.incremental.fixture.VerifierHarness;
import com.google.common.collect.Lists;
import com.verifier.tests.AddClassAnnotation;
import com.verifier.tests.AddInstanceField;
import com.verifier.tests.AddInterfaceImplementation;
import com.verifier.tests.AddMethodAnnotation;
import com.verifier.tests.AddNotRuntimeClassAnnotation;
import com.verifier.tests.ChangeFieldType;
import com.verifier.tests.ChangeInstanceFieldToStatic;
import com.verifier.tests.ChangeInstanceFieldVisibility;
import com.verifier.tests.ChangeStaticFieldToInstance;
import com.verifier.tests.ChangeStaticFieldVisibility;
import com.verifier.tests.ChangeSuperClass;
import com.verifier.tests.ChangedClassInitializer1;
import com.verifier.tests.ChangedClassInitializer2;
import com.verifier.tests.ChangedClassInitializer3;
import com.verifier.tests.DisabledClassChanging;
import com.verifier.tests.DisabledClassNotChanging;
import com.verifier.tests.DisabledMethodChanging;
import com.verifier.tests.MethodAddedClass;
import com.verifier.tests.MethodCollisionClass;
import com.verifier.tests.NewInstanceReflectionUser;
import com.verifier.tests.R;
import com.verifier.tests.ReflectiveUserNotChanging;
import com.verifier.tests.RemoveClassAnnotation;
import com.verifier.tests.RemoveInterfaceImplementation;
import com.verifier.tests.RemoveMethodAnnotation;
import com.verifier.tests.RemoveNotRuntimeClassAnnotation;
import com.verifier.tests.UnchangedClass;
import com.verifier.tests.UnchangedClassInitializer1;

import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import java.io.IOException;

import Lpackage.AnyClassWithMethodInvocation;

/** Tests for the {@link InstantRunVerifier} */
public class InstantRunVerifierTest {

    VerifierHarness harness = new VerifierHarness(true);

    @Test
    public void testUnchangedClass() throws IOException {
        InstantRunVerifierStatus changes = harness.verify(UnchangedClass.class, "verifier");
        assertEquals(COMPATIBLE,changes);
    }

    @Test
    public void testSuperClassChanged() throws IOException {
        // not changing the super class name should be ok.
        assertEquals(COMPATIBLE, harness.verify(ChangeSuperClass.class, null));
        InstantRunVerifierStatus change = harness.verify(ChangeSuperClass.class, "verifier");
        assertEquals(InstantRunVerifierStatus.PARENT_CLASS_CHANGED, change);
    }

    @Test
    public void testMethodAdded() throws IOException {
        // not adding a method should be ok.
        assertEquals(COMPATIBLE, harness.verify(MethodAddedClass.class, null));
        InstantRunVerifierStatus changes = harness.verify(MethodAddedClass.class, "verifier");
        assertEquals(InstantRunVerifierStatus.METHOD_ADDED, changes);
    }

    @Test
    public void testClassAnnotationAdded() throws IOException {
        // not adding a class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(AddClassAnnotation.class, null));
        InstantRunVerifierStatus changes = harness.verify(AddClassAnnotation.class, "verifier");
        assertEquals(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testClassAnnotationRemoved() throws IOException {
        // not removing a class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(RemoveClassAnnotation.class, null));
        InstantRunVerifierStatus changes = harness.verify(RemoveClassAnnotation.class, "verifier");
        assertEquals(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testNotRuntimeClassAnnotationAdded() throws IOException {
        // not adding a non runtime visible class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(AddNotRuntimeClassAnnotation.class, null));
        // and adding it should still be fine.
        assertEquals(COMPATIBLE, harness.verify(AddNotRuntimeClassAnnotation.class, "verifier"));
    }

    @Test
    public void testNotRuntimeClassAnnotationRemoved() throws IOException {
        // not removing a non runtime visible class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(RemoveNotRuntimeClassAnnotation.class, null));
        // and removing it should still be fine.
        assertEquals(COMPATIBLE, harness.verify(RemoveNotRuntimeClassAnnotation.class, "verifier"));
    }

    @Test
    public void testMethodAnnotationAdded() throws IOException {
        // not adding a method annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(AddMethodAnnotation.class, null));
        InstantRunVerifierStatus changes = harness.verify(AddMethodAnnotation.class, "verifier");
        assertEquals(InstantRunVerifierStatus.METHOD_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testMethodAnnotationRemoved() throws IOException {
        // not removing a class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(RemoveMethodAnnotation.class, null));
        InstantRunVerifierStatus changes = harness.verify(RemoveMethodAnnotation.class, "verifier");
        assertEquals(InstantRunVerifierStatus.METHOD_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testInterfaceImplementationAdded() throws IOException {
        // not removing a class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(AddInterfaceImplementation.class, null));
        InstantRunVerifierStatus changes = harness.verify(AddInterfaceImplementation.class, "verifier");
        assertEquals(InstantRunVerifierStatus.IMPLEMENTED_INTERFACES_CHANGE, changes);
    }

    @Test
    public void testInterfaceImplementationRemoved() throws IOException {
        // not removing a class annotation should be ok.
        assertEquals(COMPATIBLE, harness.verify(RemoveInterfaceImplementation.class, null));
        InstantRunVerifierStatus changes = harness.verify(RemoveInterfaceImplementation.class, "verifier");
        assertEquals(InstantRunVerifierStatus.IMPLEMENTED_INTERFACES_CHANGE, changes);
    }

    @Test
    public void testMethodCollisionRemoved() throws IOException {
        // not adding/removing overloaded methods should be ok.
        assertEquals(COMPATIBLE, harness.verify(MethodCollisionClass.class, null));
        InstantRunVerifierStatus changes = harness.verify(MethodCollisionClass.class, "verifier");
        assertEquals(InstantRunVerifierStatus.METHOD_DELETED, changes);
    }

    @Test
    public void testUnchangedClassInitializer() throws IOException {
        assertEquals(COMPATIBLE, harness.verify(UnchangedClassInitializer1.class, null));
        assertEquals(COMPATIBLE, harness.verify(UnchangedClassInitializer1.class, "verifier"));
        assertEquals(
                COMPATIBLE, harness.verify(UnchangedClassInitializer1.class, "lineChangingVerifier"));
    }

    @Test
    public void testChangedClassInitializer() throws IOException {
        assertEquals(InstantRunVerifierStatus.STATIC_INITIALIZER_CHANGE,
                harness.verify(ChangedClassInitializer1.class, "verifier"));
        assertEquals(InstantRunVerifierStatus.STATIC_INITIALIZER_CHANGE,
                harness.verify(ChangedClassInitializer2.class, "verifier"));
        assertEquals(InstantRunVerifierStatus.STATIC_INITIALIZER_CHANGE,
                harness.verify(ChangedClassInitializer3.class, "verifier"));
    }

    @Test
    public void testAddingInstanceField() throws IOException {
        // not adding an instance field should be ok.
        assertEquals(COMPATIBLE, harness.verify(AddInstanceField.class, null));
        assertEquals(InstantRunVerifierStatus.FIELD_ADDED,
                harness.verify(AddInstanceField.class, "verifier"));
    }

    @Test
    public void testChangingAnInstanceFieldIntoStatic() throws IOException {
        // not changing anything in an instance field should be ok.
        assertEquals(COMPATIBLE, harness.verify(ChangeInstanceFieldToStatic.class, null));
        assertEquals(InstantRunVerifierStatus.FIELD_TYPE_CHANGE,
                harness.verify(ChangeInstanceFieldToStatic.class, "verifier"));
    }

    @Test
    public void testChangingStaticFieldIntoAnInstance() throws IOException {
        // not changing anything in a static field should be ok.
        assertEquals(COMPATIBLE, harness.verify(ChangeStaticFieldToInstance.class, null));
        assertEquals(InstantRunVerifierStatus.FIELD_TYPE_CHANGE,
                harness.verify(ChangeStaticFieldToInstance.class, "verifier"));
    }

    @Test
    public void testChangingFieldType() throws IOException {
        // not changing a field type should be ok.
        assertEquals(COMPATIBLE, harness.verify(ChangeFieldType.class, null));
        assertEquals(InstantRunVerifierStatus.FIELD_TYPE_CHANGE,
                harness.verify(ChangeFieldType.class, "verifier"));
    }

    @Test
    public void testChangingInstanceFieldVisibility() throws IOException {
        // not changing a field type should be ok.
        assertEquals(COMPATIBLE, harness.verify(ChangeInstanceFieldVisibility.class, null));
        assertEquals(InstantRunVerifierStatus.FIELD_TYPE_CHANGE, harness.verify(ChangeInstanceFieldVisibility.class, "verifier"));
    }

    @Test
    public void testChangingStaticFieldVisibility() throws IOException {
        // not changing a field type should be ok.
        assertEquals(COMPATIBLE, harness.verify(ChangeStaticFieldVisibility.class, null));
        assertEquals(InstantRunVerifierStatus.FIELD_TYPE_CHANGE, harness.verify(ChangeStaticFieldVisibility.class, "verifier"));
    }

    @Test
    public void testClassNewInstanceReflectionUser() throws IOException {
        // not changing a method implementation that uses reflection should be ok.
        assertEquals(COMPATIBLE, harness.verify(NewInstanceReflectionUser.class, null));
        assertEquals(InstantRunVerifierStatus.REFLECTION_USED,
                harness.verify(NewInstanceReflectionUser.class, "verifier"));
    }

    @Test
    public void testReflectiveUserNotChanging() throws IOException {
        // not changing a method implementation that uses reflection should be ok.
        assertEquals(COMPATIBLE,
                harness.verify(ReflectiveUserNotChanging.class, null));
        // changing other methods should be fine.
        assertEquals(COMPATIBLE,
                harness.verify(ReflectiveUserNotChanging.class, "verifier"));
    }

    @Test
    public void testDisabledClassNotChanging() throws IOException {
        // even though nothing changed, the verifier will flag it as a new version is available.
        assertEquals(InstantRunVerifierStatus.INSTANT_RUN_DISABLED,
                harness.verify(DisabledClassNotChanging.class, null));
    }

    @Test
    public void testDisabledClassChanging() throws IOException {
        // not changing a method implementation from a disabled class should be ok.
        //assertEquals(InstantRunVerifierStatus.COMPATIBLE, harness.verify(DisabledClassChanging.class, null));
        // changing a method implementation from a disabled class should be flagged.
        assertEquals(InstantRunVerifierStatus.INSTANT_RUN_DISABLED,
                harness.verify(DisabledClassChanging.class, "verifier"));
    }

    @Test
    public void testDisabledMethodChanging() throws IOException {
        // not changing a method implementation from a disabled class should be ok.
        assertEquals(COMPATIBLE,
                harness.verify(DisabledMethodChanging.class, null));
        // changing a method implementation from a disabled class should be flagged.
        assertEquals(InstantRunVerifierStatus.INSTANT_RUN_DISABLED,
                harness.verify(DisabledMethodChanging.class, "verifier"));
    }

    @Test
    public void testRClassSpecialCase() throws IOException {
        assertEquals(COMPATIBLE, harness.verify(R.class, null));
        assertEquals(InstantRunVerifierStatus.R_CLASS_CHANGE,
                harness.verify(R.id.class, "verifier"));
    }

    @Test
    public void testDiffListOnAnnotationNodes() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        original.values = Lists.newArrayList("modules", Type.getObjectType("test/DaggerModule"));

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        updated.values = Lists.newArrayList("modules", Type.getObjectType("test/DaggerModule"));

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testDiffListOnAnnotationNodesEntriesDataSwap() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        original.visit("entry1", "data1");
        original.visit("entry2", "data2");

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        updated.visit("entry1", "data2");
        updated.visit("entry2", "data1");

        assertEquals(
                InstantRunVerifier.Diff.CHANGE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testDiffListOnAnnotationNodesDiffEntriesOrder() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        original.visit("entry1", "data1");
        original.visit("entry2", "data2");

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        updated.visit("entry2", "data2");
        updated.visit("entry1", "data1");

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testDiffListOnAnnotationNodesWithEnumEntryValue() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        original.visitEnum("entry", "Test", "LMyEnum");

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        updated.visitEnum("entry", "Test", "LMyEnum");

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testDiffListOnAnnotationNodesWithAnnotationNodeValue() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        AnnotationVisitor newAnnotation = original.visitAnnotation("InnerAnnotation", "LTest;");
        newAnnotation.visit("innerEntry", "innerValue");
        newAnnotation.visitEnd();

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        newAnnotation = updated.visitAnnotation("InnerAnnotation", "LTest;");
        newAnnotation.visit("innerEntry", "innerValue");
        newAnnotation.visitEnd();

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }


    @Test
    public void testDiffListOnAnnotationNodesWithArrayValues() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        original.visit("byteArray", new byte[] {1, 2, 3});
        original.visit("booleanArray", new boolean[] {false, true, false});
        original.visit("shortArray", new short[] {1, 2, 3});
        original.visit("charArray", new char[] {'a', 'b', 'c'});
        original.visit("intArray", new int[] {1, 2, 3});
        original.visit("longArray", new long[] {1, 2, 3});
        original.visit("floatArray", new float[] {1, 2, 3});
        original.visit("doubleArray", new double[] {1, 2, 3});
        original.visit("stringArray", new String[] {"1", "2", "3"});

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        updated.visit("byteArray", new byte[] {1, 2, 3});
        updated.visit("booleanArray", new boolean[] {false, true, false});
        updated.visit("shortArray", new short[] {1, 2, 3});
        updated.visit("charArray", new char[] {'a', 'b', 'c'});
        updated.visit("intArray", new int[] {1, 2, 3});
        updated.visit("longArray", new long[] {1, 2, 3});
        updated.visit("floatArray", new float[] {1, 2, 3});
        updated.visit("doubleArray", new double[] {1, 2, 3});
        updated.visit("stringArray", new String[] {"1", "2", "3"});

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testDiffListOnAnnotationNodesWithAnnotationArrayValue() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");

        AnnotationVisitor arrayVisitor = original.visitArray("test");
        AnnotationVisitor newAnnotation = arrayVisitor.visitAnnotation("InnerAnnotation", "LTest;");
        newAnnotation.visit("innerEntry", "innerValue");
        newAnnotation.visitEnd();

        newAnnotation = arrayVisitor.visitAnnotation("InnerAnnotation", "LTest;");
        newAnnotation.visit("innerEntry", "innerValue");
        newAnnotation.visitEnd();
        arrayVisitor.visitEnd();

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        arrayVisitor = updated.visitArray("test");
        newAnnotation = arrayVisitor.visitAnnotation("InnerAnnotation", "LTest;");
        newAnnotation.visit("innerEntry", "innerValue");
        newAnnotation.visitEnd();

        newAnnotation = arrayVisitor.visitAnnotation("InnerAnnotation", "LTest;");
        newAnnotation.visit("innerEntry", "innerValue");
        newAnnotation.visitEnd();
        arrayVisitor.visitEnd();

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testDiffListOnAnnotationNodesWithIntArrayValue() throws Exception {
        AnnotationNode original = new AnnotationNode("Ltest/SomeAnnotation;");
        AnnotationVisitor test = original.visitArray("test");
        test.visit("stub", 1);
        test.visit("stub", 2);
        test.visit("stub", 3);
        test.visitEnd();

        AnnotationNode updated = new AnnotationNode("Ltest/SomeAnnotation;");
        test = updated.visitArray("test");
        test.visit("stub", 1);
        test.visit("stub", 2);
        test.visit("stub", 3);
        test.visitEnd();

        assertEquals(
                InstantRunVerifier.Diff.NONE,
                InstantRunVerifier.diffList(
                        Lists.newArrayList(original),
                        Lists.newArrayList(updated),
                        InstantRunVerifier.ANNOTATION_COMPARATOR));
    }

    @Test
    public void testClassWithObjectTypePrefix() throws IOException {
        InstantRunVerifierStatus changes = harness.verify(AnyClassWithMethodInvocation.class, null);
        assertEquals(COMPATIBLE, changes);
    }
}
