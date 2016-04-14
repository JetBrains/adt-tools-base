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
import com.example.basic.InnerClassInvoker;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Invoker class of inner classes methods.
 */
public class InnerClassInvokerTest {


    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();


    @Test
    public void innerClassInstantiateOuterClass()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {

        harness.reset();

        InnerClassInvoker invoker = new InnerClassInvoker();
        assertWithMessage("base: invoker.invokeInnerClass()")
                .that(invoker.invokeInnerClass()).isEqualTo("from builder");

        harness.applyPatch("changeSubClass");
        assertWithMessage("changeSubClass: invoker.invokeInnerClass()")
                .that(invoker.invokeInnerClass()).isEqualTo("from builder revisited");
    }
}
