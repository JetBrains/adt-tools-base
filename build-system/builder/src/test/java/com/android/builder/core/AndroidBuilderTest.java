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

package com.android.builder.core;

import static com.android.builder.core.AndroidBuilder.parseHeapSize;
import static com.android.builder.core.AndroidBuilder.shouldDexInProcess;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.android.repository.Revision;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

@RunWith(MockitoJUnitRunner.class)
public class AndroidBuilderTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static File smallJar;
    private static File smallJar2;
    private static File largeJar;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    ILogger logger;

    @Mock
    DexProcessBuilder processBuilder;

    @Mock
    DexOptions dexOptions;

    @Before
    public void initLoggerMock() {
        logger = mock(ILogger.class, withSettings().verboseLogging());
    }

    @BeforeClass
    public static void createJars() throws IOException {
        smallJar = tempFolder.newFile("smallClasses.jar");
        createJar(smallJar, 5);
        smallJar2 = tempFolder.newFile("smallClasses2.jar");
        createJar(smallJar2, 6);
        largeJar = tempFolder.newFile("largeClasses.jar");
        createJar(largeJar, 100);
    }

    private static void createJar(File file, int number) throws IOException {
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file));
        try {
            for (int i = 0; i < number; i++) {
                jarOutputStream.putNextEntry(new ZipEntry(String.format("AnObject%1$d.class", i)));
                jarOutputStream.closeEntry();
            }
        } finally {
            //noinspection ThrowFromFinallyBlock
            jarOutputStream.close();
        }
    }

    @Test
    public void checkHeapSizeParser() {
        assertEquals(123L, parseHeapSize("123"));
        assertEquals(2048L, parseHeapSize("2k"));
        assertEquals(2048L, parseHeapSize("2K"));
        assertEquals(1024L * 1024L * 7L, parseHeapSize("7M"));
        assertEquals(1024L * 1024L * 1024L * 17L, parseHeapSize("17g"));
    }

    @Test
    public void testShouldDexInProcess() {
        // a ridiculously small number to ensure dex in process not be disabled due to memory needs.
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("1024");
        assertFalse(shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 2), false, logger));
    }

    @Test
    public void testDisabledDexInProcess() {
        assertFalse(shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 2), false, logger));
        assertFalse(shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 2), false, logger));
    }



    @Test
    @Ignore
    public void testExplicitInvalidDexInProcess() {
        expectedException.expectMessage(containsString("23.0.2"));
        shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 1), false, logger);
    }

    @Test
    @Ignore
    public void notEnoughMemoryForDexInProcess() {
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("10000G");
        shouldDexInProcess(processBuilder, dexOptions, new Revision(23, 0, 2), false, logger);
    }

    @Test
    public void instantRunWithOldBuildTools() {
        assertFalse(shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 1), true, logger));
    }


    @Test
    public void lowMemoryWithInstantRunAndManyClasses() {
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("10000G");
        when(processBuilder.getInputs()).thenReturn(Collections.singleton(largeJar));
        assertFalse(shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 2), true, logger));
    }

    @Test
    public void lowMemoryWithInstantRunWithMultipleInputs() {
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("10000G");
        when(processBuilder.getInputs()).thenReturn(ImmutableSet.of(smallJar, smallJar2));
        assertFalse(shouldDexInProcess(
                processBuilder, dexOptions, new Revision(23, 0, 2), true, logger));
    }



}
