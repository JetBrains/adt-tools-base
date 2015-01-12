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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.internal.AaptCruncher;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.internal.PngException;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;

/**
 * Synchronous version of the aapt cruncher test.
 */
public class NinePatchAaptProcessorTest extends BasePngTest {

    static AtomicLong START_TIME = new AtomicLong(System.currentTimeMillis());

    public static Test suite() {
        sourceAndCrunchedFiles.clear();
        TestSuite suite = new TestSuite();
        suite.setName("NinePatchAaptProcessor");

        NinePatchAaptProcessorTest test = null;
        for (File file : getNinePatches()) {
            if (test == null) {
                START_TIME.set(System.currentTimeMillis());
            }
            String testName = "process_aapt_" + file.getName();

            test = (NinePatchAaptProcessorTest) TestSuite.createTest(
                    NinePatchAaptProcessorTest.class, testName);

            test.setFile(file);

            suite.addTest(test);
        }
        if (test != null) {
            test.setIsFinal(true);
        }

        return suite;
    }

    @NonNull
    private File mFile;

    private boolean mIsFinal = false;

    protected void setFile(@NonNull File file) {
        mFile = file;
    }

    protected void setIsFinal(boolean isFinal) {
        mIsFinal = isFinal;
    }

    @NonNull
    protected File getAapt() {
        return getAapt(FullRevision.parseRevision("21"));
    }

    @NonNull
    protected File getAapt(FullRevision fullRevision) {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        SdkManager sdkManager = SdkManager.createManager(getSdkDir().getAbsolutePath(), logger);
        assert sdkManager != null;
        BuildToolInfo buildToolInfo = sdkManager.getBuildTool(fullRevision);
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools 20");
        }
        return new File(buildToolInfo.getPath(BuildToolInfo.PathId.AAPT));
    }

    @NonNull
    protected PngCruncher getCruncher() {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        ProcessExecutor processExecutor = new DefaultProcessExecutor(logger);
        ProcessOutputHandler processOutputHandler = new LoggedProcessOutputHandler(logger);
        File aapt = getAapt();
        return new AaptCruncher(aapt.getAbsolutePath(), processExecutor, processOutputHandler);
    }

    private static Map<File, File> sourceAndCrunchedFiles = new HashMap<File, File>();

    public void tearSuiteDown() throws IOException, DataFormatException {
        long startTime = System.currentTimeMillis();
        try {
            getCruncher().end();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("waiting for requests completion : " + (System.currentTimeMillis() - startTime));
        System.out.println("total time : " + (System.currentTimeMillis() - START_TIME.get()));
        System.out.println("Comparing crunched files");
        long comparisonStartTime = System.currentTimeMillis();
        for (Map.Entry<File, File> sourceAndCrunched : sourceAndCrunchedFiles.entrySet()) {
            System.out.println(sourceAndCrunched.getKey().getName());
            File crunched = new File(sourceAndCrunched.getKey().getParent(), sourceAndCrunched.getKey().getName() + getControlFileSuffix());

            //copyFile(sourceAndCrunched.getValue(), crunched);
            Map<String, Chunk> testedChunks = compareChunks(crunched, sourceAndCrunched.getValue());

            try {
                compareImageContent(crunched, sourceAndCrunched.getValue(), false);
            } catch(Throwable e) {
                throw new RuntimeException("Failed with " + testedChunks.get("IHDR"), e);
            }
        }
        System.out.println("Done comparing crunched files " + (System.currentTimeMillis() - comparisonStartTime));
    }

    protected String getControlFileSuffix() {
        return ".crunched.aapt";
    }

    private static void copyFile(File source, File dest)
            throws IOException {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            inputChannel.close();
            outputChannel.close();
        }
    }


    @NonNull
    protected File crunchFile(@NonNull File file)
            throws PngException, IOException {
        File outFile = File.createTempFile("pngWriterTest", ".png");
        outFile.deleteOnExit();
        PngCruncher aaptCruncher = getCruncher();
        try {
            aaptCruncher.crunchPng(file, outFile);
        } catch (PngException e) {
            e.printStackTrace();
            throw e;
        }
        return outFile;
    }

    @Override
    protected void runTest() throws Throwable {
        File outFile = crunchFile(mFile);
        sourceAndCrunchedFiles.put(mFile, outFile);
        if (mIsFinal) {
            tearSuiteDown();
        }
    }

    private static Map<String, Chunk> compareChunks(@NonNull File original, @NonNull File tested) throws
            IOException, DataFormatException {
        Map<String, Chunk> originalChunks = readChunks(original);
        Map<String, Chunk> testedChunks = readChunks(tested);

        compareChunk(originalChunks, testedChunks, "IHDR");
        compareChunk(originalChunks, testedChunks, "npLb");
        compareChunk(originalChunks, testedChunks, "npTc");

        return testedChunks;
    }

    private static void compareChunk(
            @NonNull Map<String, Chunk> originalChunks,
            @NonNull Map<String, Chunk> testedChunks,
            @NonNull String chunkType) {
        assertEquals(originalChunks.get(chunkType), testedChunks.get(chunkType));
    }

    @NonNull
    protected static File[] getNinePatches() {
        File pngFolder = getPngFolder();
        File ninePatchFolder = new File(pngFolder, "ninepatch");

        File[] files = ninePatchFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getPath().endsWith(SdkConstants.DOT_9PNG);
            }
        });
        if (files != null) {
            return files;
        }

        return new File[0];
    }
}
