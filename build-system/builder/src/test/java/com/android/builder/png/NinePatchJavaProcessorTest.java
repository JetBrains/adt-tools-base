/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.android.builder.internal.JavaPngCruncher;
import com.android.ide.common.internal.PngCruncher;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.File;

public class NinePatchJavaProcessorTest extends NinePatchAaptProcessorTest {

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.setName("NinePatchProcessor");

        NinePatchJavaProcessorTest test = null;
        for (File file : getNinePatches()) {
            if (test == null) {
                START_TIME.set(System.currentTimeMillis());
            }
            String testName = "process_" + file.getName();

            test = (NinePatchJavaProcessorTest) TestSuite.createTest(
                    NinePatchJavaProcessorTest.class, testName);

            test.setFile(file);

            suite.addTest(test);
        }
        if (test != null) {
            test.setIsFinal(true);
        }
        return suite;
    }

    @NonNull
    @Override
    protected PngCruncher getCruncher() {
        return new JavaPngCruncher();
    }

    @Override
    protected String getControlFileSuffix() {
        return ".crunched";
    }
}
