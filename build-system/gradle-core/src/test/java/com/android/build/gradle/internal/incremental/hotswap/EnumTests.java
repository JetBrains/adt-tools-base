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
import com.example.basic.Enums;

import org.junit.ClassRule;
import org.junit.Test;

public class EnumTests {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void testEnums() throws Exception {
        harness.reset();

        assertEquals("zero", Enums.VALUE_0.getValue());
        assertEquals("overriden:one:other", Enums.VALUE_1.getValue());

        harness.applyPatch("changeBaseClass");

        assertEquals("zero:patched", Enums.VALUE_0.getValue());
        assertEquals("patched+overriden:one:patched:other+patched", Enums.VALUE_1.getValue());
    }
}
