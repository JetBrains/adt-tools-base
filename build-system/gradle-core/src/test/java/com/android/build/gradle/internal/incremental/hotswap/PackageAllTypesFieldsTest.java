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
import com.example.basic.PackagePrivateFieldAccess;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for accessing package-private, protected and public fields and methods.
 */
public class PackageAllTypesFieldsTest {
    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement(true);

    /**
     * Checks that package private fields and methods are accessible from the bytecode enhanced
     * version.
     */
    @Test
    public void checkInitialByteCodeChanges() throws Exception {

        harness.reset();
        PackagePrivateFieldAccess packagePrivateAccess = new PackagePrivateFieldAccess();

        assertWithMessage("base: packagePrivateAccess.accessAllFields()")
                .that(packagePrivateAccess.accessIntFields()).isEqualTo("19");

        assertWithMessage("base: packagePrivateAccess.accessStringFields()")
                .that(packagePrivateAccess.accessStringFields()).isEqualTo(
                "foobarfooblahblahblah");

        assertWithMessage("base: packagePrivateAccess.accessArrayFields()")
                .that(packagePrivateAccess.accessArrayFields()).isEqualTo(
                "1,2,3,4,5,1,2,3,1,3,5,7");

        assertWithMessage("base: packagePrivateAccess.accessArrayOfStringFields()")
                .that(packagePrivateAccess.accessArrayOfStringFields()).isEqualTo(
                "foo,bar,foo,blah,blah,blah");
    }

    @Test
    public void checkByteCodeEnhancedCode() throws Exception {

        harness.applyPatch("changeSubClass");
        PackagePrivateFieldAccess packagePrivateAccess = new PackagePrivateFieldAccess();

        assertWithMessage("changeSubClass: packagePrivateAccess.accessAllFields()")
                .that(packagePrivateAccess.accessIntFields()).isEqualTo("19");

        assertWithMessage("changeSubClass: packagePrivateAccess.accessStringFields()")
                .that(packagePrivateAccess.accessStringFields()).isEqualTo(
                "blahblahblahfoofoobar");

        assertWithMessage("changeSubClass: packagePrivateAccess.accessArrayFields()")
                .that(packagePrivateAccess.accessArrayFields()).isEqualTo(
                "1,3,5,7,1,2,3,1,2,3,4,5");

        assertWithMessage("changeSubClass: packagePrivateAccess.accessArrayOfStringFields()")
                .that(packagePrivateAccess.accessArrayOfStringFields()).isEqualTo(
                "blah,blah,blah,foo,foo,bar");
    }
}
