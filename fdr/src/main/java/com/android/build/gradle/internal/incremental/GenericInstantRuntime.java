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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic Instant Run services. must not depend on Android APIs.
 *
 * TODO: transform this static methods into interface/implementation.
 */
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

    public static Object getPrivateField(Object target, String name) {
        try {
            Field declaredField = getFieldByName(target.getClass(), name);
            if (declaredField == null) {
                throw new RuntimeException(new NoSuchFieldException(name));
            }
            declaredField.setAccessible(true);
            return declaredField.get(target);
        } catch (IllegalAccessException e) {
            if (logging != null) {
                logging.log(Level.SEVERE, String.format("Exception during getPrivateField %s", name), e);
            }
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
            if (logging != null) {
                logging.log(Level.SEVERE,
                        String.format("Exception during setPrivateField %s", name), e);
            }
            throw new RuntimeException(e);
        }
    }

    public static Object invokeProtectedMethod(Object target, String name, String[] parameterTypes,
            Object[] params) {

        if (logging!=null && logging.isLoggable(Level.FINE)) {
            logging.log(Level.FINE, String.format("protectedMethod:%s on %s", name, target));
        }
        Class[] paramTypes = new Class[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            BasicType basicType = BasicType.parse(parameterTypes[i]);
            if (basicType != null) {
                paramTypes[i] = basicType.getJavaType();
            } else {
                try {
                    paramTypes[i] = target.getClass().getClassLoader().loadClass(parameterTypes[i]);
                } catch (ClassNotFoundException e) {
                    logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
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
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            logging.log(Level.SEVERE, String.format("Exception while invoking %s", name), e);
            throw new RuntimeException(e);
        }
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
