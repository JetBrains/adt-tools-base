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

package com.android.build.api;

import static org.junit.Assert.assertEquals;

import com.android.build.api.transform.Transform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test that tries to ensure that our public API remains stable.
 */
public class StableApiTest {

    private static final URL API_LIST_URL =
            Resources.getResource(StableApiTest.class, "api-list.txt");

    @Test
    public void apiElements() throws Exception {
        ImmutableSet<ClassPath.ClassInfo> allClasses =
                ClassPath.from(Transform.class.getClassLoader())
                        .getTopLevelClassesRecursive("com.android.build.api");

        String apiElements =
                allClasses
                        .stream()
                        .filter(classInfo -> !classInfo.getSimpleName().endsWith("Test"))
                        .flatMap(classInfo -> getApiElements(classInfo.load()))
                        .sorted()
                        .collect(Collectors.joining("\n"));

        // Compare the two as strings, to get a nice diff UI in the IDE.
        String expectedApiElements = Resources.toString(API_LIST_URL, Charset.defaultCharset());
        assertEquals(expectedApiElements, apiElements);
    }

    @Test
    public void apiListHash() throws Exception {
        // ATTENTION REVIEWER: if this needs to be changed, please make sure changes to api-list.txt
        // are backwards compatible.
        assertEquals(
                "7e068a2459a5ea323f9f2d7c72d55e71b6574ac1",
                Hashing.sha1().hashBytes(Resources.toByteArray(API_LIST_URL)).toString());
    }

    private static Stream<String> getApiElements(Class<?> klass) {
        if (!Modifier.isPublic(klass.getModifiers())) {
            return Stream.empty();
        }

        for (Field field : klass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())) {
                Assert.fail(
                        String.format(
                                "Public instance field %s exposed in class %s.",
                                field.getName(),
                                klass.getName()));
            }
        }

        Stream<Stream<String>> streams =
                Stream.of(
                        // The class itself:
                        Stream.of(klass.getName()),

                        // Constructors:
                        Stream.of(klass.getDeclaredConstructors())
                                .map(Invokable::from)
                                .filter(StableApiTest::isPublic)
                                .map(StableApiTest::getApiElement),

                        // Methods:
                        Stream.of(klass.getDeclaredMethods())
                                .map(Invokable::from)
                                .filter(StableApiTest::isPublic)
                                .map(StableApiTest::getApiElement),

                        // Finally, all inner classes:
                        Stream.of(klass.getDeclaredClasses())
                                .flatMap(StableApiTest::getApiElements));

        return streams.flatMap(Function.identity());
    }

    private static boolean isPublic(Invokable<?, ?> invokable) {
        return invokable.isPublic();
    }

    private static String getApiElement(Invokable<?, ?> invokable) {
        String className = invokable.getDeclaringClass().getName();
        String parameters =
                invokable
                        .getParameters()
                        .stream()
                        .map(Parameter::getType)
                        .map(StableApiTest::typeToString)
                        .collect(Collectors.joining(", "));
        String descriptor = typeToString(invokable.getReturnType()) + " (" + parameters + ")";

        String name = invokable.getName();
        if (name.equals(className)) {
            name = "<init>";
        }

        String thrownExceptions = "";
        ImmutableList<TypeToken<? extends Throwable>> exceptionTypes =
                invokable.getExceptionTypes();
        if (!exceptionTypes.isEmpty()) {
            thrownExceptions =
                    exceptionTypes
                            .stream()
                            .map(StableApiTest::typeToString)
                            .collect(Collectors.joining(", ", " throws ", ""));
        }

        return String.format("%s.%s: %s%s", className, name, descriptor, thrownExceptions);
    }

    private static String typeToString(TypeToken<?> typeToken) {
        if (typeToken.isArray()) {
            return typeToString(typeToken.getComponentType()) + "[]";
        } else {
            return typeToken.toString();
        }
    }
}
