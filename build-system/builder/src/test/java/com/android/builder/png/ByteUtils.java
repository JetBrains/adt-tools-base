/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.google.common.collect.Maps;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * A small util class to do byte conversion.
 *
 * This class allocates a couple of buffers and reuse them. Each new instance
 * gets new buffers.
 *
 * This is not thread-safe.
 */
class ByteUtils {

    @NonNull
    private final ByteBuffer mIntBuffer;
    @NonNull
    private final ByteBuffer mLongBuffer;

    ByteUtils() {
        // ByteBuffer for endian-ness conversion
        mIntBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        mLongBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
    }

    @NonNull
    byte[] getLongAsIntArray(long value) {
        return ((ByteBuffer) mLongBuffer.rewind()).putLong(value).array();
    }

    @NonNull
    byte[] getIntAsArray(int value) {
        return ((ByteBuffer) mIntBuffer.rewind()).putInt(value).array();
    }

    static class Cache {

        private static final Cache sPngCache = new Cache();

        private final Map<Long, ByteUtils> map = Maps.newHashMap();

        @NonNull
        static Cache getCache() {
            return sPngCache;
        }

        synchronized ByteUtils getUtils(long key) {
            ByteUtils utils = map.get(key);
            if (utils == null) {
                utils = new ByteUtils();
                map.put(key, utils);
            }

            return utils;
        }

        static ByteUtils get() {
            return getCache().getUtils(Thread.currentThread().getId());
        }

        synchronized void clear() {
            map.clear();
        }
    }
}
