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

import com.android.build.gradle.ProguardFiles;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Tests that {@link com.android.build.gradle.shrinker.parser.ProguardParser} can parse the bundled
 * rules that we ship with the plugin.
 */
public class ProguardParserTest_DefaultRules extends AbstractShrinkerTest {

    @Before
    public void createMainMethod() throws Exception {
        Files.write(
                TestClasses.emptyClass("Main"),
                new File(mTestPackageDir, "Main.class"));
    }

    @Test
    public void testAllWhitelistedFiles() throws Exception {
        for (String name : ProguardFiles.DEFAULT_PROGUARD_WHITELIST) {
            File rulesFile = tmpDir.newFile(name);
            ProguardFiles.extractBundledProguardFile(name, rulesFile);
            run(parseKeepRules(Files.toString(rulesFile, StandardCharsets.UTF_8)));
            assertClassSkipped("Main");
        }
    }
}
