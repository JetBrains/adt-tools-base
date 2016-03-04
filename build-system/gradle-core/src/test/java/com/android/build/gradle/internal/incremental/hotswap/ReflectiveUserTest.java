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
import com.example.basic.ReflectiveUser;

import org.junit.ClassRule;
import org.junit.Test;

public class ReflectiveUserTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @SuppressWarnings("AccessStaticViaInstance")
    @Test
    public void checkByteCodeChanges() throws Exception {

        ReflectiveUser reflectiveUser = new ReflectiveUser();
        harness.reset();

        assertWithMessage("base: ReflectiveUser.reflectiveUser()")
                .that(reflectiveUser.amIWhoIThinkIam()).isTrue();

        assertWithMessage("base: ReflectiveUser.useReflectionOnPublicMethod")
                .that(reflectiveUser.useReflectionOnPublicMethod()).isEqualTo("desserts");

        assertWithMessage("base: ReflectiveUser.noReflectionUse")
                .that(reflectiveUser.noReflectionUse()).isEqualTo("desserts");

        ReflectiveUser reflectionInConstructor = new ReflectiveUser("unused");
        assertWithMessage("base: ReflectiveUser.reflectionInConstructor")
                .that(reflectionInConstructor.value).isEqualTo("desserts");

        harness.applyPatch("changeSubClass");

        // unchanged as the use of reflection is banned.
        assertWithMessage("changeSubClass: ReflectiveUser.useReflectionOnPublicMethod")
                .that(reflectiveUser.useReflectionOnPublicMethod()).isEqualTo("desserts");

        assertWithMessage("changeSubClass: ReflectiveUser.noReflectionUse")
                .that(reflectiveUser.noReflectionUse()).isEqualTo("stressed");

        // unchanged as the use of reflection is banned.
        reflectionInConstructor = new ReflectiveUser("unused");
        assertWithMessage("base: ReflectiveUser.reflectionInConstructor")
                .that(reflectionInConstructor.value).isEqualTo("desserts");
    }

}
