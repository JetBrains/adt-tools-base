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
import com.example.basic.MultipleMethodInvocations;

import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.logging.Logger;

/**
 * Tests that invoke method that delegates to other methods on "this" or other delegate objects.
 */
public class MultipleMethodInvocationsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void changeMultipleMethodInvocations() throws Exception {

        harness.reset();
        MultipleMethodInvocations testTarget = new MultipleMethodInvocations();
        assertEquals("foo-4-barbar", testTarget.doAll());

        harness.applyPatch("changeBaseClass");
        assertEquals("barfoo-5-bar", testTarget.doAll());
    }
}
