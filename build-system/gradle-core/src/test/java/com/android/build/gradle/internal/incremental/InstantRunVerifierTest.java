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
import com.verifier.tests.MethodAddedClass;
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
        IncompatibleChange changes = harness.verify(UnchangedClass.class.getName(), "verifier");
        assertNull(changes);
    }

    @Test
    public void testMethodAdded() throws IOException {
        IncompatibleChange changes = harness.verify(MethodAddedClass.class.getName(), "verifier");
        assertEquals(IncompatibleChange.METHOD_ADDED, changes);
    }
}
