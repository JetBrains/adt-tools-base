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

import static org.junit.Assert.assertEquals;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.AnonymousClasses;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for static anonymous classes.
 * TODO: non static...
 */
public class AnonymousClassesTest {
    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement(false);

    /**
     * Checks that the initial bytecode changes did not prevent proper access to private fields
     * and methods.
     *
     * @throws Exception when Byte code generation failed.
     */
    @Test
    public void checkAnonymousClasses() throws Exception {

        harness.reset();
        assertEquals("first", AnonymousClasses.FIRST.doSomething());
        assertEquals("second", AnonymousClasses.SECOND.doSomething());

        harness.applyPatch("changeBaseClass");
        assertEquals("patched_first", AnonymousClasses.FIRST.doSomething());
        assertEquals("patched_second", AnonymousClasses.SECOND.doSomething());
    }
}
