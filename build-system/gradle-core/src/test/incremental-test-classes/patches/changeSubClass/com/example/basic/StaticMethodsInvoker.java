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

/**
 * Invoker of static methods on itself or on others.
 */
public class StaticMethodsInvoker {

    public int invokeAllStaticIntMethods() {
        return (AllAccessStaticMethods.getPackagePrivateStaticInt()
                + AllAccessStaticMethods.getPublicStaticInt())
                / AllAccessStaticMethods.getProtectedStaticInt();
    }

    public String invokeAllStaticStringMethods() {
        return Joiner.on(',').join(AllAccessStaticMethods.getPublicStaticString(),
                AllAccessStaticMethods.getProtectedStaticString(),
                AllAccessStaticMethods.getPackagePrivateStaticString());
    }

    public double invokeAllStaticDoubleArrayMethods() {
        double toReturn = addAll(AllAccessStaticMethods.getPackagePrivateStaticDoubles());
        toReturn -= addAll(AllAccessStaticMethods.getProtectedStaticDoubles());
        toReturn += addAll(AllAccessStaticMethods.getPublicStaticDoubles());
        return toReturn;
    }

    public String invokeAllStaticStringArrayMethods() {
        return Joiner.on(':').join(
                Joiner.on(',').join(AllAccessStaticMethods.getProtectedStaticStrings()),
                Joiner.on(',').join(AllAccessStaticMethods.getPackagePrivateStrings()),
                Joiner.on(",").join(AllAccessStaticMethods.getPublicStaticStrings()));
    }

    private double addAll(double[] doubles) {
        double accumulator = 0;
        if (doubles!=null) {
            for (double aDouble : doubles) {
                accumulator += aDouble;
            }
        }
        return accumulator;
    }
}