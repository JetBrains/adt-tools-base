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
@Target(ElementType.FIELD)
public @interface Chunk {
    /**
     * Specifies the number of bytes to read to decode the annotated
     * field. This value is optional.
     */
    long byteCount() default -1;
    /**
     * Specifies the number of bytes to read to decode the annotated
     * field. The number of bytes is computed by interpreting this
     * String as an expression. If the expression contains multiple
     * lines separated by \n characters, the last line must assign
     * a value to the "byteCount" variable. Assignment is automatic
     * for single line expressions. This value is optional.
     */
    String dynamicByteCount() default "";
    /**
     * List of class parameters to inject in the dynamicByteCount()
     * expression. Each parameter should map to a $T token in the
     * expression. Injected class parameters will be properly
     * imported on your behalf in the generated code.
     * For instance:
     * <pre>
     * {@literal @}Chunk(
     *     dynamicByteCount = "new $T().computeByteCount()"
     *     btyeCountParams = { MyClass.class }
     * )
     * </pre>
     */
    Class<?>[] byteCountParams() default { };

    /**
     * Specifies the number of items to read to populate a list.
     * This parameter only works for fields of type java.util.List.
     * If the expression contains multiple lines separated by \n
     * characters, the last line must assign a value to the "size"
     * variable. Assignment is automatic for single line expressions.
     * This value is optional.
     */
    int size() default -1;
    /**
     * Specifies the number of items to read to populate a list.
     * The number of items to read is computed by interpreting this
     * String as an expression.
     * This parameter only works for fields of type java.util.List.
     * This value is optional.
     */
    String dynamicSize() default "";
    /**
     * List of class parameters to inject in the dynamicSize()
     * expression. Each parameter should map to a $T token in the
     * expression. Injected class parameters will be properly
     * imported on your behalf in the generated code.
     * For instance:
     * <pre>
     * {@literal @}Chunk(
     *     dynamicSize = "new $T().computeSize()"
     *     sizeParams = { MyClass.class }
     * )
     * </pre>
     */
    Class<?>[] sizeParams() default { };

    /**
     * Indicates that the field's value must match the interpreted
     * expression. This value is optional.
     */
    String match() default "";

    /**
     * Can be used to change the type of the field based on
     * conditions. This parameter only makes sense when the field's
     * type is set to Object. If all the cases fail, the appropriate
     * number of bytes is skipped. This value is optional.
     */
    Case[] switchType() default { };

    /**
     * Specifies an expression that returns a boolean.
     * If the boolean is true, the field is decoded from the source.
     * If false, the field is ignored. This value is optional.
     */
    String readIf() default "";
    /**
     * List of class parameters to inject in the readIf()
     * expression. Each parameter should map to a $T token in the
     * expression. Injected class parameters will be properly
     * imported on your behalf in the generated code.
     * For instance:
     * <pre>
     * {@literal @}Chunk(
     *     readIf = "new $T().predicate()"
     *     readIfParams = { MyClass.class }
     * )
     * </pre>
     */
    Class<?>[] readIfParams() default { };

    /**
     * Interrupts the source decoding process if this
     * boolean expression returns true. This value is optional.
     */
    String stopIf() default "";
    /**
     * List of class parameters to inject in the stopIf()
     * expression. Each parameter should map to a $T token in the
     * expression. Injected class parameters will be properly
     * imported on your behalf in the generated code.
     * For instance:
     * <pre>
     * {@literal @}Chunk(
     *     stopIf = "new $T().predicate()"
     *     stopIfParams = { MyClass.class }
     * )
     * </pre>
     */
    Class<?>[] stopIfParams() default { };

    /**
     * Specifies the encoding for fields of String type.
     * This value is set to ISO-8859-1 by default.
     */
    String encoding() default "ISO-8859-1";

    /**
     * An expression that returns the key for each
     * item when decoding a java.util.Map field.
     * This value is mandatory for fields of type Map.
     */
    String key() default "";

    /**
     * If set to true, the value will be printed right after
     * decoding.
     */
    boolean debug() default false;

    /**
     * Size of the intermediate buffer used to read unbounded
     * byte arrays. The default is 4K.
     */
    int bufferSize() default 4096;

    @Retention(RetentionPolicy.SOURCE)
    @interface Case {
        /**
         * Boolean expression. If true, the field is read as a
         * value of type type(). Mandatory.
         */
        String test();

        /**
         * The type of the field if the test passes. Mandatory.
         */
        Class<?> type();
    }
}
