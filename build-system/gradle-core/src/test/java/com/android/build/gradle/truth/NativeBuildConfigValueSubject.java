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

package com.android.build.gradle.truth;

import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.NativeSourceFileValue;
import com.android.build.gradle.external.gson.NativeSourceFolderValue;
import com.android.build.gradle.external.gson.NativeToolchainValue;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Truth support for validating {@link NativeBuildConfigValue}.
 * - Maps are compared by key (so order doesn't matter)
 * - Lists are compared ordinally (order matters) unless the list is in UNORDERED_LISTS
 * - Fields are discovered reflectively so that new fields are caught.
 */
public class NativeBuildConfigValueSubject
        extends Subject<NativeBuildConfigValueSubject, NativeBuildConfigValue> {
    private static ImmutableSet<String> UNORDERED_LISTS = ImmutableSet.of(
            "/cFileExtensions",
            "/cppFileExtensions");

    public static final SubjectFactory<NativeBuildConfigValueSubject, NativeBuildConfigValue>
            FACTORY =  new SubjectFactory<NativeBuildConfigValueSubject, NativeBuildConfigValue>() {
                @Override
                public NativeBuildConfigValueSubject getSubject(
                        FailureStrategy fs, NativeBuildConfigValue that) {
                    return new NativeBuildConfigValueSubject(fs, that);
                }
            };

    public NativeBuildConfigValueSubject(
            FailureStrategy failureStrategy, NativeBuildConfigValue subject) {
        super(failureStrategy, subject);
    }

    public void isEqualTo(NativeBuildConfigValue other) {

        try {
            assertEqual("", getSubject(), other);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal Access", e);
        }
    }

    private void assertEqual(
            String levelDescription,
            Object actual,
            Object expected) throws IllegalAccessException {
        if (expected == null) {
            check().that(actual).named(levelDescription).isNull();
            return;
        }

        check().that(actual).named(levelDescription).isNotNull();

        if (expected instanceof List) {
            List actualList = (List) actual;
            List expectedList = (List) expected;
            check().that(actualList.size())
                    .named(levelDescription + ".size")
                    .isEqualTo(expectedList.size());

            if (UNORDERED_LISTS.contains(levelDescription)) {
                actualList = Lists.newArrayList(actualList);
                expectedList = Lists.newArrayList(expectedList);
                Collections.sort(actualList);
                Collections.sort(expectedList);
            }

            for (int i = 0; i < actualList.size(); ++i) {
                assertEqual(levelDescription + "[" + i + "]", actualList.get(i),
                        expectedList.get(i));
            }
            return;
        }

        if (expected instanceof Map) {
            check().that(actual).isInstanceOf(Map.class);
            Map<String, ?> actualMap = (Map) actual;
            Map<String, ?> expectedMap = (Map) expected;
            Set<String> actualKeys = actualMap.keySet();
            Set<String> expectedKeys = expectedMap.keySet();
            check().that(actualKeys)
                .named(levelDescription + ".keys")
                .containsAllIn(expectedKeys);
            for (Object key : actualMap.keySet()) {
                assertEqual(levelDescription + "[" + key + "]",
                        actualMap.get(key), expectedMap.get(key));
            }
            return;
        }

        if (expected instanceof NativeBuildConfigValue
                || expected instanceof NativeLibraryValue
                || expected instanceof NativeSourceFileValue
                || expected instanceof NativeSourceFolderValue
                || expected instanceof NativeToolchainValue) {
            check().that(actual).isInstanceOf(expected.getClass());
            for (Field field : actual.getClass().getFields()) {
                Object fieldActual = field.get(actual);
                Object fieldExpected = field.get(expected);
                assertEqual(levelDescription + "/" + field.getName(),
                        fieldActual, fieldExpected);
            }
            return;
        }

        if (expected instanceof File) {
            check().that(actual).isInstanceOf(expected.getClass());
            check().that(((File) actual).getPath().replace('\\', '/'))
                    .named(levelDescription)
                    .isEqualTo(((File) expected).getPath().replace('\\', '/'));
        } else if (expected instanceof String) {
            check().that(actual).isInstanceOf(expected.getClass());
            check().that(((String) actual).replace('\\', '/'))
                    .named(levelDescription)
                    .isEqualTo(((String) expected).replace('\\', '/'));
        } else {
            check().that(actual).named(levelDescription).isEqualTo(expected);
        }
    }

}
