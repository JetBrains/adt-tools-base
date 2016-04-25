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
 * Tests for {@link com.android.build.gradle.shrinker.parser.ProguardParser}, related to primitives.
 */
public class ProguardParserTest_Primitives extends AbstractShrinkerTest {

    @Before
    public void createMainMethod() throws Exception {
        Files.write(
                TestClasses.classWithEmptyMethods(
                        "Main",
                        "intReturn:()I",
                        "intArrayReturn:()[I",
                        "intArray2Return:()[[I",
                        "intArgument:(I)V",
                        "twoPrimitiveArguments:(IF)V",
                        "intArrayArgument:([I)V",
                        "intArray2Argument:([[I)V"),
                new File(mTestPackageDir, "Main.class"));
    }

    @Test
    public void argument() throws Exception {
        run(parseKeepRules("-keep class test/Main { void *(%); }"));
        assertMembersLeft("Main", "<init>:()V", "intArgument:(I)V");
    }

    @Test
    public void arrayArgument() throws Exception {
        run(parseKeepRules("-keep class test/Main { void *(%[]); }"));
        assertMembersLeft("Main", "<init>:()V", "intArrayArgument:([I)V");
    }

    @Test
    public void arrayArgument_twoDimensions() throws Exception {
        run(parseKeepRules("-keep class test/Main { void *(%[][]); }"));
        assertMembersLeft("Main", "<init>:()V", "intArray2Argument:([[I)V");
    }

    @Test
    public void returnPrimitive() throws Exception {
        run(parseKeepRules("-keep class test/Main { % *(); }"));
        assertMembersLeft("Main", "<init>:()V", "intReturn:()I");
    }

    @Test
    public void returnArray() throws Exception {
        run(parseKeepRules("-keep class test/Main { %[] *(); }"));
        assertMembersLeft("Main", "<init>:()V", "intArrayReturn:()[I");
    }

    @Test
    public void returnArray_twoDimensions() throws Exception {
        run(parseKeepRules("-keep class test/Main { %[][] *(); }"));
        assertMembersLeft("Main", "<init>:()V", "intArray2Return:()[[I");
    }

    @Test
    public void twoPrimitiveArguments() throws Exception {
        run(parseKeepRules("-keep class test/Main { void *(%, %); }"));
        assertMembersLeft("Main", "<init>:()V", "twoPrimitiveArguments:(IF)V");
    }
}
