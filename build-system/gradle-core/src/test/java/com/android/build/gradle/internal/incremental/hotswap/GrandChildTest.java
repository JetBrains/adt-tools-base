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
import com.example.basic.GrandChild;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.logging.Logger;

/**
 * Tests when changing grand children of a hierarchy.
 */
public class GrandChildTest {
    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void changeGrandChildImpl() throws Exception {

        harness.reset();
        GrandChild grandChild = new GrandChild();

        assertWithMessage("base: grandChild:someProtectedMethodInv()")
                .that(grandChild.someProtectedMethodInv()).isEqualTo(
                    "protected_method:24.0from grand child12_child"
                        +"protected_method:12.0from grand child24_child");

        assertWithMessage("base: grandChild:somePublicMethodInv()")
                .that(grandChild.somePublicMethodInv()).isEqualTo(
                "public_method:24.0from grand child12_child"
                        +"public_method:12.0from grand child24_child");

        assertWithMessage("base: grandChild:equals()")
                .that(grandChild.equals(grandChild)).isTrue();

        // change the super class of the parentInvocation instance and check that parent's methods
        // are the new implementations.
        harness.applyPatch("changeSubClass");

        assertWithMessage("changeSub: grandChild:someProtectedMethodInv()")
                .that(grandChild.someProtectedMethodInv()).isEqualTo(
                "protected_method:26.0from grand child13_child"
                        + "protected_method:13.0from grand child26_child");

        assertWithMessage("changeSub: grandChild:somePublicMethodInv()")
                .that(grandChild.somePublicMethodInv()).isEqualTo(
                "public_method:26.0from grand child12_child"
                        +"public_method:12.0from grand child26_child");

        assertWithMessage("changeSub: grandChild:equals()")
                .that(grandChild.equals(grandChild)).isFalse();
    }
}
