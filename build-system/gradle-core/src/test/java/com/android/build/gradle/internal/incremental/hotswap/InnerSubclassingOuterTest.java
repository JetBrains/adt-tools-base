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
import com.example.basic.InnerOuterInvoker;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for inner class subclassing outer class with private constructors.
 */
public class InnerSubclassingOuterTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();


    @Test
    public void innerClassInstantiateOuterClass()
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
            IllegalAccessException {

        harness.reset();

        InnerOuterInvoker invoker = new InnerOuterInvoker();
        assertWithMessage("base: invoker.innerClassSubclassingOuterClassIntValue()")
                .that(invoker.innerClassSubclassingOuterClassIntValue()).isEqualTo(12);

        assertWithMessage("base: invoker.innerClassSubclassingOuterClassFieldValue()")
                .that(invoker.innerClassSubclassingOuterClassFieldValue()).isEqualTo("desserts");

        // disabled due to constructor use.
        //harness.applyPatch("changeBaseClass");
        //assertWithMessage("changeSubClass: invoker.innerClassSubclassingOuterClassIntValue()")
        //        .that(invoker.innerClassSubclassingOuterClassIntValue()).isEqualTo(48);
        //
        //assertWithMessage("base: invoker.innerClassSubclassingOuterClassFieldValue()")
        //        .that(invoker.innerClassSubclassingOuterClassFieldValue()).isEqualTo("stressed");
    }
}
