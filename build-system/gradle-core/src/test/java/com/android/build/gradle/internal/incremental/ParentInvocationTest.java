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

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.ParentInvocation;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.logging.Logger;

public class ParentInvocationTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void changeBaseClassTest() throws Exception {

        IncrementalSupportRuntime.setLogger(Logger.getAnonymousLogger());

        harness.reset();
        ParentInvocation parentInvocation = new ParentInvocation();

        assertWithMessage("base: ParentInvocation:childMethod()")
                .that(parentInvocation.childMethod()).isEqualTo("child_method");

        // change the super class of the parentInvocation instance and check that parent's methods
        // are the new implementations.
        harness.applyPatch("changeBaseClass");
        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllParent())")
                .that(parentInvocation.invokeAllParent()).containsExactly(
                "patched_protected_method",
                "patched_package_private_method",
                "patched_public_method");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeAll())")
                .that(parentInvocation.invokeAll()).containsExactly(
                "patched_private_method",
                "patched_protected_method_child",
                "patched_package_private_method_child",
                "patched_public_method_child");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllDispatches())")
                .that(parentInvocation.invokeAllDispatches()).containsExactly(
                "patched_private_method",
                "patched_protected_method_child",
                "patched_package_private_method_child",
                "patched_public_method_child");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllDoNotOverrideDispatches())")
                .that(parentInvocation.invokeAllDoNotOverrideDispatches()).containsExactly(
                "patched_private_method",
                "patched_protected_method_child",
                "patched_package_private_method_child",
                "patched_public_method_child");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeDoNotOverrideMethodsDirectly())")
                .that(parentInvocation.doNotOverridePublicMethodDispatch()).isEqualTo(
                "patched_public_method_child");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeDoNoOverrideMethodsDirectly())")
                .that(parentInvocation.invokeDoNoOverrideMethodsDirectly()).containsExactly(
                "patched_protected_method_child",
                "patched_package_private_method_child",
                "patched_public_method_child");

    }
}

