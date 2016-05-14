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

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class ChunksCollector {
    private final TypeElement mElement;
    private final ErrorHandler mErrorHandler;

    ChunksCollector(TypeElement element, ErrorHandler errorHandler) {
        mElement = element;
        mErrorHandler = errorHandler;
    }

    /**
     * Finds all the fields in the specified type elememt that are annotated
     * with {@link Chunk}. The chunk annotations are wrapped in a {@link FieldChunk}
     * to provide easy access to the field's type and name.
     *
     * Only valid fields are returned, others are ignored. A field is valid if it:
     * - is annotated with {@link Chunk}
     * - is not final
     * - is not private
     * - is not static
     */
    List<FieldChunk> collect() {
        List<? extends Element> enclosedElements = mElement.getEnclosedElements();
        List<VariableElement> fields = ElementFilter.fieldsIn(enclosedElements);

        List<FieldChunk> chunks = new ArrayList<>();
        for (VariableElement field : fields) {
            Chunk chunk = field.getAnnotation(Chunk.class);
            if (chunk != null && validate(field)) {
                chunks.add(FieldChunk.from(chunk, field));
            }
        }

        return chunks;
    }

    private boolean validate(VariableElement field) {
        Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.FINAL)) {
            mErrorHandler.error(mElement, "Field %s in class %s is final", field.getSimpleName());
            return false;
        } else if (modifiers.contains(Modifier.PRIVATE)) {
            mErrorHandler.error(mElement, "Field %s in class %s is private", field.getSimpleName());
            return false;
        } else if (modifiers.contains(Modifier.STATIC)) {
            mErrorHandler.error(mElement, "Field %s in class %s is static", field.getSimpleName());
            return false;
        }
        return true;
    }
}
