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

package com.android.build.gradle.internal.incremental;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;

/**
 * Tests for the InstantRunMethodVerifier class
 */
public class InstantRunMethodVerifierTest {

    ClassNode targetClass;

    @Before
    public void setup() throws IOException {
        ClassReader classReader = new ClassReader(InstantRunMethodVerifierTarget.class.getName());
        targetClass = new ClassNode();
        classReader.accept(targetClass, ClassReader.SKIP_FRAMES);
    }

    @Test
    public void testMethods() {

        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) targetClass.methods) {
            if (method.name.equals(ByteCodeUtils.CONSTRUCTOR)) {
                continue;
            }
            assertEquals("Failed when checking " + method.name, InstantRunVerifierStatus.REFLECTION_USED,
                    InstantRunMethodVerifier.verifyMethod(method));
        }
    }
}
