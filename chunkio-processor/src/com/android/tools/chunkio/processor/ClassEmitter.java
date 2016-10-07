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
import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import com.android.tools.chunkio.codegen.ClassDef;
import com.android.tools.chunkio.codegen.JavaFile;
import com.android.tools.chunkio.reader.ArrayChunkReader;
import com.android.tools.chunkio.reader.ChunkReader;
import com.android.tools.chunkio.reader.CollectionChunkReader;
import com.android.tools.chunkio.reader.DynamicTypeChunkReader;
import com.android.tools.chunkio.reader.EnumChunkReader;
import com.android.tools.chunkio.codegen.MethodDef;
import com.android.tools.chunkio.codegen.TypeDef;
import com.android.tools.chunkio.reader.PrimitiveChunkReader;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class ClassEmitter {
    private final TypeElement typeElement;

    private final Elements elementUtils;
    private final ErrorHandler errorHandler;
    private final Filer filer;
    private final TypeVisitor<ChunkReader, Void> typeVisitor;

    public ClassEmitter(TypeElement typeElement, Environment environment) {
        this.typeElement = typeElement;
        elementUtils = environment.elementUtils;
        errorHandler = environment.errorHandler;
        filer = environment.filer;
        typeVisitor = environment.typeElementVisitor;
    }

    public void emit() {
        // Find all annotated fields in the class
        List<FieldChunk> chunks = new ChunksCollector(typeElement, errorHandler).collect();
        ClassName className = ClassName.from(typeElement, elementUtils);
        emitCode(chunks, className);
    }

    private void emitCode(List<FieldChunk> chunks, ClassName className) {
        try {
            ClassDef classDef = createClass(chunks, className);
            JavaFile javaFile = JavaFile.builder(className.packageName, classDef).build();
            writeCodeToFile(className, javaFile);
        } catch (Exception e) {
            errorHandler.error(typeElement,
                               "Code generation failed with exception: %s", Utils.stackTraceToString(e));
        }
    }

    private void writeCodeToFile(ClassName className, JavaFile javaFile)
            throws IOException {
        JavaFileObject sourceFile = filer.createSourceFile(className.qualifiedName);
        Writer writer = sourceFile.openWriter();
        javaFile.emit(writer);
        writer.close();
    }

    private ClassDef createClass(List<FieldChunk> chunks, ClassName className) {
        return ClassDef.builder(className.className)
                .modifiers(EnumSet.of(Modifier.FINAL))
                .addMethod(generateMethod(typeElement, chunks, className))
                .build();
    }

    /**
     * Creates the read() method that will be emitted in the generated source file.
     * The method has the following signature:
     * <pre>
     * static T read(DataInputStream in, LinkedList&lt;Object&gt; stack)
     * </pre>
     * Where T is an actual type.
     */
    private static MethodDef.Builder createReadMethod(TypeDef type) {
        return MethodDef.builder("read")
                .modifiers(EnumSet.of(Modifier.STATIC))
                .addParameter(RangedInputStream.class, "in")
                .addParameter(StackType.class.getGenericSuperclass(), "stack")
                .throwsException(IOException.class)
                .returns(type);
    }

    private MethodDef generateMethod(TypeElement typeElement, List<FieldChunk> chunks,
            ClassName className) {
        TypeDef type = TypeDef.fromClass(className.packageName, className.sourceName);
        String name = Utils.variableName(typeElement.getSimpleName().toString());

        MethodDef.Builder builder = createReadMethod(type);
        emitMethodPrologue(type, builder, name);

        for (FieldChunk chunk : chunks) {
            ChunkReader reader = chunk.type.accept(typeVisitor, null);
            if (reader == null) {
                errorReaderNotFound(typeElement, chunk);
                continue;
            }

            boolean hasReadCondition = emitReadConditionStart(builder, chunk);
            if (hasTypeSwitch(chunk, reader)) {
                emitSwitchedRead(builder, name, chunk, reader, typeElement);
            } else {
                emitRead(builder, name, chunk, reader);
            }
            emitDebug(type, builder, name, chunk);
            emitMatchTest(builder, name, chunk, reader);
            emitStopCondition(builder, name, chunk);
            emitReadConditionEnd(builder, hasReadCondition);
        }

        emitMethodEpilogue(builder, name);
        return builder.build();
    }

    private void emitSwitchedRead(MethodDef.Builder builder, String name,
            FieldChunk chunk, ChunkReader reader, TypeElement typeElement) {
        boolean first = true;
        Chunk.Case[] tests = chunk.chunk.switchType();

        String dottedName = name + '.';
        if (chunk.hasDynamicByteCount()) {
            emitDynamicByteCount(builder, chunk);
        }
        reader.emitPrologue(builder, dottedName, chunk);

        for (Chunk.Case test : tests) {
            TypeMirror typeMirror = typeFromTest(test);

            ChunkReader switchedReader = typeMirror.accept(typeVisitor, null);
            if (switchedReader == null) {
                errorReaderNotFound(typeElement, chunk);
                continue;
            }

            if (first) {
                builder.beginControlStatement("if ($L)", test.test());
                first = false;
            } else {
                builder.continueControlStatement("else if ($L)", test.test());
            }

            if (chunk.hasDynamicByteCount()) {
                switchedReader.emitDynamicRead(builder, dottedName, chunk);
            } else {
                switchedReader.emitRead(builder, dottedName, chunk);
            }
        }

        if (!first) {
            builder.endControlStatement();
        }
        reader.emitEpilogue(builder, dottedName, chunk);
    }

    private static void emitReadConditionEnd(MethodDef.Builder builder, boolean hasReadCondition) {
        if (hasReadCondition) {
            builder.endControlStatement();
        }
    }

    private static boolean emitReadConditionStart(MethodDef.Builder builder, FieldChunk chunk) {
        boolean readIf = !chunk.readIf().isEmpty();
        if (readIf) {
            Object[] params;
            try {
                params = chunk.readIfParams();
            } catch (MirroredTypesException e) {
                params = typesFromException(e);
            }
            builder.beginControlStatement("if (" + chunk.readIf() + ")", params);
        }
        return readIf;
    }
    private void emitRead(MethodDef.Builder builder, String name, FieldChunk chunk,
            ChunkReader reader) {
        if (reader instanceof CollectionChunkReader) {
            emitCollectionRead(name, builder, chunk, reader);
        } else {
            emitSingleRead(name, builder, chunk, reader);
        }
    }

    private static void emitSingleRead(String name, MethodDef.Builder builder,
            FieldChunk chunk, ChunkReader reader) {
        String dottedName = name + '.';
        if (chunk.hasDynamicByteCount()) {
            emitDynamicByteCount(builder, chunk);

            reader.emitPrologue(builder, dottedName, chunk);
            reader.emitDynamicRead(builder, dottedName, chunk);
            reader.emitEpilogue(builder, dottedName, chunk);
        } else {
            reader.emitPrologue(builder, dottedName, chunk);
            reader.emitRead(builder, dottedName, chunk);
            reader.emitEpilogue(builder, dottedName, chunk);
        }
    }

    private void emitCollectionRead(String name, MethodDef.Builder builder,
            FieldChunk chunk, ChunkReader reader) {
        String dottedName = name + '.';
        CollectionChunkReader collectionReader = (CollectionChunkReader) reader;

        TypeMirror elementType = collectionReader.getElementType();
        String elementName = Utils.variableName(TypeDef.of(elementType).getSimpleName());
        ChunkReader elementReader = elementType.accept(typeVisitor, null);

        reader.emitRead(builder, dottedName, chunk);

        if (chunk.hasSize()) {
            emitSize(builder, chunk);
        } else {
            emitByteCount(builder, chunk);
        }

        reader.emitPrologue(builder, "", chunk);
        builder.addStatement("$T $L", elementType, elementName);

        if (chunk.hasSize()) {
            builder.beginControlStatement("for (int i = 0; i < size; i++)");
        } else {
            builder.beginControlStatement("while (in.available() > 0)");
        }

        elementReader.emitRead(builder, "", chunk.derive(elementName));

        collectionReader.pushElement(builder, dottedName, chunk, elementName);
        builder.endControlStatement();

        reader.emitEpilogue(builder, "", chunk);
    }

    private static void emitByteCount(MethodDef.Builder builder, FieldChunk chunk) {
        if (chunk.hasDynamicByteCount()) {
            emitDynamicByteCount(builder, chunk);
        } else if (chunk.byteCount() > 0) {
            builder.addStatement("byteCount = $L", chunk.byteCount());
        }
    }

    private static void emitDynamicByteCount(MethodDef.Builder builder, FieldChunk chunk) {
        Object[] params;
        try {
            params = chunk.byteCountParams();
        } catch (MirroredTypesException e) {
            params = typesFromException(e);
        }
        emitAssignment(builder, chunk.dynamicByteCount(), params, "byteCount = ");
    }

    private static void emitSize(MethodDef.Builder builder, FieldChunk chunk) {
        if (chunk.hasDynamicSize()) {
            emitDynamicSize(builder, chunk);
        } else {
            builder.addStatement("size = $L", chunk.size());
        }
    }

    private static void emitDynamicSize(MethodDef.Builder builder, FieldChunk chunk) {
        Object[] params;
        try {
            params = chunk.sizeParams();
        } catch (MirroredTypesException e) {
            params = typesFromException(e);
        }
        emitAssignment(builder, chunk.dynamicSize(), params, "size = ");
    }

    private static void emitAssignment(MethodDef.Builder builder, String dynamic,
            Object[] params, String name) {
        if (Utils.isMultiline(dynamic)) {
            builder.beginBlock();
            builder.addStatement(dynamic, params);
            builder.endBlock();
        } else {
            builder.add(name);
            builder.addStatement(dynamic, params);
        }
    }

    /**
     * Emits a simple System.out.println() for a given field.
     */
    private static void emitDebug(TypeDef type, MethodDef.Builder builder, String name,
            FieldChunk chunk) {
        if (chunk.debug()) {
            builder.addStatement("System.out.println(\"$T.$L = \" + $L.$L)",
                    type, chunk.name, name, chunk.name);
        }
    }

    /**
     * Emits the code that compares the read field to a given expression
     * and throws an exception in case of failure.
     */
    private static void emitMatchTest(MethodDef.Builder builder, String name,
            FieldChunk chunk, ChunkReader reader) {
        if (!chunk.match().isEmpty()) {
            builder.add("$T.checkState(", ChunkUtils.class);
            emitMatchExpression(builder, name, chunk, reader);
            builder.addStatement(",\n$S)",
                    "Value read in " + chunk.name + " does not match expected value");
        }
    }

    /**
     * Emits the match expression for the specified reader.
     * An array will match using Arrays.equals(), primitives and enums using ==
     * and other types with Object.equals().
     */
    private static void emitMatchExpression(MethodDef.Builder builder, String name,
            FieldChunk chunk, ChunkReader reader) {
        if (reader instanceof PrimitiveChunkReader || reader instanceof EnumChunkReader) {
            builder.add("$L.$L == ($L)", name, chunk.name, chunk.match());
        } else if (reader instanceof ArrayChunkReader) {
            builder.add("$T.equals($L.$L, ($L))", Arrays.class, name,
                    chunk.name, chunk.match());
        } else {
            builder.add("$L.$L.equals($L)", name, chunk.name, chunk.match());
        }
    }

    /**
     * Emits the code that stops reading from the stream if a condition
     * is met.
     */
    private static void emitStopCondition(MethodDef.Builder builder, String name,
            FieldChunk chunk) {
        if (!chunk.stopIf().isEmpty()) {
            Object[] params;
            try {
                params = chunk.stopIfParams();
            } catch (MirroredTypesException e) {
                params = typesFromException(e);
            }
            builder.beginControlStatement("if (" + chunk.stopIf() + ")", params);
            builder.addStatement("stack.removeFirst()");
            builder.addStatement("return $L", name);
            builder.endControlStatement();
        }
    }

    /**
     * Emits the read() method epilogue. It pops the instance we created
     * off the stack and returns that instance.
     */
    private static void emitMethodEpilogue(MethodDef.Builder builder, String name) {
        builder.add("\n");
        builder.addStatement("stack.removeFirst()");
        builder.addStatement("return $L", name);
    }

    /**
     * Emits the read() method prologue. It creates an instance of the class
     * to read from the input stream, initializes the stack if necessary and
     * declares a couple of variables.
     */
    private static void emitMethodPrologue(TypeDef type, MethodDef.Builder builder, String name) {
        builder.addStatement("$T $L = new $T()", type, name, type);
        builder.addStatement("stack.addFirst($L)", name);
        builder.add("\n");
        builder.addStatement("int size = 0");
        builder.addStatement("long byteCount = 0");
        builder.add("\n");
    }

    private void errorReaderNotFound(TypeElement typeElement, FieldChunk chunk) {
        errorHandler.error(typeElement, "Could not generate code for field %s", chunk.name);
    }

    private static boolean hasTypeSwitch(FieldChunk chunk, ChunkReader reader) {
        return reader instanceof DynamicTypeChunkReader && chunk.switchType().length > 0;
    }

    private TypeMirror typeFromTest(Chunk.Case test) {
        TypeMirror typeMirror;
        try {
            String canonicalName = test.type().getCanonicalName();
            TypeElement switchedElement = elementUtils.getTypeElement(canonicalName);
            typeMirror = switchedElement.asType();
        } catch (MirroredTypeException e) {
            typeMirror = e.getTypeMirror();
        }
        return typeMirror;
    }

    private static Object[] typesFromException(MirroredTypesException e) {
        Object[] params;List<? extends TypeMirror> typeMirrors = e.getTypeMirrors();
        params = typeMirrors.toArray(new TypeMirror[typeMirrors.size()]);
        return params;
    }

    /**
     * This class only exist so we can get the type parameters for code generation.
     */
    private static class StackType extends LinkedList<Object> {
    }
}
