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

package com.android.tools.chunkio.reader;

import com.android.tools.chunkio.processor.FieldChunk;
import com.android.tools.chunkio.Chunk;
import com.android.tools.chunkio.codegen.MethodDef;

/**
 * A chunk reader can generate code to read a specific data type
 * into a class field.
 */
public interface ChunkReader {
    /**
     * Invoked before {@link #emitRead(MethodDef.Builder, String, FieldChunk)} or
     * {@link #emitDynamicRead(MethodDef.Builder, String, FieldChunk)}.
     *
     * @param builder The method builder where to add the generated code
     * @param target The generated name of the instance holding the field to write to
     * @param chunk The field chunk describing the field to write to
     */
    void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk);

    /**
     * Invoked after {@link #emitRead(MethodDef.Builder, String, FieldChunk)} or
     * {@link #emitDynamicRead(MethodDef.Builder, String, FieldChunk)}.
     *
     * @param builder The method builder where to add the generated code
     * @param target The generated name of the instance holding the field to write to
     * @param chunk The field chunk describing the field to write to
     */
    void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk);

    /**
     * Emits the code that will read a type into the target's field.
     * When this method is called, the byte count defined in the
     * field's chunk annotation is guaranteed to be constant. As such,
     * {@link Chunk#dynamicByteCount()} can be ignored.
     *
     * @param builder The method builder where to add the generated code
     * @param target The generated name of the instance holding the field to write to
     * @param chunk The field chunk describing the field to write to
     */
    void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk);

    /**
     * Emits the code that will read a type into the target's field.
     * When this method is called, the byte count defined in the
     * field's chunk annotation is guaranteed to be dynamic. As such,
     * {@link Chunk#byteCount()} can be ignored.
     *
     * @param builder The method builder where to add the generated code
     * @param target The generated name of the instance holding the field to write to
     * @param chunk The field chunk describing the field to write to
     */
    void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk);
}
