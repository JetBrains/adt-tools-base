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

import com.android.ide.common.internal.PngCruncher;
import com.android.sdklib.mock.MockLog;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.util.zip.DataFormatException;

/**
 * Asynchronous version of the aapt cruncher test.
 */
public class NinePatchAsyncAaptProcessTest extends NinePatchAaptProcessorTest {

    private MockLog mLogger = new MockLog();

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.setName("NinePatchAsyncAaptProcessor");

        NinePatchAaptProcessorTest test = null;
        for (File file : getNinePatches()) {
            String testName = "process_async_aapt_" + file.getName();

            test = (NinePatchAsyncAaptProcessTest) TestSuite.createTest(
                    NinePatchAsyncAaptProcessTest.class, testName);

            test.setFile(file);

            suite.addTest(test);
        }
        if (test != null) {
            test.setIsFinal(true);
        }

        return suite;
    }

    @Override
    public void tearSuiteDown() throws IOException, DataFormatException {

        super.tearSuiteDown();
        for (String message : mLogger.getMessages()) {
            System.out.println(message);
        }
    }

    @Override
    protected PngCruncher getCruncher() {
        File aapt = new File(getSdkDir(), "build-tools/android-21/aapt");

        assertTrue("Test requires build-tools 21.0.0", aapt.isFile());
        return QueuedCruncher.Builder.INSTANCE.newCruncher(aapt.getAbsolutePath(), mLogger);
    }
}
