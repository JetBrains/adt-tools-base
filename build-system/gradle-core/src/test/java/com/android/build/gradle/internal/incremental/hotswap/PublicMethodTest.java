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
import com.example.basic.AllAccessMethods;
import com.example.basic.PublicMethodInvoker;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.logging.Logger;

/**
 * Test to exercise public method invocations.
 */
public class PublicMethodTest {
    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();


    @Test
    public void changeBaseClassTest() throws Exception {

        harness.reset();
        AllAccessMethods allAccessMethods = new AllAccessMethods() {
            @Override
            public String abstractMethod(double a, String b, int c) {
                return "Abstract";
            }
        };
        PublicMethodInvoker publicMethodInvoker = new PublicMethodInvoker(5);

        assertWithMessage("base: PublicMethodInvoker:invokeAllPublicMethods()")
                .that(publicMethodInvoker.invokeAllPublicMethods(allAccessMethods)).isEqualTo(
                    "public_method:1.0invoker12-2-6-24.0-true-48.24-a-5");

        assertWithMessage("base: PublicMethodInvoker:invokeAllPublicArrayMethods()")
                .that(publicMethodInvoker.invokeAllPublicArrayMethods(allAccessMethods)).isEqualTo(
                "invoker,a,b-3,4,5-5,2,3-true,true,false-a,b,c-12.0,8.0,9.0-56.0,6.0,7.0-5");

        harness.applyPatch("changeSubClass");
        assertWithMessage("changeSubClass: PublicMethodInvoker:invokeAllPublicMethods()")
                .that(publicMethodInvoker.invokeAllPublicMethods(allAccessMethods)).isEqualTo(
                    "public_method:1.0invoker_reloaded12-2-6-24.0-true-48.24-a-5");

        assertWithMessage("changeSubClass: PublicMethodInvoker:invokeAllPublicArrayMethods()")
                .that(publicMethodInvoker.invokeAllPublicArrayMethods(allAccessMethods)).isEqualTo(
                "invoker:a:b-5:2:3-3:4:5-a:b:c-true:true:false-12.0:8.0:9.0-56.0:6.0:7.0-5");

        publicMethodInvoker = new PublicMethodInvoker(7);
        assertWithMessage("base: PublicMethodInvoker:invokeAllPublicMethods()")
                .that(publicMethodInvoker.invokeAllPublicMethods(allAccessMethods)).isEqualTo(
                "public_method:1.0invoker_reloaded12-2-6-24.0-true-48.24-a-14");
    }
}
