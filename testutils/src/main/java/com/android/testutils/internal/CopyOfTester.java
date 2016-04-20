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

package com.android.testutils.internal;

import com.android.annotations.NonNull;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;

import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility to test "copyOf" methods.
 */
public class CopyOfTester {

    public static <T> void assertAllMethodsCalled(
            @NonNull Class<T> iface,
            @NonNull T object,
            @NonNull Consumer<T> methodUnderTest) {
        Set<Method> allMethods = ImmutableSet.copyOf(iface.getDeclaredMethods());
        Set<Method> methodsCalled = new HashSet<>();

        T proxy = Reflection.newProxy(iface, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(
                    @NonNull Object proxy,
                    @NonNull Method method,
                    @NonNull Object[] args)
                    throws Throwable {
                methodsCalled.add(method);
                return method.invoke(object, args);
            }
        });

        methodUnderTest.accept(proxy);

        if (!methodsCalled.containsAll(allMethods)) {
            Assert.fail(
                    String.format(
                            "Not all methods called on %s, missing: %s",
                            iface.getName(),
                            Joiner.on(',').join(Sets.difference(allMethods, methodsCalled))));
        }
    }
}
