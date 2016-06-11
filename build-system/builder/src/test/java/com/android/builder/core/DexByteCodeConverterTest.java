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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.android.ide.common.process.JavaProcessExecutor;
import com.android.repository.Revision;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DexByteCodeConverterTest {

    private ILogger logger;

    @Mock
    DexOptions dexOptions;

    private DexByteCodeConverter dexByteCodeConverter;

    @Before
    public void initLoggerMock() {
        logger = mock(StdLogger.class, withSettings().verboseLogging());
        dexByteCodeConverter = new DexByteCodeConverter(
                logger,
                null /* targetInfo */,
                mock(JavaProcessExecutor.class),
                false /* verboseExec */);
    }

    @Test
    public void checkSizeParser() {
        assertEquals(123L, (long) DexByteCodeConverter.parseSizeToBytes("123").get());
        assertEquals(2048L, (long) DexByteCodeConverter.parseSizeToBytes("2k").get());
        assertEquals(2048L, (long) DexByteCodeConverter.parseSizeToBytes("2K").get());
        assertEquals(1024L * 1024L * 7L, (long) DexByteCodeConverter.parseSizeToBytes("7M").get());
        assertEquals(1024L * 1024L * 1024L * 17L,
                (long) DexByteCodeConverter.parseSizeToBytes("17g").get());
        assertFalse(DexByteCodeConverter.parseSizeToBytes("foo").isPresent());
    }

    @Test
    public void checkDexInProcessIsDefaultEnabled() {
        when(dexOptions.getDexInProcess()).thenReturn(true);
        // a very small number to ensure dex in process not be disabled due to memory needs.
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("1024");
        assertTrue(dexByteCodeConverter.shouldDexInProcess(dexOptions, new Revision(23, 0, 2)));
    }

    @Test
    public void checkDisabledDexInProcessIsDisabled() {
        when(dexOptions.getDexInProcess()).thenReturn(false);
        assertFalse(dexByteCodeConverter.shouldDexInProcess(dexOptions, new Revision(23, 0, 2)));
    }

    @Test
    public void checkDexInProcessWithOldBuildToolsIsDisabled() {
        when(dexOptions.getDexInProcess()).thenReturn(true);
        assertFalse(dexByteCodeConverter.shouldDexInProcess(dexOptions, new Revision(23, 0, 1)));
    }

    @Test
    public void checkDexInProcessWithNotEnoughMemoryIsDisabled() {
        when(dexOptions.getDexInProcess()).thenReturn(true);

        // a very large number to ensure dex in process is disabled due to memory needs.
        String heapSizeSetting = "10000G";
        
        when(dexOptions.getJavaMaxHeapSize()).thenReturn(heapSizeSetting);
        assertFalse(dexByteCodeConverter.shouldDexInProcess(dexOptions, new Revision(23, 0, 2)));
        verify(logger).warning(
                contains("org.gradle.jvmargs=-Xmx"),
                any(),
                eq(10000 * 1024 + DexByteCodeConverter.NON_DEX_HEAP_SIZE / 1024 / 1024),
                contains(heapSizeSetting));
        verifyNoMoreInteractions(logger);
    }

}