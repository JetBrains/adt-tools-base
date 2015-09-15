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

/**
 * Provide static methods with all access types.
 */
public class AllAccessStaticMethods {

    public static int getPublicStaticInt() {
        return 1;
    }

    protected static int getProtectedStaticInt() {
        return 3;
    }

    static int getPackagePrivateStaticInt() {
        return 5;
    }

    public static String getPublicStaticString() {
        return "public";
    }

    protected static String getProtectedStaticString() {
        return "protected";
    }

    static String getPackagePrivateStaticString() {
        return "package_private";
    }

    public static double[] getPublicStaticDoubles() {
        return new double[]{1d, 4d};
    }

    protected static double[] getProtectedStaticDoubles() {
        return new double[0];
    }

    static double[] getPackagePrivateStaticDoubles() {
        return null;
    }

    public static String[] getPublicStaticStrings() {
        return new String[] { "public", "string"};
    }

    protected static String[] getProtectedStaticStrings() {
        return new String[0];
    }

    static String[] getPackagePrivateStrings() {
        return new String[] { "package", "private", "string"};
    }
}
