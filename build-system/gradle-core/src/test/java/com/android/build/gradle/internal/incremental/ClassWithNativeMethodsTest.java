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

package com.android.build.gradle.internal.incremental;

import static org.junit.Assert.fail;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.ClassWithNativeMethod;
import java.lang.reflect.Method;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Check that native methods are not added to patched classes.
 */
public class ClassWithNativeMethodsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void checkNativeNotAdded() throws Exception {
        harness.reset();
        harness.applyPatch("changeSubClass");
        Class<?> overrideClass =
                harness.loadPatchForClass("changeBaseClass", ClassWithNativeMethod.class);

        for (Method m :
                overrideClass.getMethods()) {
            if (m.getName().contains("jniEnterprise")) {
                fail("Override class should not contain native methods from the base class");
            }
        }
    }
}
