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

package com.android.tools.profiler;

import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ProfilerTransform extends ClassTransform {

    public ProfilerTransform() {
        super("profiler");
    }

    @Override
    protected void transform(InputStream in, OutputStream out) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        // Create the chain of transformers
        ClassVisitor visitor = writer;
        visitor = new InitializerAdapter(visitor);
        visitor = new NetworkingAdapter(visitor);

        ClassReader cr = new ClassReader(in);
        cr.accept(visitor, 0);
        out.write(writer.toByteArray());
    }
}

