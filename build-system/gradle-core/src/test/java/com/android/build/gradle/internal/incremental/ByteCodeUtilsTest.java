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

package com.android.build.gradle.internal.incremental;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Tests for the {@link ByteCodeUtils} class.
 */
@RunWith(MockitoJUnitRunner.class)
public class ByteCodeUtilsTest {

    @Mock
    GeneratorAdapter generator;

    @Test
    public void testShortUnbox() {
        ByteCodeUtils.unbox(generator, Type.SHORT_TYPE);
        verify(generator, times(1)).checkCast(Type.getObjectType("java/lang/Number"));
        verify(generator, times(1)).invokeVirtual(Type.getObjectType("java/lang/Number"),
                Method.getMethod("short shortValue()"));
    }

    @Test
    public void testByteUnbox() {
        ByteCodeUtils.unbox(generator, Type.BYTE_TYPE);
        verify(generator, times(1)).checkCast(Type.getObjectType("java/lang/Number"));
        verify(generator, times(1)).invokeVirtual(Type.getObjectType("java/lang/Number"),
                Method.getMethod("byte byteValue()"));
    }

    @Test
    public void testIntUnbox() {
        ByteCodeUtils.unbox(generator, Type.INT_TYPE);
        verify(generator, times(1)).unbox(Type.INT_TYPE);
    }

    @Test
    public void testObjectUnbox() {
        ByteCodeUtils.unbox(generator, Type.getType(String.class));
        verify(generator, times(1)).unbox(Type.getType(String.class));
    }

    @Test
    public void testGetPackageName() throws Exception {
        assertThat(ByteCodeUtils.getPackageName("foo/bar/Baz")).isEqualTo(Optional.of("foo.bar"));
        assertThat(ByteCodeUtils.getPackageName("Baz")).isEqualTo(Optional.empty());
    }
}
