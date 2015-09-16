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
 * Class to test static and static final fields.
 */
public class AllAccessStaticFields {

    private static int privateInt = 13;
    protected static int protectedInt = 26;
    static int packagePrivateInt = 39;
    public static int publicInt = 52;

    private static final int finalPrivateInt = 15;
    protected static final int finalProtectedInt = 30;
    static final int finalPackagePrivateInt = 45;
    public static final int finalPublicInt = 60;

    // reverse order
    public static String staticAccessAllFields() {
        return String.valueOf(publicInt) +
                packagePrivateInt +
                protectedInt +
                privateInt;
    }

    public static void staticSetAllFields(
            int privateInt, int protectedInt, int packagePrivateInt, int publicInt) {

        AllAccessStaticFields.privateInt = privateInt;
        AllAccessStaticFields.protectedInt = protectedInt;
        AllAccessStaticFields.packagePrivateInt = packagePrivateInt;
        AllAccessStaticFields.publicInt = publicInt;
    }


    // don't reverse order, add one.
    @SuppressWarnings("MethodMayBeStatic")
    public String accessAllFields() {
        return String.valueOf(publicInt + 1) +
                (packagePrivateInt + 1) +
                (protectedInt + 1) +
                (privateInt +1);
    }

    @SuppressWarnings("AccessStaticViaInstance")
    public void setAllFields(
            int privateInt, int protectedInt, int packagePrivateInt, int publicInt) {

        this.privateInt = 2*privateInt;
        this.protectedInt = 2*protectedInt;
        this.packagePrivateInt = 2*packagePrivateInt;
        this.publicInt = 2*publicInt;
    }

    // reverse order.
    public static String staticAccessAllFinalFields() {
        return String.valueOf(finalPublicInt) +
                finalPackagePrivateInt +
                finalProtectedInt +
                finalPrivateInt;
    }

    // no change.
    @SuppressWarnings("MethodMayBeStatic")
    public String accessAllFinalFields() {
        return String.valueOf(finalPrivateInt) +
                finalProtectedInt +
                finalPackagePrivateInt +
                finalPublicInt;
    }
}
