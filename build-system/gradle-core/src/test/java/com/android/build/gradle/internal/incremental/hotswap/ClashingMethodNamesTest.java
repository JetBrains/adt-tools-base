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

import static org.hamcrest.core.IsEqual.equalTo;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.example.basic.ClashStaticMethod;
import com.google.common.truth.Expect;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class ClashingMethodNamesTest {

    @Rule
    public Expect expect = Expect.create();

    @ClassRule
    public static ClassEnhancement harness = new ClassEnhancement(false);

    @Test
    public void checkClashingStaticAndNonStaticMethods() throws Exception {
        harness.reset();
        ClashStaticMethod m = new ClashStaticMethod("A");
        expect.withFailureMessage("base: new ClashStaticMethod('A').append('B')")
                .that(m.append("B"))
                .isEqualTo("AB_instance");

        expect.withFailureMessage("base: new ClashStaticMethod('A').append('B')")
                .that(ClashStaticMethod.append(m, "B"))
                .isEqualTo("AB");

        harness.applyPatch("changeBaseClass");
        expect.withFailureMessage("changeBaseClass: new ClashStaticMethod('A').append('B')")
                .that(m.append("B"))
                .isEqualTo("BA_instance_override");
        expect.withFailureMessage("changeBaseClass: new ClashStaticMethod('A').append('B')")
                .that(ClashStaticMethod.append(m, "B"))
                .isEqualTo("BA");

    }
}
