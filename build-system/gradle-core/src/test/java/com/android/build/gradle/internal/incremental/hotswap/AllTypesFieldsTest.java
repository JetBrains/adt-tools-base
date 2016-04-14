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
import static org.junit.Assert.assertFalse;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.AllTypesFields;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for private fields accesses.
 */
public class AllTypesFieldsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement(false);

    /**
     * Checks that the initial bytecode changes did not prevent proper access to private fields
     * and methods.
     *
     * @throws Exception when Byte code generation failed.
     */
    @Test
    public void checkInitialByteCodeChanges() throws Exception {

        harness.reset();
        AllTypesFields allTypesFields = new AllTypesFields();

        allTypesFields.setPrivateBooleanField(false);
        assertFalse(allTypesFields.getPrivateBooleanField());

        allTypesFields.setPrivateDoubleField(1354.43d);
        assertEquals(1354.43d, allTypesFields.getPrivateDoubleField(), 0d);

        // more to come...
    }

}
