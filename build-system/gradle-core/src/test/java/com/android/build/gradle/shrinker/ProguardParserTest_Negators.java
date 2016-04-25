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

package com.android.build.gradle.shrinker;

import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Tests for {@link com.android.build.gradle.shrinker.parser.ProguardParser}, related to negated
 * types names.
 */
public class ProguardParserTest_Negators extends AbstractShrinkerTest {

    @Before
    public void createMainMethod() throws Exception {
        Files.write(
                TestClasses.emptyClass("OnClickListener"),
                new File(mTestPackageDir, "OnClickListener.class"));

        Files.write(
                TestClasses.classWithEmptyMethods(
                        "Main",
                        "getOther:()I",
                        "setOther:(I)V",
                        "getOnClickListener:()Ltest/OnClickListener;",
                        "setOnClickListener:(Ltest/OnClickListener;)V"),
                new File(mTestPackageDir, "Main.class"));
    }

    @Test
    public void argumentType() throws Exception {
        run(parseKeepRules("-keep class test/Main { void set*(!**Listener); }"));
        assertMembersLeft("Main", "<init>:()V", "setOther:(I)V");
    }

    @Test
    public void returnType() throws Exception {
        run(parseKeepRules("-keep class test/Main { !**Listener get*(); }"));
        assertMembersLeft("Main", "<init>:()V", "getOther:()I");
    }
}
