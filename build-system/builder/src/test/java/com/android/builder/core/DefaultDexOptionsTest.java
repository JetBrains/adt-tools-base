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

package com.android.builder.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;

public class DefaultDexOptionsTest {

    @Test
    public void copyOfHandlesAllFields() throws Exception {
        DefaultDexOptions realObject = new DefaultDexOptions();
        Set<Method> allGetters = ImmutableSet.copyOf(DexOptions.class.getDeclaredMethods());
        Set<Method> gettersCalled = Sets.newHashSet();

        DexOptions proxy = Reflection.newProxy(DexOptions.class, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(
                    @NonNull Object proxy,
                    @NonNull Method method,
                    @NonNull Object[] args) throws Throwable {
                gettersCalled.add(method);
                return method.invoke(realObject, args);
            }
        });

        DefaultDexOptions.copyOf(proxy);
        assertThat(gettersCalled).containsExactlyElementsIn(allGetters);
    }
}