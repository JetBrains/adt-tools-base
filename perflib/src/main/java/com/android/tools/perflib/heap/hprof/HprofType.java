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

package com.android.tools.perflib.heap.hprof;

public class HprofType {
    public static final byte TYPE_OBJECT = 2;
    public static final byte TYPE_BOOLEAN = 4;
    public static final byte TYPE_CHAR = 5;
    public static final byte TYPE_FLOAT = 6;
    public static final byte TYPE_DOUBLE = 7;
    public static final byte TYPE_BYTE = 8;
    public static final byte TYPE_SHORT = 9;
    public static final byte TYPE_INT = 10;
    public static final byte TYPE_LONG = 11;

    public static int sizeOf(byte type, int idSize) {
        switch (type) {
            case TYPE_OBJECT: return idSize;
            case TYPE_BOOLEAN: return 1;
            case TYPE_CHAR: return 2;
            case TYPE_FLOAT: return 4;
            case TYPE_DOUBLE: return 8;
            case TYPE_BYTE: return 1;
            case TYPE_SHORT: return 2;
            case TYPE_INT: return 4;
            case TYPE_LONG: return 8;
            default: throw new IllegalArgumentException("Invalid type: " + type);
        }
    }
}
