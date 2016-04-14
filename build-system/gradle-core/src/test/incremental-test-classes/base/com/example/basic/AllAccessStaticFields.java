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

    private static int privateInt = 12;
    protected static int protectedInt = 24;
    static int packagePrivateInt = 36;
    public static int publicInt = 48;

    private static final int finalPrivateInt = 14;
    protected static final int finalProtectedInt = 28;
    static final int finalPackagePrivateInt = 42;
    public static final int finalPublicInt = 56;

    public static String staticAccessAllFields() {
        System.out.println(Object.class);
        return String.valueOf(privateInt) +
                protectedInt +
                packagePrivateInt +
                publicInt;
    }

    public static void staticSetAllFields(
            int privateInt, int protectedInt, int packagePrivateInt, int publicInt) {

        AllAccessStaticFields.privateInt = privateInt;
        AllAccessStaticFields.protectedInt = protectedInt;
        AllAccessStaticFields.packagePrivateInt = packagePrivateInt;
        AllAccessStaticFields.publicInt = publicInt;
    }


    @SuppressWarnings("MethodMayBeStatic")
    public String accessAllFields() {
        return String.valueOf(privateInt) +
                protectedInt +
                packagePrivateInt +
                publicInt;
    }

    @SuppressWarnings("AccessStaticViaInstance")
    public void setAllFields(
            int privateInt, int protectedInt, int packagePrivateInt, int publicInt) {

        this.privateInt = privateInt;
        this.protectedInt = protectedInt;
        this.packagePrivateInt = packagePrivateInt;
        this.publicInt = publicInt;
    }

    public static String staticAccessAllFinalFields() {
        return String.valueOf(finalPrivateInt) +
                finalProtectedInt +
                finalPackagePrivateInt +
                finalPublicInt;
    }

    @SuppressWarnings("MethodMayBeStatic")
    public String accessAllFinalFields() {
        return String.valueOf(finalPrivateInt) +
                finalProtectedInt +
                finalPackagePrivateInt +
                finalPublicInt;
    }
}
