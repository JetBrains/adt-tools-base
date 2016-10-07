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

package com.android.tools.chunkio;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

public final class ChunkIO {
    private static final WeakHashMap<Class<?>, Method> sReaders = new WeakHashMap<>();

    private ChunkIO() {
    }

    /**
     * Reads an instance of the specified class from an input stream.
     * The specified class can be read from the stream only if it
     * declares the {@literal @}Chunked annotation. See {@link Chunked}
     * and {@link Chunk} for more information.
     *
     * @param in The input stream to read a class instance from
     * @param type The class whose instance to read from the stream
     *
     * @return An instance of type
     *
     * @throws ChunkException If an error occurred while reading
     * from the stream
     */
    public static <T> T read(InputStream in, Class<T> type) throws ChunkException {
        Method method = sReaders.get(type);
        if (method == null) {
            List<String> names = new ArrayList<>();
            for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
                names.add(c.getSimpleName().replace('.', '$'));
            }

            Collections.reverse(names);
            String className = type.getPackage().getName() + '.' +
                    ChunkUtils.join(names, "$") + "$$ChunkIO";

            try {
                Class<?> reader = Class.forName(className);
                method = reader.getDeclaredMethod("read", RangedInputStream.class, LinkedList.class);
                method.setAccessible(true);
                sReaders.put(type, method);
            } catch (ClassNotFoundException e) {
                throw new ChunkException("Could not find the decoder for type " + type, e);
            } catch (NoSuchMethodException e) {
                throw new ChunkException("Could not find the read() method for type " + type, e);
            }
        }

        try {
            //noinspection unchecked
            return (T) method.invoke(null, new RangedInputStream(in), new LinkedList<>());
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new ChunkException("Could not invoke the read() method for type " + type, e);
        }
    }
}
