package com.android.tests.basic;

import com.android.tests.internal.StringGetterInternal;

public class StringGetter{

    /**
     * Public method that will not get obfuscated
     */
    public static String getString(int foo) {
         return StringGetterInternal.getString(foo);
    }

    /**
     * Public method that will get obfuscated by the app project.
     */
    public static String getString2(int foo) {
        return StringGetterInternal.getString(foo);
    }

    /**
     * method that will get obfuscated by the library.
     */
    public static String getString3(int foo) {
        return StringGetterInternal.getString(foo);
    }
}
