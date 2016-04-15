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

package com.android.build.gradle.internal.incremental.hotswap;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.Constructors;
import com.google.common.collect.ImmutableList;
import com.jasmin.VariableBeforeSuper;

import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConstructorTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void updateConstructor() throws Exception {
        harness.reset();

        Constructors.Sub sub = new Constructors.Sub(1.2, ".3.", 4);
        assertEquals("base:1.2[.3.]4", sub.getBaseFinal());
        assertEquals("sub:1.2.3.4", sub.getSubFinal());
        assertEquals("Sub(double, String, int)", sub.value);

        sub = new Constructors.Sub(1234L, 0.5f);
        assertEquals("base:0.5[[1234]]0", sub.getBaseFinal());
        assertEquals("sub:0.5[1234]0", sub.getSubFinal());
        assertEquals("Sub(long, float)", sub.value);

        sub = new Constructors.Sub(10, 20, 30);
        assertEquals("base:2.1[220]30", sub.getBaseFinal());
        assertEquals("sub:2.122030", sub.getSubFinal());
        assertEquals("Sub(2, 20, 30)", sub.value);

        Constructors outer = new Constructors("outer");
        Constructors.DupInvokeSpecialSub dup = outer.new DupInvokeSpecialSub();
        assertEquals("original", dup.value);
        assertEquals("outer", outer.value);

        harness.applyPatch("changeBaseClass");
        sub = new Constructors.Sub(1.2, ".3.", 4);
        assertEquals("1.2(.3.)4:patched_base", sub.getBaseFinal());
        assertEquals("1.2.3.4:patched_sub", sub.getSubFinal());
        assertEquals("patched_Sub(double, String, int)", sub.value);

        sub = new Constructors.Sub(1234L, 0.5f);
        assertEquals("0.5((1235*))0:patched_base", sub.getBaseFinal());
        assertEquals("0.5(1235*)0:patched_sub", sub.getSubFinal());
        assertEquals("patched_Sub(long, float)", sub.value);

        sub = new Constructors.Sub(10, 20, 30);
        assertEquals("9.1(920)30:patched_base", sub.getBaseFinal());
        assertEquals("9.192030:patched_sub", sub.getSubFinal());
        assertEquals("Sub(9, 20, 30)", sub.value);

        outer = new Constructors("outer_patched");
        dup = outer.new DupInvokeSpecialSub();
        assertEquals("patched", dup.value);
        assertEquals("outer_patched", outer.value);
    }

    @Test
    public void updateConstructorSuperCall() throws Exception {
        harness.reset();
        Constructors.Sub sub = new Constructors.Sub(10, 20, 30, 40);
        assertEquals("base:", sub.getBaseFinal());
        assertEquals(":sub", sub.getSubFinal());
        assertEquals("Sub(int, int, int, int)", sub.value);

        harness.reset();
        harness.applyPatch("changeBaseClass");
        sub = new Constructors.Sub(10, 20, 30, 40);
        assertEquals("3.020343:patched_base", sub.getBaseFinal());
        assertEquals(":patched_sub", sub.getSubFinal());
        assertEquals("patched_Sub(int, int, int, int)3", sub.value);
    }

    @Test
    public void updateConstructorSuperCallInAbstractBase() throws Exception {
        harness.reset();
        Constructors.SubOfAbstract sub = new Constructors.SubOfAbstract(10, 20, 30, 40);
        assertEquals("abstract base:", sub.getBaseFinal());
        assertEquals(":sub_abstract", sub.getSubFinal());
        assertEquals("SubOfAbstract(int, int, int, int)", sub.value);

        harness.reset();
        harness.applyPatch("changeBaseClass");
        sub = new Constructors.SubOfAbstract(10, 20, 30, 40);
        assertEquals("patched_abstract_base:30.0203070", sub.getBaseFinal());
        assertEquals(":patched_sub_abstract", sub.getSubFinal());
        assertEquals("patched_SubOfAbstract(int, int, int, int)", sub.value);
    }

    @Test
    public void testExceptionsInConstructor()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {

        harness.reset();
        try {
            Constructors.Sub sub = new Constructors.Sub();
            fail("iae expected");
        } catch(IllegalArgumentException e) {
            assertEquals("pass me a string !", e.getMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub("sub", true);
            fail("iae expected");
        } catch(IllegalArgumentException e) {
            assertEquals("iae overflow", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub("sub", "expected", true);
            fail("iae expected");
        } catch(IllegalArgumentException e) {
            assertEquals("expected overflow", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub("sub", true, "expected");
            fail("RuntimeException expected");
        } catch(RuntimeException e) {
            assertEquals("expected iae overflow", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(ImmutableList.of("one", "two"), true);
            fail("RuntimeException expected");
        } catch(IllegalArgumentException e) {
            assertEquals("two overflow", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(ImmutableList.of("one", "two"), false);
            assertEquals("success", sub.getSubFinal());
        } catch(IllegalArgumentException e) {
            fail("unexpected " + e);
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(true, ImmutableList.of("one", "two"));
            fail("RuntimeException expected");
        } catch(RuntimeException e) {
            assertEquals("two", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(false, ImmutableList.of("one", "two"));
            assertEquals("success", sub.getSubFinal());
        } catch(Exception e) {
            fail("unexpected " + e);
        }

        Constructors.Sub orginalSub = new Constructors.Sub("sub", false);
        assertEquals("base:1.0sub2", orginalSub.getBaseFinal());

        harness.applyPatch("changeBaseClass");
        try {
            Constructors.Sub sub = new Constructors.Sub();
            fail("iae expected");
        } catch(IllegalArgumentException e) {
            assertEquals("pass me an updated string !", e.getMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub("sub", false);
            fail("iae expected");
        } catch(IllegalArgumentException e) {
            assertEquals("updated iae underflow", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub("sub", "expected", false);
            fail("iae expected");
        } catch(IllegalArgumentException e) {
            assertEquals("expected underflow", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub("sub", false, "expected");
            fail("RuntimeException expected");
        } catch(RuntimeException e) {
            assertEquals("updated iae underflow expected", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(ImmutableList.of("un", "deux"), false);
            fail("RuntimeException expected");
        } catch(RuntimeException e) {
            assertEquals("underflow deux", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(ImmutableList.of("un", "deux"), true);
            assertEquals("updated subFinal", sub.getSubFinal());
        } catch(IllegalArgumentException e) {
            fail("unexpected " + e);
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(true, ImmutableList.of("one", "two"));
            fail("RuntimeException expected");
        } catch(RuntimeException e) {
            assertEquals("updated two", e.getLocalizedMessage());
        }

        try {
            Constructors.Sub sub = new Constructors.Sub(false, ImmutableList.of("one", "two"));
            assertEquals("updated success", sub.getSubFinal());
        } catch(Exception e) {
            fail("unexpected " + e);
        }

        Constructors.Sub sub = new Constructors.Sub("sub", true);
        assertEquals("10.0sub20:patched_base", sub.getBaseFinal());
    }

    @Test
    public void patchPublicConstructorWhichCallsAPrivateConstructor() throws Exception {
        harness.reset();
        Constructors.PrivateConstructor p = new Constructors.PrivateConstructor();
        assertEquals("Public constructor calls private constructor.", "Base", p.getString());
        harness.applyPatch("changeBaseClass");
        p = new Constructors.PrivateConstructor();
        assertEquals("Public constructor calls private constructor.", "Patched", p.getString());
    }

    @Test
    public void variablesBeforeSuperCall() throws Exception {
        harness.reset();
        // Jasmin class is compiled from VariableBeforeSuper.j
        VariableBeforeSuper object = new VariableBeforeSuper(1, 2.0);
        assertEquals(6, object.x);
        assertEquals(2.0, object.y, 0.01);
        harness.applyPatch("changeBaseClass");
        object = new VariableBeforeSuper(1, 2.0);
        assertEquals(53, object.x);
        assertEquals(2.0, object.y, 0.01);
    }
}
