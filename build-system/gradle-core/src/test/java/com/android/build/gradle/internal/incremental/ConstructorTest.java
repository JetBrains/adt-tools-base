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

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.Constructors;

import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ConstructorTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void updateConstructor() throws Exception {
        harness.reset();

        Constructors.Sub sub = new Constructors.Sub(1.2, ".3.", 4);
        assertEquals("base:1.2.3.4", sub.getBaseFinal());
        assertEquals("sub:1.2.3.4", sub.getSubFinal());

        harness.applyPatch("changeBaseClass");
        sub = new Constructors.Sub(1.2, ".3.", 4);
        assertEquals("1.2.3.4:patched_base", sub.getBaseFinal());
        assertEquals("1.2.3.4:patched_sub", sub.getSubFinal());

    }
}
