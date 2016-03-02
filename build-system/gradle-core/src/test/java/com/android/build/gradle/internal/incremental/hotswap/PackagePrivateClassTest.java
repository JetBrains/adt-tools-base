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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.PackagePrivateInvoker;

import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

/**
 * Test for package private classes/methods and fields.
 */
public class PackagePrivateClassTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();


    @Test
    public void verifyBaseClassAccesses() throws Exception {
        Class packagePrivateClass = PackagePrivateInvoker.class.getClassLoader()
                .loadClass("com.example.basic.PackagePrivateClass");
        assertThat(packagePrivateClass).isNotNull();
        assertTrue(Modifier.isPublic(packagePrivateClass.getModifiers()));
        for (Method method : packagePrivateClass.getDeclaredMethods()) {
            assertWithMessage(method.getName() + " is package private")
                    .that(Modifier.isPublic(method.getModifiers())
                        || Modifier.isPrivate(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers())).isTrue();
        }
        for (Field field : packagePrivateClass.getDeclaredFields()) {
            assertWithMessage(field.getName() + " is package private")
                    .that(Modifier.isPublic(field.getModifiers())
                            || Modifier.isPrivate(field.getModifiers())
                            || Modifier.isProtected(field.getModifiers())).isTrue();
        }
    }

    @Test
    public void changeBaseClassTest() throws Exception {

        harness.reset();
        PackagePrivateInvoker packagePrivateInvoker = new PackagePrivateInvoker();

        assertWithMessage("base: PackagePrivateInvoker:createPackagePrivateObject()")
                .that(packagePrivateInvoker.createPackagePrivateObject()).isEqualTo("foo");

        assertWithMessage("base: PackagePrivateInvoker:invokeTernaryOperator()")
                .that(packagePrivateInvoker.invokeTernaryOperator(true))
                .isEqualTo("package_private");

        assertWithMessage("base: PackagePrivateInvoker:invokeTernaryOperator()")
                .that(packagePrivateInvoker.invokeTernaryOperator(false))
                .isEqualTo("another_package_private");

        assertWithMessage("base: PackagePrivateInvoker:ternaryOperatorInConstructorParams()")
                .that(packagePrivateInvoker.ternaryOperatorInConstructorParams(true))
                .isEqualTo("true");

        assertWithMessage("base: PackagePrivateInvoker:ternaryOperatorInConstructorParams()")
                .that(packagePrivateInvoker.ternaryOperatorInConstructorParams(false))
                .isEqualTo("false");

        assertWithMessage("base: PackagePrivateInvoker:invokePackagePrivateInterface()")
                .that(packagePrivateInvoker.invokePackagePrivateInterface())
                .isEqualTo("packagePrivateInterface");


        harness.applyPatch("changeSubClass");
        assertWithMessage("changeSubClass: PackagePrivateInvoker:createPackagePrivateObject()")
                .that(packagePrivateInvoker.createPackagePrivateObject()).isEqualTo("foobar");

        assertWithMessage("changeSubClass: PackagePrivateInvoker:invokeTernaryOperator()")
                .that(packagePrivateInvoker.invokeTernaryOperator(true))
                .isEqualTo("patched_package_private");

        assertWithMessage("changeSubClass: PackagePrivateInvoker:invokeTernaryOperator()")
                .that(packagePrivateInvoker.invokeTernaryOperator(false))
                .isEqualTo("patched_another_package_private");

        assertWithMessage("changeSubClass: PackagePrivateInvoker:ternaryOperatorInConstructorParams()")
                .that(packagePrivateInvoker.ternaryOperatorInConstructorParams(true))
                .isEqualTo("false");

        assertWithMessage("changeSubClass: PackagePrivateInvoker:ternaryOperatorInConstructorParams()")
                .that(packagePrivateInvoker.ternaryOperatorInConstructorParams(false))
                .isEqualTo("true");

        assertWithMessage("changeSubClass: PackagePrivateInvoker:invokePackagePrivateInterface()")
                .that(packagePrivateInvoker.invokePackagePrivateInterface())
                .isEqualTo("patched_packagePrivateInterface");
    }
}