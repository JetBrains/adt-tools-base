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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility to test "copyOf" methods.
 */
public class CopyOfTester {

    private static final Pattern GETTER_NAME = Pattern.compile("^(get|is)[A-Z].*");

    private static final Predicate<Method> IS_GETTER =
            m -> GETTER_NAME.matcher(m.getName()).matches();

    public static <T> void assertAllGettersCalled(
            @NonNull Class<T> klass,
            @NonNull T object,
            @NonNull Consumer<T> methodUnderTest) {
        Set<Method> allGetters =
                Arrays.stream(klass.getMethods())
                        .filter(IS_GETTER)
                        .filter(method ->  method.getDeclaringClass() != Object.class)
                        .collect(Collectors.toSet());
        assertThat(allGetters).named("getters declared in " + klass.getName()).isNotEmpty();

        Set<Method> gettersCalled = new HashSet<>();

        T mock = Mockito.mock(klass, (Answer) invocation -> {
            Method method = invocation.getMethod();
            if (GETTER_NAME.matcher(method.getName()).matches()) {
                gettersCalled.add(method);
            }
            return method.invoke(object, invocation.getArguments());
        });

        methodUnderTest.accept(mock);

        assertThat(gettersCalled)
                .named("getters called")
                .containsExactlyElementsIn(allGetters);
    }
}
