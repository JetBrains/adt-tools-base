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
import static org.junit.Assert.fail;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.Exceptions;

import org.junit.ClassRule;
import org.junit.Test;

public class ExceptionsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void testExceptions() throws Exception {
        harness.reset();

        Exceptions exceptions = new Exceptions();
        try {
            exceptions.throwsNamed();
            fail();
        } catch (Exceptions.MyException e) {
            assertEquals("original", e.message);
        }
        assertEquals("before:caught[original]:finally", exceptions.catchesNamed());
        assertEquals("caught: ,protected,static,ctr", exceptions.catchesHiddenNamed());

        try {
            exceptions.throwsRuntime();
            fail();
        } catch (RuntimeException e) {
            assertEquals("original", e.getMessage());
        }
        assertEquals("before:caught[original]:finally", exceptions.catchesRuntime());

        harness.applyPatch("changeBaseClass");

        try {
            exceptions.throwsNamed();
            fail();
        } catch (Exceptions.MyException e) {
            assertEquals("patched", e.message);
        }
        assertEquals("before_p:caught_p[patched]:finally_p", exceptions.catchesNamed());

        // disabled due to constructor use.
        //assertEquals("caught_p: ;protected_p;static_p;ctr_p", exceptions.catchesHiddenNamed());

        try {
            exceptions.throwsRuntime();
            fail();
        } catch (RuntimeException e) {
            assertEquals("patched", e.getMessage());
        }
        assertEquals("before_p:caught_p[patched]:finally_p", exceptions.catchesRuntime());

    }
}
