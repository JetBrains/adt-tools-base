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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API used to easily decode binary files by describing their content
 * using only classes and fields. This annotation is meant to be used
 * on a field to tell the IO engine how to read the source stream.
 * For instance, here is how to use this annotation to read a 4-byte
 * String, an unsigned int and a float from a stream:
 *
 * <pre>
 * {@literal @}Chunked
 * class MyData {
 *     {@literal @}Chunk(byteCount = 4)
 *     String signature;
 *     {@literal @}Chunk(byteCount = 4)
 *     long unsignedInt;
 *     {@literal @}Chunk
 *     float myFloat;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Chunked {
}
