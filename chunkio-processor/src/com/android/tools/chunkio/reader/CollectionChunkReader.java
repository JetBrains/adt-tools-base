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
import com.android.tools.chunkio.codegen.MethodDef;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Tag interface to indicate that a chunk reader can
 * read collections. Unlike other tag interfaces defined
 * in this package, this one defines a few methods.
 */
public interface CollectionChunkReader {
    /**
     * Indicates whether this reader's collection type can accept
     * the supplied list of parameters. For instance, a reader
     * that reads maps will not accept lists of parameters that
     * do not have exactly two types.
     *
     * @param parameters List of parameter types
     */
    boolean acceptTypeParameters(List<TypeElement> parameters);

    /**
     * Emits the code that will read an element for this reader's
     * collection and add it to that collection.
     *
     * @param builder The method builder where to add the generated code
     * @param target The generated name of the instance holding the collection
     * @param chunk The field chunk describing the annotated collection
     * @param elementName The generated name of the variable to add to the collection
     */
    void pushElement(MethodDef.Builder builder, String target, FieldChunk chunk, String elementName);

    /**
     * Returns the type of the values held by the collections
     * this reader can read.
     */
    TypeMirror getElementType();
}
