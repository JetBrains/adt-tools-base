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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.build.gradle.internal.incremental.fixture.VerifierHarness;
import com.verifier.tests.AddClassAnnotation;
import com.verifier.tests.AddInterfaceImplementation;
import com.verifier.tests.AddMethodAnnotation;
import com.verifier.tests.ChangeSuperClass;
import com.verifier.tests.MethodAddedClass;
import com.verifier.tests.RemoveClassAnnotation;
import com.verifier.tests.RemoveInterfaceImplementation;
import com.verifier.tests.RemoveMethodAnnotation;
import com.verifier.tests.UnchangedClass;

import org.junit.Test;

import java.io.IOException;

/**
 * Tests for the {@link InstantRunVerifier}
 */
public class InstantRunVerifierTest {

    VerifierHarness harness = new VerifierHarness(true);

    @Test
    public void testUnchangedClass() throws IOException {
        IncompatibleChange changes = harness.verify(UnchangedClass.class, "verifier");
        assertNull(changes);
    }

    @Test
    public void testSuperClassChanged() throws IOException {
        // not changing the super class name should be ok.
        assertNull(harness.verify(ChangeSuperClass.class, null));
        IncompatibleChange change = harness.verify(ChangeSuperClass.class, "verifier");
        assertEquals(IncompatibleChange.PARENT_CLASS_CHANGED, change);
    }

    @Test
    public void testMethodAdded() throws IOException {
        // not adding a method should be ok.
        assertNull(harness.verify(MethodAddedClass.class, null));
        IncompatibleChange changes = harness.verify(MethodAddedClass.class, "verifier");
        assertEquals(IncompatibleChange.METHOD_ADDED, changes);
    }

    @Test
    public void testClassAnnotationAdded() throws IOException {
        // not adding a class annotation should be ok.
        assertNull(harness.verify(AddClassAnnotation.class, null));
        IncompatibleChange changes = harness.verify(AddClassAnnotation.class, "verifier");
        assertEquals(IncompatibleChange.CLASS_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testClassAnnotationRemoved() throws IOException {
        // not removing a class annotation should be ok.
        assertNull(harness.verify(RemoveClassAnnotation.class, null));
        IncompatibleChange changes = harness.verify(RemoveClassAnnotation.class, "verifier");
        assertEquals(IncompatibleChange.CLASS_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testMethodAnnotationAdded() throws IOException {
        // not adding a method annotation should be ok.
        assertNull(harness.verify(AddMethodAnnotation.class, null));
        IncompatibleChange changes = harness.verify(AddMethodAnnotation.class, "verifier");
        assertEquals(IncompatibleChange.METHOD_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testMethodAnnotationRemoved() throws IOException {
        // not removing a class annotation should be ok.
        assertNull(harness.verify(RemoveMethodAnnotation.class, null));
        IncompatibleChange changes = harness.verify(RemoveMethodAnnotation.class, "verifier");
        assertEquals(IncompatibleChange.METHOD_ANNOTATION_CHANGE, changes);
    }

    @Test
    public void testInterfaceImplementationAdded() throws IOException {
        // not removing a class annotation should be ok.
        assertNull(harness.verify(AddInterfaceImplementation.class, null));
        IncompatibleChange changes = harness.verify(AddInterfaceImplementation.class, "verifier");
        assertEquals(IncompatibleChange.IMPLEMENTED_INTERFACES_CHANGE, changes);
    }

    @Test
    public void testInterfaceImplementationRemoved() throws IOException {
        // not removing a class annotation should be ok.
        assertNull(harness.verify(RemoveInterfaceImplementation.class, null));
        IncompatibleChange changes = harness.verify(RemoveInterfaceImplementation.class, "verifier");
        assertEquals(IncompatibleChange.IMPLEMENTED_INTERFACES_CHANGE, changes);
    }
}
