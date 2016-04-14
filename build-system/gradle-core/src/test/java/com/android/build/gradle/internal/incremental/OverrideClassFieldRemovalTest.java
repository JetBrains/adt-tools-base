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

import static org.junit.Assert.assertTrue;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.ClassWithFields;

import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Test to ensure that all original class fields are removed in the $override class.
 */
public class OverrideClassFieldRemovalTest {

    /**
     * this is more complicated than it should be. Our tests have jacoco injected so our
     * .class files contain some jacoco fields, so we make sure that all original fields
     * have been removed from the class.
     */
    @Test
    public void testFieldRemoval()
            throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException,
            InstantiationException {

        harness.reset();

        harness.applyPatch("changeBaseClass");
        Class overrideClass = harness.loadPatchForClass("changeBaseClass", ClassWithFields.class);

        for (Field field : overrideClass.getDeclaredFields()) {
            assertTrue(field.getName().startsWith("$"));
        }
    }

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();
}
