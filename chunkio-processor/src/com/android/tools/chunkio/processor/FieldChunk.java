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

package com.android.tools.chunkio.processor;

import com.android.tools.chunkio.Chunk;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Simple delegate for {@link Chunk} annotations. This
 * delegate also holds the name and the type of the field
 * that was annotated with the chunk.
 */
public class FieldChunk {
    /** The chunk found on a field. */
    public final Chunk chunk;
    /** Name of the annotated field. */
    public final String name;
    /** Type of the annotated field. */
    public final TypeMirror type;

    private FieldChunk(Chunk chunk, String name, TypeMirror type) {
        this.chunk = chunk;
        this.name = name;
        this.type = type;
    }

    public boolean hasSize() {
        return size() >= 0 || !dynamicSize().isEmpty();
    }

    boolean hasDynamicSize() {
        return !dynamicSize().isEmpty();
    }

    public boolean hasByteCount() {
        return byteCount() > 0 || !dynamicByteCount().isEmpty();
    }

    public boolean hasDynamicByteCount() {
        return !dynamicByteCount().isEmpty();
    }

    public long byteCount() {
        return chunk.byteCount();
    }

    public String encoding() {
        return chunk.encoding();
    }

    public int bufferSize() {
        return chunk.bufferSize();
    }

    String dynamicSize() {
        return chunk.dynamicSize();
    }

    public String key() {
        return chunk.key();
    }

    String dynamicByteCount() {
        return chunk.dynamicByteCount();
    }

    Class<?>[] byteCountParams() {
        return chunk.byteCountParams();
    }

    public int size() {
        return chunk.size();
    }

    Class<?>[] sizeParams() {
        return chunk.sizeParams();
    }

    Chunk.Case[] switchType() {
        return chunk.switchType();
    }

    String match() {
        return chunk.match();
    }

    String readIf() {
        return chunk.readIf();
    }

    Class<?>[] readIfParams() {
        return chunk.readIfParams();
    }

    String stopIf() {
        return chunk.stopIf();
    }

    Class<?>[] stopIfParams() {
        return chunk.stopIfParams();
    }

    boolean debug() {
        return chunk.debug();
    }

    FieldChunk derive(String name) {
        return new FieldChunk(this.chunk, name, this.type);
    }

    static FieldChunk from(Chunk chunk, VariableElement field) {
        return new FieldChunk(chunk, field.getSimpleName().toString(), field.asType());
    }
}
