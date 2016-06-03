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

import com.android.tools.chunkio.Chunked;
import com.android.tools.chunkio.reader.ChunkReader;
import com.android.tools.chunkio.reader.ChunkReaders;
import com.android.tools.chunkio.reader.CollectionChunkReader;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import java.util.ArrayList;
import java.util.List;

class TypeElementVisitor extends SimpleTypeVisitor6<ChunkReader, Void> {
    private final Elements elements;
    private final ErrorHandler errorHandler;

    TypeElementVisitor(Elements elements, ErrorHandler handler) {
        this.elements = elements;
        errorHandler = handler;
    }

    @Override
    public ChunkReader visitPrimitive(PrimitiveType primitiveType, Void ignore) {
        return ChunkReaders.get(primitiveType.getKind());
    }

    @Override
    public ChunkReader visitArray(ArrayType arrayType, Void ignore) {
        return ChunkReaders.getArrayReader(arrayType.getComponentType().getKind());
    }

    @Override
    public ChunkReader visitDeclared(DeclaredType declaredType, Void ignore) {
        TypeElement element = (TypeElement) declaredType.asElement();
        String name = element.getQualifiedName().toString();

        ChunkReader reader = ChunkReaders.get(name);
        if (reader == null) {
            //noinspection EnumSwitchStatementWhichMissesCases
            switch (element.getKind()) {
                case ENUM:
                    reader = getEnumChunkReader(element);
                    break;
                case CLASS:
                    reader = getChunkedChunkReader(element);
                    break;
                case INTERFACE:
                    reader = getCollectionChunkReader(declaredType, name);
                    break;
            }
        }

        return reader;
    }

    private ChunkReader getCollectionChunkReader(DeclaredType declaredType, String name) {
        ChunkReader reader = ChunkReaders.getCollectionReader(name);
        if (reader != null) {
            List<? extends TypeMirror> arguments = declaredType.getTypeArguments();
            List<TypeElement> paramElements = new ArrayList<>(arguments.size());

            for (TypeMirror argument : arguments) {
                DeclaredType declaredParamType = (DeclaredType) argument;
                TypeElement paramElement = (TypeElement) declaredParamType.asElement();
                paramElements.add(paramElement);
            }

            CollectionChunkReader collectionReader = (CollectionChunkReader) reader;
            if (!collectionReader.acceptTypeParameters(paramElements)) {
                errorHandler.error(declaredType.asElement(),
                                   "Invalid collection type parameters");
            }
        }

        return reader;
    }

    private ChunkReader getChunkedChunkReader(TypeElement element) {
        Chunked chunked = element.getAnnotation(Chunked.class);
        //noinspection VariableNotUsedInsideIf
        if (chunked != null) {
            ClassName targetName = ClassName.from(element, elements);
            return ChunkReaders.createReader(targetName.packageName, targetName.className);
        }
        return null;
    }

    private ChunkReader getEnumChunkReader(TypeElement element) {
        ClassName targetName = ClassName.from(element, elements);
        return ChunkReaders.createEnumReader(targetName.packageName, targetName.sourceName);
    }

    @Override
    public ChunkReader visitNoType(NoType noType, Void aVoid) {
        return ChunkReaders.get(TypeKind.VOID);
    }
}
