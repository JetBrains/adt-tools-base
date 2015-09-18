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

package com.android.builder.shrinker.parser;

/**
 * Java modifiers recognized by ProGuard class specifications.
 */
public class Modifier {
    public static final int DEFAULT = 0;
    public static final int PUBLIC       = 0x0001;
    public static final int PRIVATE      = 0x0002;
    public static final int PROTECTED    = 0x0004;
    public static final int STATIC       = 0x0008;
    public static final int FINAL        = 0x0010;
    public static final int SYNCHRONIZED = 0x0020;
    public static final int VOLATILE     = 0x0040;
    public static final int BRIDGE       = 0x0040;
    public static final int TRANSIENT    = 0x0080;
    public static final int VARARGS      = 0x0080;
    public static final int NATIVE       = 0x0100;
    public static final int ABSTRACT     = 0x0200;
    public static final int STRICTFP     = 0x0800;
    public static final int SYNTHETIC    = 0x1000;
}
