package com.android.tests.basic;

import java.lang.RuntimeException;
import java.lang.String;
import java.lang.reflect.Method;

public class StringGetter{

    public static String getString(int foo) {
         return getStringInternal(foo);
    }

    public static String getStringInternal(int foo) {
        try {
            // use reflection to make sure the class wasn't obfuscated
            Class<?> theClass = Class.forName("com.android.tests.basic.StringProvider");
            Method method = theClass.getDeclaredMethod("getString", int.class);
            return (String) method.invoke(null, foo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
