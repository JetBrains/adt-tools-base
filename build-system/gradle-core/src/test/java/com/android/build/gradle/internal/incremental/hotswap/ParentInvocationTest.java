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
import com.example.basic.ParentInvocation;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.logging.Logger;

public class ParentInvocationTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void changeBaseClassTest() throws Exception {

        harness.reset();
        ParentInvocation parentInvocation = new ParentInvocation();

        assertWithMessage("base: ParentInvocation:childMethod()")
                .that(parentInvocation.childMethod(1.2, ".3.", 4)).isEqualTo("child_method:1.2.3.4");

        // change the super class of the parentInvocation instance and check that parent's methods
        // are the new implementations.
        harness.applyPatch("changeBaseClass");

        assertWithMessage("base: ParentInvocation:childMethod()")
                .that(parentInvocation.childMethod(1.2, ".3.", 4)).isEqualTo("child_method:1.2.3.4");

        // Call methods defined on the base class
        assertWithMessage("changeBaseClass: AllAccessMethods:invokeAll())")
                .that(parentInvocation.invokeAll(1.2, ".3.", 4)).containsExactly(
                "patched_private_method:1.2.3.4",
                "patched_protected_method:1.2.3.4_child",
                "patched_package_private_method:1.2.3.4_child",
                "patched_public_method:1.2.3.4_child");

        assertWithMessage("changeBaseClass: AllAccessMethods:invokeAllDispatches())")
                .that(parentInvocation.invokeAllDispatches(1.2, ".3.", 4)).containsExactly(
                "patched_private_method:1.2.3.4",
                "patched_protected_method:1.2.3.4_child",
                "patched_package_private_method:1.2.3.4_child",
                "patched_public_method:1.2.3.4_child");

        assertWithMessage("changeBaseClass: AllAccessMethods:invokeAllDoNotOverrideDispatches())")
                .that(parentInvocation
                        .invokeAllDoNotOverrideDispatches(1.2, ".3.", 4)).containsExactly(
                "patched_private_method:1.2.3.4",
                "patched_protected_method:1.2.3.4_child",
                "patched_package_private_method:1.2.3.4_child",
                "patched_public_method:1.2.3.4_child");

        // Call methods on the sub class
        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllParent())")
                .that(parentInvocation.invokeAllParent(1.2, ".3.", 4)).containsExactly(
                "patched_protected_method:1.2.3.4",
                "patched_package_private_method:1.2.3.4",
                "patched_public_method:1.2.3.4");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllFromSubclass())")
                .that(parentInvocation.invokeAllFromSubclass(1.2, ".3.", 4)).containsExactly(
                "patched_package_private_method:1.2.3.4_child",
                "patched_protected_method:1.2.3.4_child",
                "patched_public_method:1.2.3.4_child",
                "abstract: 1.2.3.4");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeDoNoOverrideMethodsDirectly())")
                .that(parentInvocation
                        .invokeDoNoOverrideMethodsDirectly(1.2, ".3.", 4)).containsExactly(
                "patched_protected_method:1.2.3.4_child",
                "patched_package_private_method:1.2.3.4_child",
                "patched_public_method:1.2.3.4_child");

        // Now change the sub class
        harness.applyPatch("changeSubClass");

        // Call methods on the sub class
        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllParent())")
                .that(parentInvocation.invokeAllParent(1.2, ".3.", 4)).containsExactly(
                "patched_protected_method:1.2.3.4",
                "patched_package_private_method:1.2.3.4",
                "patched_public_method:1.2.3.4");

        assertWithMessage("changeBaseClass: ParentInvocation:invokeAllFromSubclass())")
                .that(parentInvocation.invokeAllFromSubclass(1.2, ".3.", 4)).containsExactly(
                "patched_package_private_method:1.2.3.4_child",
                "patched_protected_method:1.2.3.4_child",
                "patched_public_method:1.2.3.4_child",
                "abstract_patched: 1.2.3.4");

        assertWithMessage("base: ParentInvocation:childMethod()")
                .that(parentInvocation.childMethod(1.2, ".3.", 4)).isEqualTo("patched_child_method:1.2.3.4");

        //assertWithMessage("changeBaseClass: ParentInvocation:invokeDoNoOverrideMethodsDirectly())")
        //        .that(parentInvocation
        //                .invokeDoNoOverrideMethodsDirectly(1.2, ".3.", 4)).containsExactly(
        //        "patched_protected_method:1.2.3.4_child",
        //        "patched_package_private_method:1.2.3.4_child",
        //        "patched_public_method:1.2.3.4_child");
    }
}

