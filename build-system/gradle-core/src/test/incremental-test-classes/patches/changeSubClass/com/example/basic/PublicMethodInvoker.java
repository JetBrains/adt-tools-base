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

package com.example.basic;

import com.google.common.base.Joiner;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Invoke public methods on some other objects.
 */
public class PublicMethodInvoker {

    private final int myField;

    public PublicMethodInvoker(int myField) {
        this.myField = 2*myField;
    }

    // add tests for all types of parameters and return types.
    public String invokeAllPublicMethods(AllAccessMethods allAccessMethods) {
        StringBuilder result = new StringBuilder();
        result.append(allAccessMethods.publicStringMethod(1d, "invoker_reloaded", 12));
        result.append('-');
        result.append(allAccessMethods.publicIntMethod(1));
        result.append('-');
        result.append(allAccessMethods.publicLongMethod(3));
        result.append('-');
        result.append(allAccessMethods.publicFloatMethod(12f));
        result.append('-');
        result.append(allAccessMethods.publicBooleanMethod(false));
        result.append('-');
        result.append(allAccessMethods.publicDoubleMethod(24.12d));
        result.append('-');
        result.append(allAccessMethods.publicCharMethod('c'));
        result.append('-');
        result.append(myField);
        allAccessMethods.voidMethod();
        return result.toString();
    }


    public String invokeAllPublicArrayMethods(AllAccessMethods allAccessMethods) {
        StringBuilder result = new StringBuilder();
        result.append(Joiner.on(':').join(
                allAccessMethods.publicStringArrayMethod(new String[] {"invoker"})));
        result.append('-');
        result.append(Joiner.on(':').join(
                Ints.asList(allAccessMethods.publicIntArrayMethod(new int[] {5}))));
        result.append('-');
        result.append(Joiner.on(':').join(
                Longs.asList(allAccessMethods.publicLongArrayMethod(new long[] {3}))));
        result.append('-');
        result.append(Joiner.on(':').join(
                Chars.asList(allAccessMethods.publicCharArrayMethod(new char[] {'a'}))));
        result.append('-');
        result.append(Joiner.on(':').join(
                Booleans.asList(allAccessMethods.publicBooleanArrayMethod(new boolean[] {false}))));
        result.append('-');
        result.append(Joiner.on(':').join(
                Floats.asList(allAccessMethods.publicFloatArrayMethod(new float[] {12f}))));
        result.append('-');
        result.append(Joiner.on(':').join(
                Doubles.asList(allAccessMethods.publicDoubleArrayMethod(new double[] {56d}))));
        result.append('-');
        result.append(myField);
        allAccessMethods.voidMethod();
        return result.toString();
    }
}