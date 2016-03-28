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
import static org.junit.Assert.fail;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.FinalFieldsInCtor;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for classes that sets their final fields in their constructors.
 */
public class FinalFieldsInCtorTest {

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void test() throws Exception {
        harness.reset();

        FinalFieldsInCtor target = new FinalFieldsInCtor("package", "private", "protected", "public");
        assertThat(target.getPackagePrivateField()).isEqualTo("package");
        assertThat(target.getPrivateField()).isEqualTo("private");
        assertThat(target.getProtectedField()).isEqualTo("protected");
        assertThat(target.getPublicField()).isEqualTo("public");

        harness.applyPatch("changeBaseClass");
        assertThat(target.getPackagePrivateField()).isEqualTo("method package");
        assertThat(target.getPrivateField()).isEqualTo("method private");
        assertThat(target.getProtectedField()).isEqualTo("method protected");
        assertThat(target.getPublicField()).isEqualTo("method public");

        target = new FinalFieldsInCtor("package", "private", "protected", "public");
        assertThat(target.getPackagePrivateField()).isEqualTo("method modified package");
        assertThat(target.getPrivateField()).isEqualTo("method modified private");
        assertThat(target.getProtectedField()).isEqualTo("method modified protected");
        assertThat(target.getPublicField()).isEqualTo("method modified public");
    }
}
