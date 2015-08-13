/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.ide.common.internal.AaptCruncher;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.internal.PngException;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.Maps;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;

@RunWith(Parameterized.class)
public class NinePatchAaptProcessorTest {

    private static Map<File, File> mSourceAndCrunchedFiles;

    private static AtomicLong sClassStartTime = new AtomicLong();
    private static final AtomicInteger sCruncherKey = new AtomicInteger();
    private static final PngCruncher sCruncher = getCruncher();

    private final File mFile;

    public NinePatchAaptProcessorTest(File file, String testName) {
        mFile = file;
    }

    @BeforeClass
    public static void setup() {
        mSourceAndCrunchedFiles = Maps.newHashMap();
        sCruncherKey.set(sCruncher.start());
    }

    @Test
    public void run() throws PngException, IOException {
        File outFile = NinePatchAaptProcessorTestUtils.crunchFile(
                sCruncherKey.get(), mFile, sCruncher);
        mSourceAndCrunchedFiles.put(mFile, outFile);
    }


    @AfterClass
    public static void tearDownAndCheck() throws IOException, DataFormatException {
        NinePatchAaptProcessorTestUtils.tearDownAndCheck(
                sCruncherKey.get(), mSourceAndCrunchedFiles, sCruncher, sClassStartTime);
        mSourceAndCrunchedFiles = null;
    }

    @NonNull
    private static PngCruncher getCruncher() {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        ProcessExecutor processExecutor = new DefaultProcessExecutor(logger);
        ProcessOutputHandler processOutputHandler = new LoggedProcessOutputHandler(logger);
        File aapt = NinePatchAaptProcessorTestUtils.getAapt(FullRevision.parseRevision("21"));
        return new AaptCruncher(aapt.getAbsolutePath(), processExecutor, processOutputHandler);
    }

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> getNinePatches() {
        Collection<Object[]> params = NinePatchAaptProcessorTestUtils.getNinePatches();
        sClassStartTime.set(System.currentTimeMillis());
        return params;
    }
}
