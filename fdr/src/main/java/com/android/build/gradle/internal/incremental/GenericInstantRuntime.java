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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic Instant Run services. must not depend on Android APIs.
 *
 * TODO: transform this static methods into interface/implementation.
 */
@SuppressWarnings("unused")
public class GenericInstantRuntime {

    protected interface Logging {
        void log(@NonNull Level level, @NonNull String string);

        boolean isLoggable(@NonNull Level level);

        void log(@NonNull Level level, @NonNull String string, @Nullable Throwable throwable);
    }

    protected static Logging logging = null;

    public static void setLogger(final Logger logger) {

        logging = new GenericInstantRuntime.Logging() {
            @Override
            public void log(@NonNull Level level, @NonNull String string) {
                logger.log(level, string);
            }

            @Override
            public boolean isLoggable(@NonNull Level level) {
                return logger.isLoggable(level);
            }

            @Override
            public void log(@NonNull Level level, @NonNull String string,
                    @Nullable Throwable throwable) {
                logger.log(level, string, throwable);
            }
        };
    }

    @Nullable
    public static Object getPrivateField(Object targetObject, String fieldName) {
        return getFieldValue(targetObject.getClass(), targetObject, fieldName);
    }

    @Nullable
    public static void setPrivateField(@NonNull Object targetObject, @NonNull Object value, @NonNull  String fieldName) {
        setFieldValue(targetObject.getClass(), targetObject, value, fieldName);
    }

    @Nullable
    public static Object getStaticPrivateField(Class targetClass, String fieldName) {
        return getFieldValue(targetClass, null /* targetObject */, fieldName);
    }

    @Nullable
    public static void setStaticPrivateField(
            @NonNull Object value, @NonNull Class targetClass, @NonNull  String fieldName) {
        setFieldValue(targetClass, null /* targetObject */, value, fieldName);
    }

    @Nullable
    private static void setFieldValue(
            @NonNull Class targetClass, @Nullable Object targetObject, @Nullable  Object value, @NonNull  String fieldName) {
        try {
            Field declaredField = getField(targetClass, fieldName);
            declaredField.set(targetObject, value);
        } catch (IllegalAccessException e) {
            if (logging != null) {
                logging.log(Level.SEVERE,
                        String.format("Exception during setPrivateField %s", fieldName), e);
            }
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static Object getFieldValue(
            @NonNull Class targetClass,
            @Nullable Object targetObject,
            @NonNull  String fieldName) {
        try {
            Field declaredField = getField(targetClass, fieldName);
            return declaredField.get(targetObject);
        } catch (IllegalAccessException e) {
            if (logging != null) {
                logging.log(Level.SEVERE,
                        String.format("Exception during%1$s getField %2$s",
                                targetObject == null ? " static" : "",
                                fieldName), e);
            }
            throw new RuntimeException(e);
        }
    }


    @NonNull
    private static Field getField(Class target, String name) {
        Field declareField = getFieldByName(target, name);
        if (declareField == null) {
            throw new RuntimeException(new NoSuchElementException(name));
        }
        declareField.setAccessible(true);
        return declareField;
    }

    public static Object invokeProtectedMethod(Object target,
            String name,
            String[] parameterTypes,
            Object[] params) {

        if (logging!=null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE, String.format("protectedMethod:%s on %s", name, target));
        }
        Class[] paramTypes = translateParameterTypes(target.getClass(), parameterTypes);
        try {
            Method toDispatchTo = getMethodByName(target.getClass(), name, paramTypes);
            if (toDispatchTo == null) {
                throw new RuntimeException(new NoSuchMethodException(name));
            }
            toDispatchTo.setAccessible(true);
            return toDispatchTo.invoke(target, params);
        } catch (InvocationTargetException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
            throw new RuntimeException(e);
        }
    }

    public static Object invokeProtectedStaticMethod(Class targetClass,
            String name,
            String[] parameterTypes,
            Object[] params) {

        if (logging!=null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE,
                    String.format("protectedStaticMethod:%s on %s", name, targetClass.getName()));
        }
        Class[] paramTypes = translateParameterTypes(targetClass, parameterTypes);
        try {
            Method toDispatchTo = getMethodByName(targetClass, name, paramTypes);
            if (toDispatchTo == null) {
                throw new RuntimeException(new NoSuchMethodException(
                        name + " in class " + targetClass.getName()));
            }
            toDispatchTo.setAccessible(true);
            return toDispatchTo.invoke(null /* target */, params);
        } catch (InvocationTargetException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
            throw new RuntimeException(e);
        }
    }

    private static Class[] translateParameterTypes(Class targetClass, String[] parameterTypes) {
        Class[] paramTypes = new Class[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            BasicType basicType = BasicType.parse(parameterTypes[i]);
            if (basicType != null) {
                paramTypes[i] = basicType.getJavaType();
            } else {
                try {
                    ClassLoader classLoader = targetClass.getClassLoader() != null
                            ? targetClass.getClassLoader()
                            : GenericInstantRuntime.class.getClassLoader();

                    paramTypes[i] = classLoader.loadClass(parameterTypes[i]);
                } catch (ClassNotFoundException e) {
                    logging.log(Level.SEVERE,
                            String.format("Exception while loading parameter %1$s class",
                                    paramTypes[i]), e);
                }
            }
        }
        return paramTypes;
    }

    private static Field getFieldByName(Class<?> aClass, String name) {

        if (logging!= null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE, String.format("getFieldByName:%s in %s", name, aClass.getName()));
        }

        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // ignored.
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static Method getMethodByName(Class<?> aClass, String name, Class[] paramTypes) {

        if (aClass == null) {
            return null;
        }

        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                // ignored.
            }
            currentClass = currentClass.getSuperclass();
            if (currentClass!= null && logging!=null && logging.isLoggable(Level.FINE)) {
                logging.log(Level.FINE, String.format(
                        "getMethodByName:Looking in %s now", currentClass.getName()));
            }

        }
        return null;
    }

    public static void trace(String s) {
        if (logging != null) {
            logging.log(Level.FINE, s);
        }
    }

    public static void trace(String s1, String s2) {
        if (logging != null) {
            logging.log(Level.FINE, String.format("%s %s", s1, s2));
        }
    }

    public static void trace(String s1, String s2, String s3) {
        if (logging != null) {
            logging.log(Level.FINE, String.format("%s %s %s", s1, s2, s3));
        }
    }

}
