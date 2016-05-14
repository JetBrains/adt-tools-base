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

import com.android.tools.chunkio.Chunked;
import com.android.tools.chunkio.processor.FieldChunk;
import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.codegen.MethodDef;
import com.android.tools.chunkio.codegen.TypeDef;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.DataInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all supported instances of {@link ChunkReader}. These instances
 * can be queried using the following methods:
 * <ul>
 *     <li>{@link #get(String)}, to retrieve a Class chunk reader</li>
 *     <li>{@link #get(TypeKind)}, to retrieve a primitive chunk reader</li>
 *     <li>{@link #getArrayReader(TypeKind)}, to retrieve an array chunk reader</li>
 *     <li>{@link #getCollectionReader(String)}, to retrieve a collection chunk reader</li>
 * </ul>
 */
public final class ChunkReaders {
    private static final Map<TypeKind, ChunkReader> sTypeReaders = new HashMap<>();
    private static final Map<TypeKind, ChunkReader> sArrayReaders = new HashMap<>();
    private static final Map<String, ChunkReader> sClassReaders = new HashMap<>();
    private static final Map<String, ChunkReader> sCollectionReaders = new HashMap<>();

    static {
        sTypeReaders.put(TypeKind.BOOLEAN, new BooleanChunkReaderImpl());
        sTypeReaders.put(TypeKind.BYTE, new ByteChunkReaderImpl());
        sTypeReaders.put(TypeKind.CHAR, new CharChunkReaderImpl());
        sTypeReaders.put(TypeKind.DOUBLE, new DoubleChunkReaderImpl());
        sTypeReaders.put(TypeKind.FLOAT, new FloatChunkReaderImpl());
        sTypeReaders.put(TypeKind.INT, new IntChunkReaderImpl());
        sTypeReaders.put(TypeKind.LONG, new LongChunkReaderImpl());
        sTypeReaders.put(TypeKind.SHORT, new ShortChunkReaderImpl());

        sArrayReaders.put(TypeKind.BYTE, new ByteArrayChunkReaderImpl());

        sClassReaders.put(Object.class.getCanonicalName(), new ObjectChunkReaderImpl());
        sClassReaders.put(String.class.getCanonicalName(), new StringChunkReaderImpl());
        sClassReaders.put(Void.class.getCanonicalName(), new VoidChunkReaderImpl());

        sCollectionReaders.put(List.class.getCanonicalName(), new ListChunkReaderImpl());
        sCollectionReaders.put(Map.class.getCanonicalName(), new MapChunkReaderImpl());
    }

    private ChunkReaders() {
    }

    /**
     * Returns a chunk reader for the specified type. The only supported types
     * are primitives: boolean, byte, char, double, float, int, long and short.
     */
    public static ChunkReader get(TypeKind kind) {
        return sTypeReaders.get(kind);
    }

    /**
     * Returns a chunk reader for the specified fully qualified class name.
     * Classes supported by default are: Object, String and Void. More
     * readers can be added using {@link #createReader(String, String)} and
     * {@link #createEnumReader(String, String)}.
     */
    public static ChunkReader get(String className) {
        return sClassReaders.get(className);
    }

    /**
     * Returns a chunk reader for an array of the specified type.
     * Only byte arrays are supported.
     */
    public static ChunkReader getArrayReader(TypeKind kind) {
        return sArrayReaders.get(kind);
    }

    /**
     * Returns a chunk reader for the specified fully qualified collection
     * name. The supported collections are <code>java.util.List</code> and
     * <code>java.util.Map</code>.
     */
    public static ChunkReader getCollectionReader(String className) {
        return sCollectionReaders.get(className);
    }

    /**
     * Creates and returns a reader for the specified class in the specified
     * package. The class <strong>must</strong> declared the {@link Chunked}
     * annotation for the reader to be valid.
     * The created reader is cached and be subsequently accessed using
     * {@link #get(String)}.
     *
     * @param packageName The package name of the class to read
     * @param className The name of the class to read
     */
    public static ChunkReader createReader(String packageName, String className) {
        ChunkReader reader = new ChunkedChunkReaderImpl(packageName, className);
        cacheReader(className, reader);
        return reader;
    }

    /**
     * Creates and returns a reader for the specified enum.
     * The created reader is cached and be subsequently accessed using
     * {@link #get(String)}.
     *
     * @param packageName The package name of the enum to read
     * @param className The name of the enum to read
     */
    public static ChunkReader createEnumReader(String packageName, String className) {
        ChunkReader reader = new EnumChunkReaderImpl(packageName, className);
        cacheReader(className, reader);
        return reader;
    }

    private static void cacheReader(String className, ChunkReader reader) {
        sClassReaders.put(className + '.' + className, reader);
    }

    private static abstract class ClassChunkReader implements ChunkReader {
        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }
    }

    private static abstract class CollectionClassChunkReader extends ClassChunkReader
            implements CollectionChunkReader {
        List<TypeElement> mParameters;

        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (chunk.hasByteCount() && !chunk.hasSize()) {
                if (!chunk.hasDynamicByteCount()) {
                    builder.addStatement("byteCount = $L", chunk.byteCount());
                }
                builder.addStatement("in.pushRange(byteCount)", DataInputStream.class);
            }
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (chunk.hasByteCount() && !chunk.hasSize()) {
                builder.addStatement("in.popRange()");
            }
        }

        @Override
        public boolean acceptTypeParameters(List<TypeElement> parameters) {
            mParameters = parameters;
            return true;
        }

        @Override
        public TypeMirror getElementType() {
            // the last parameter works for lists and maps
            return mParameters.get(mParameters.size() - 1).asType();
        }
    }

    private static class ListChunkReaderImpl extends CollectionClassChunkReader {
        @Override
        public boolean acceptTypeParameters(List<TypeElement> parameters) {
            return parameters.size() == 1 && super.acceptTypeParameters(parameters);
        }

        @Override
        public void pushElement(MethodDef.Builder builder, String target, FieldChunk chunk,
                String elementName) {
            builder.addStatement("$L$L.add($L)", target, chunk.name, elementName);
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = new $T<$T>()", target, chunk.name, ArrayList.class,
                    mParameters.get(0).asType());
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            emitRead(builder, target, chunk);
        }
    }

    private static class MapChunkReaderImpl extends CollectionClassChunkReader {
        @Override
        public boolean acceptTypeParameters(List<TypeElement> parameters) {
            if (parameters.size() != 2) {
                return false;
            }

            //noinspection SimplifiableIfStatement
            if (!parameters.get(0).getQualifiedName().toString().equals("java.lang.String")) {
                return false;
            }

            return super.acceptTypeParameters(parameters);
        }

        @Override
        public void pushElement(MethodDef.Builder builder, String target, FieldChunk chunk,
                String elementName) {
            builder.addStatement("$L$L.put(String.valueOf($L), $L)",
                    target, chunk.name, chunk.key(), elementName);
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = new $T<$T, $T>()", target, chunk.name, HashMap.class,
                    mParameters.get(0).asType(), mParameters.get(1).asType());
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            emitRead(builder, target, chunk);
        }
    }

    private static class EnumChunkReaderImpl implements ChunkReader, EnumChunkReader {
        private final TypeDef mType;
        private boolean mSkip;
        private long mCount;

        private EnumChunkReaderImpl(String packageName, String className) {
            mType = TypeDef.fromClass(packageName, className);
        }

        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (mSkip) {
                builder.addStatement("$T.skip(in, $L)", ChunkUtils.class, mCount - 4);
            }
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            mSkip = false;
            mCount = chunk.byteCount();

            switch ((int) mCount) {
                case 1:
                    expression = "in.readUnsignedByte()";
                    break;
                case 2:
                    expression = "in.readUnsignedShort()";
                    break;
                case -1:
                case 4:
                    expression = "in.readInt()";
                    break;
                default:
                    expression = "in.readInt()";
                    mSkip = true;
            }
            builder.addStatement("$L$L = $T.values()[$L]", target, chunk.name, mType, expression);
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = $T.values()[byteCount]", target, chunk.name, mType);
        }
    }

    private static class ChunkedChunkReaderImpl extends ClassChunkReader {
        private final TypeDef mType;

        private ChunkedChunkReaderImpl(String packageName, String className) {
            mType = TypeDef.fromClass(packageName, className);
        }

        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (chunk.hasByteCount()) {
                if (!chunk.hasDynamicByteCount()) {
                    builder.addStatement("byteCount = $L", chunk.byteCount());
                }
                builder.addStatement("in.pushRange(byteCount)");
            }
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (chunk.hasByteCount()) {
                builder.addStatement("in.popRange()");
            }
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            long byteCount = chunk.byteCount();
            if (byteCount > 0) {
                emitRangedRead(builder, target, chunk);
            } else {
                builder.addStatement("$L$L = $T.read(in, stack)", target, chunk.name, mType);
            }
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            emitRangedRead(builder, target, chunk);
        }

        private void emitRangedRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = $T.read(in, stack)",
                    target, chunk.name, mType);
        }
    }

    private static class StringChunkReaderImpl extends ClassChunkReader {
        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = $T.readString(in, $L, $T.forName($S))", target,
                    chunk.name, ChunkUtils.class, chunk.byteCount(), Charset.class, chunk.encoding());
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = $T.readString(in, byteCount, $T.forName($S))",
                    target, chunk.name, ChunkUtils.class, Charset.class, chunk.encoding());
        }
    }

    private static class ObjectChunkReaderImpl extends VoidChunkReaderImpl
            implements DynamicTypeChunkReader {
        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (chunk.hasByteCount()) {
                if (!chunk.hasDynamicByteCount()) {
                    builder.addStatement("byteCount = $L", chunk.byteCount());
                }
                builder.addStatement("in.pushRange(byteCount)");
            }
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
            if (chunk.hasByteCount()) {
                builder.addStatement("in.popRange()");
            }
        }
    }

    private static class VoidChunkReaderImpl extends ClassChunkReader {
        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.add("/* $L$L */\n", target, chunk.name);
            builder.addStatement("$T.skip(in, $L)", ChunkUtils.class, chunk.byteCount());
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.add("/* $L$L */\n", target, chunk.name);
            builder.addStatement("$T.skip(in, byteCount)", ChunkUtils.class);
        }
    }

    private static abstract class PrimitiveChunkReaderImpl
            implements ChunkReader, PrimitiveChunkReader {
        private String mPrimitive;

        private PrimitiveChunkReaderImpl(String primitive) {
            mPrimitive = primitive;
        }

        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target,
                FieldChunk chunk) {
            builder.addStatement("$L$L = $T.read$L(in, byteCount)", target, chunk.name,
                    ChunkUtils.class, mPrimitive);
        }

        void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk,
                String expression, boolean skip, long count) {
            builder.addStatement("$L$L = $L", target, chunk.name, expression);
            if (skip) {
                builder.addStatement("$T.skip(in, $L)", ChunkUtils.class, count);
            }
        }
    }

    private static class BooleanChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private BooleanChunkReaderImpl() {
            super("Boolean");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case -1:
                case 1:
                    expression = "in.readByte() != 0";
                    break;
                case 2:
                    expression = "in.readShort() != 0";
                    break;
                case 4:
                    expression = "in.readInt() != 0";
                    break;
                case 8:
                    expression = "in.readLong() != 0";
                    break;
                default:
                    expression = "in.readByte() != 0";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 1);
        }
    }

    private static class ByteChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private ByteChunkReaderImpl() {
            super("Byte");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case -1:
                case 1:
                    expression = "in.readByte()";
                    break;
                default:
                    expression = "in.readByte()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 1);
        }
    }


    private static class CharChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private CharChunkReaderImpl() {
            super("Char");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case 1:
                    expression = "(char) (in.readByte() & 0xff)";
                    break;
                case -1:
                case 2:
                    expression = "in.readChar()";
                    break;
                default:
                    expression = "in.readChar()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 2);
        }
    }

    private static class DoubleChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private DoubleChunkReaderImpl() {
            super("Double");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case 4:
                    expression = "in.readFloat()";
                    break;
                case -1:
                case 8:
                    expression = "in.readDouble()";
                    break;
                default:
                    expression = "in.readDouble()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 8);
        }
    }

    private static class FloatChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private FloatChunkReaderImpl() {
            super("Float");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case -1:
                case 4:
                    expression = "in.readFloat()";
                    break;
                default:
                    expression = "in.readFloat()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 4);
        }
    }

    private static class IntChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private IntChunkReaderImpl() {
            super("Int");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case 1:
                    expression = "in.readUnsignedByte()";
                    break;
                case 2:
                    expression = "in.readUnsignedShort()";
                    break;
                case -1:
                case 4:
                    expression = "in.readInt()";
                    break;
                default:
                    expression = "in.readInt()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 4);
        }
    }

    private static class LongChunkReaderImpl extends PrimitiveChunkReaderImpl {
        private LongChunkReaderImpl() {
            super("Long");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case 1:
                    expression = "in.readUnsignedByte()";
                    break;
                case 2:
                    expression = "in.readUnsignedShort()";
                    break;
                case 4:
                    expression = "in.readInt() & 0xffffffffL";
                    break;
                case -1:
                case 8:
                    expression = "in.readLong()";
                    break;
                default:
                    expression = "in.readLong()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 8);
        }
    }

    private static class ShortChunkReaderImpl extends PrimitiveChunkReaderImpl {
        ShortChunkReaderImpl() {
            super("Short");
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            String expression;
            boolean skip = false;
            long count = chunk.byteCount();

            switch ((int) count) {
                case 1:
                    expression = "(short) (in.readByte() & 0xff)";
                    break;
                case -1:
                case 2:
                    expression = "in.readShort()";
                    break;
                default:
                    expression = "in.readShort()";
                    skip = true;
            }

            emitRead(builder, target, chunk, expression, skip, count - 2);
        }
    }

    private static class ByteArrayChunkReaderImpl implements ChunkReader, ArrayChunkReader {
        @Override
        public void emitPrologue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitEpilogue(MethodDef.Builder builder, String target, FieldChunk chunk) {
        }

        @Override
        public void emitRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            long count = chunk.byteCount();
            if (count >= 0) {
                builder.addStatement("$L$L = $T.readByteArray(in, $L)", target, chunk.name,
                        ChunkUtils.class, count);
            } else {
                builder.addStatement("$L$L = $T.readUnboundedByteArray(in, $L)", target,
                        chunk.name, ChunkUtils.class, chunk.bufferSize());
            }
        }

        @Override
        public void emitDynamicRead(MethodDef.Builder builder, String target, FieldChunk chunk) {
            builder.addStatement("$L$L = $T.readByteArray(in, byteCount, $L)", target, chunk.name,
                    ChunkUtils.class, chunk.bufferSize());
        }
    }
}
