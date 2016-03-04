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

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.StaticMethodsInvoker;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.logging.Logger;

/**
 * Tests that will invoke code that itself use all possible static methods access rights invocation
 */
public class AllAccessStaticMethodsTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void checkInitialByteCodeChanges() throws Exception {

        harness.reset();

        StaticMethodsInvoker invoker = new StaticMethodsInvoker();

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticIntMethods()")
                .that(invoker.invokeAllStaticIntMethods()).isEqualTo(9);

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticStringMethods()")
                .that(invoker.invokeAllStaticStringMethods()).isEqualTo(
                "package_private,protected,public");

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticDoubleArrayMethods()")
                .that(invoker.invokeAllStaticDoubleArrayMethods() == 5d);

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticStringArrayMethods()")
                .that(invoker.invokeAllStaticStringArrayMethods()).isEqualTo(
                "package,private,string::public,string");
    }

    @Test
    public void checkByteCodeEnhancedVersion() throws Exception {

        harness.applyPatch("changeSubClass");

        StaticMethodsInvoker invoker = new StaticMethodsInvoker();

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticIntMethods()")
                .that(invoker.invokeAllStaticIntMethods()).isEqualTo(2);

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticStringMethods()")
                .that(invoker.invokeAllStaticStringMethods()).isEqualTo(
                "public,protected,package_private");

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticDoubleArrayMethods()")
                .that(invoker.invokeAllStaticDoubleArrayMethods() == 5d);

        assertWithMessage("base: StaticMethodsInvoker.invokeAllStaticStringArrayMethods()")
                .that(invoker.invokeAllStaticStringArrayMethods()).isEqualTo(
                ":package,private,string:public,string");
    }
}
