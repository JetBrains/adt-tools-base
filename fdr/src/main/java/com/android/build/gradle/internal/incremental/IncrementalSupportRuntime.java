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

package com.android.build.gradle.internal.incremental;

import java.lang.System;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Support for registering patched classes.
 */
public class IncrementalSupportRuntime {

    public static Object getPrivateField(Object target, String name) {
        try {
            Field declaredField = getFieldByName(target.getClass(), name);
            if (declaredField == null) {
                throw new RuntimeException(new NoSuchFieldException(name));
            }
            declaredField.setAccessible(true);
            return declaredField.get(target);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void setPrivateField(Object target, Object value, String name) {
        try {
            Field declaredField = getFieldByName(target.getClass(), name);
            if (declaredField == null) {
                throw new RuntimeException(new NoSuchFieldException(name));
            }
            declaredField.setAccessible(true);
            declaredField.set(target, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Object invokeProtectedMethod(Object target, String name, String[] parameterTypes,
            Object[] params) {
        System.out.println("invoke protected called");
        Class[] paramTypes = new Class[parameterTypes.length];
        for (int i=0; i<parameterTypes.length; i++) {
            BasicType basicType = BasicType.parse(parameterTypes[i]);
            if (basicType != null) {
                paramTypes[i] = basicType.getJavaType();
            } else {
                try {
                    paramTypes[i] = target.getClass().getClassLoader().loadClass(parameterTypes[i]);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            Method toDispatchTo = getMethodByName(target.getClass(), name, paramTypes);
            if (toDispatchTo == null) {
                throw new RuntimeException(new NoSuchMethodException(name));
            }
            toDispatchTo.setAccessible(true);
            return toDispatchTo.invoke(target, params);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static Field getFieldByName(Class<?> aClass, String name) {
        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    private static Method getMethodByName(Class<?> aClass, String name, Class[] paramTypes) {
        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                currentClass = aClass.getSuperclass();
            }
        }
        return null;
    }

    public static void trace(String s) {
        System.out.println(s);
    }

    public static void trace(String s1, String s2) {
        System.out.println(s1 + s2);
    }

    public static void trace(String s1, String s2, String s3) {
        System.out.println(s1 + s2 + s3);
    }
}
