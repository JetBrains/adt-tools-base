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

package com.android.builder.shrinker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.builder.shrinker.AbstractShrinker.CounterSet;
import com.android.builder.shrinker.TestClassesForIncremental.Cycle;
import com.android.builder.shrinker.TestClassesForIncremental.Simple;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Tests for {@link IncrementalShrinker}.
 */
public class IncrementalShrinkerTest extends AbstractShrinkerTest {

    private FullRunShrinker<String> mFullRunShrinker;

    @Before
    public void createShrinker() throws Exception {
        mFullRunShrinker = new FullRunShrinker<String>(
                new WaitableExecutor<Void>(),
                JavaSerializationShrinkerGraph.empty(mIncrementalDir),
                getPlatformJars());
    }

    @Test
    public void simple_testIncrementalUpdate() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        assertTrue(new File(mIncrementalDir, "shrinker.bin").exists());
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m1:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        long timestampBbb = getOutputClassFile("Bbb").lastModified();
        long timestampMain = getOutputClassFile("Main").lastModified();

        // Give file timestamps time to tick.
        Thread.sleep(1000);

        Files.write(Simple.main2(), new File(mTestPackageDir, "Main.class"));
        incrementalRun("Main");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m2:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        assertTrue(timestampMain < getOutputClassFile("Main").lastModified());
        assertEquals(timestampBbb, getOutputClassFile("Bbb").lastModified());
    }


    @Test
    public void cycle() throws Exception {
        // Given:
        Files.write(Cycle.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(Cycle.cycleOne(), new File(mTestPackageDir, "CycleOne.class"));
        Files.write(Cycle.cycleTwo(), new File(mTestPackageDir, "CycleTwo.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        assertTrue(new File(mIncrementalDir, "shrinker.bin").exists());
        assertMembersLeft("Main", "main:()V");
        assertMembersLeft("CycleOne", "<init>:()V");
        assertMembersLeft("CycleTwo", "<init>:()V");
        assertClassSkipped("NotUsed");

        byte[] mainBytes = Files.toByteArray(getOutputClassFile("Main"));

        Files.write(Cycle.main2(), new File(mTestPackageDir, "Main.class"));
        incrementalRun("Main");

        // Then:
        assertMembersLeft("Main", "main:()V");
        assertClassSkipped("CycleOne");
        assertClassSkipped("CycleTwo");
        assertClassSkipped("NotUsed");

        assertNotEquals(mainBytes, Files.toByteArray(getOutputClassFile("Main")));
    }

    private void fullRun(String className, String... methods) throws IOException {
        mFullRunShrinker.run(
                mInputs,
                Collections.<TransformInput>emptyList(),
                mOutput,
                ImmutableMap.<CounterSet, KeepRules>of(
                        CounterSet.SHRINK, new TestKeepRules(className, methods)),
                true);
    }

    private void incrementalRun(String... changedClasses) throws Exception {
        IncrementalShrinker<String> incrementalShrinker = new IncrementalShrinker<String>(
                new WaitableExecutor<Void>(),
                JavaSerializationShrinkerGraph.readFromDir(mIncrementalDir));

        Map<File, Status> files = Maps.newHashMap();
        for (String name : changedClasses) {
            files.put(
                    new File(mTestPackageDir, name + ".class"),
                    Status.CHANGED);
        }

        when(mDirectoryInput.getChangedFiles()).thenReturn(files);

        incrementalShrinker.incrementalRun(mInputs, mOutput);
    }
}
