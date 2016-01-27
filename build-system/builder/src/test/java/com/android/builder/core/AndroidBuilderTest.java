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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.repository.Revision;
import com.android.utils.ILogger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AndroidBuilderTest {

    @Mock
    ILogger logger;

    @Mock
    DexOptions dexOptions;

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
        when(dexOptions.getDexInProcess()).thenReturn(true);
        // a ridiculous number to ensure dex in process not be disabled due to memory needs.
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("1024");
        assertTrue(shouldDexInProcess(dexOptions, new Revision(23, 0, 2), false, logger));
    }

    @Test
    public void testDisabledDexInProcess() {
        when(dexOptions.getDexInProcess()).thenReturn(null);
        assertFalse(shouldDexInProcess(dexOptions, new Revision(23, 0, 2), false, logger));
        when(dexOptions.getDexInProcess()).thenReturn(false);
        assertFalse(shouldDexInProcess(dexOptions, new Revision(23, 0, 2), false, logger));
    }

    @Test(expected = RuntimeException.class)
    public void testExplicitInvalidDexInProcess() {
        when(dexOptions.getDexInProcess()).thenReturn(true);
        shouldDexInProcess(dexOptions, new Revision(23, 0, 1), false, logger);
    }

    @Test
    public void notEnoughMemoryForDexInProcess() {
        when(dexOptions.getDexInProcess()).thenReturn(true);
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("5000G");
        assertFalse(shouldDexInProcess(dexOptions, new Revision(23, 0, 2), false, logger));
        verify(logger).warning(anyString(), Matchers.any(), Matchers.any());
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void instantRunWithOldBuildTools() {
        when(dexOptions.getDexInProcess()).thenReturn(true);
        assertFalse(shouldDexInProcess(dexOptions, new Revision(23, 0, 1), true, logger));
        verify(logger).warning(anyString(), Matchers.any());
        verifyNoMoreInteractions(logger);
    }
}
