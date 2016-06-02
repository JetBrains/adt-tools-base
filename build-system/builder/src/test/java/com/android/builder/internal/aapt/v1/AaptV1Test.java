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

package com.android.builder.internal.aapt.v1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptTestUtils;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Tests for {@link AaptV1}.
 */
public class AaptV1Test {

    /**
     * Temporary folder to use in tests.
     */
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    /**
     * Creates the {@link Aapt} instance.
     *
     * @return the instance
     * @throws Exception failed to create the {@link Aapt} instance
     */
    @NonNull
    private static Aapt makeAapt() throws Exception {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        Revision revision = Revision.parseRevision("22.0.1");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo buildToolInfo =
                AndroidSdkHandler.getInstance(TestUtils.getSdkDir()).getBuildToolInfo(revision,
                        progress);
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools " + revision.toShortString());
        }

        return new AaptV1(
                new DefaultProcessExecutor(logger),
                new LoggedProcessOutputHandler(logger),
                buildToolInfo,
                logger,
                AaptV1.PngProcessMode.ALL);
    }

    @Test
    public void compilePng() throws Exception {
        Aapt aapt = makeAapt();
        Future<File> compiledFuture =
                aapt.compile(
                        AaptTestUtils.getTestPng(mTemporaryFolder),
                        AaptTestUtils.getOutputDir(mTemporaryFolder));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());
    }

    @Test
    public void compileTxt() throws Exception {
        Aapt aapt = makeAapt();
        Future<File> compiledFuture =
                aapt.compile(
                        AaptTestUtils.getTestTxt(mTemporaryFolder),
                        AaptTestUtils.getOutputDir(mTemporaryFolder));
        File compiled = compiledFuture.get();
        assertNull(compiled);
    }

    @Test
    public void parallelInterface() throws Exception {
        Aapt aapt = makeAapt();

        int parallel = 10;
        File[] imgs = new File[parallel];
        for (int i = 0; i < parallel; i++) {
            imgs[i] = mTemporaryFolder.newFile("i" + i + ".png");
            Files.copy(AaptTestUtils.getTestPng(mTemporaryFolder), imgs[i]);
        }

        @SuppressWarnings("unchecked")
        Future<File>[] futures = new Future[parallel];
        for (int i = 0; i < parallel; i++) {
            futures[i] = aapt.compile(imgs[i], AaptTestUtils.getOutputDir(mTemporaryFolder));
            assertFalse(futures[i].isDone());
        }

        Set<File> results = new HashSet<>();
        for (int i = 0; i < parallel; i++) {
            File f = futures[i].get();
            assertTrue(results.add(f));
        }
    }

    @Test
    public void noCrunchPngIfBigger() throws Exception {
        Aapt aapt = makeAapt();

        File originalFile = AaptTestUtils.getNonCrunchableTestPng();

        Future<File> compiledFuture =
                aapt.compile(originalFile, AaptTestUtils.getOutputDir(mTemporaryFolder));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());

        assertTrue(
                "originalFile.length() ["
                        + originalFile.length()
                        + "] != compiled.length() ["
                        + compiled.length()
                        + "]",
                originalFile.length() == compiled.length());
    }

    @Test
    public void crunchPngIfSmaller() throws Exception {
        Aapt aapt = makeAapt();

        File originalFile = AaptTestUtils.getCrunchableTestPng();

        Future<File> compiledFuture =
                aapt.compile(originalFile, AaptTestUtils.getOutputDir(mTemporaryFolder));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());

        assertTrue(
                "originalFile.length() ["
                        + originalFile.length()
                        + "] < compiled.length() ["
                        + compiled.length()
                        + "]",
                originalFile.length() > compiled.length());
    }

    @Test
    public void ninePatchPngsAlwaysProcessedEvenIfBigger() throws Exception {
        Aapt aapt = makeAapt();

        File originalFile = AaptTestUtils.getNinePatchTestPng();

        Future<File> compiledFuture =
                aapt.compile(originalFile, AaptTestUtils.getOutputDir(mTemporaryFolder));
        File compiled = compiledFuture.get();
        assertNotNull(compiled);
        assertTrue(compiled.isFile());

        assertTrue(
                "originalFile.length() ["
                        + originalFile.length()
                        + "] > compiled.length() ["
                        + compiled.length()
                        + "]",
                originalFile.length() < compiled.length());
    }
}
