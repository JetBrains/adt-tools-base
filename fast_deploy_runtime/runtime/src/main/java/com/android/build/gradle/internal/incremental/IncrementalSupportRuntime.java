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

import android.util.Log;

import com.android.tools.fd.runtime.BootstrapApplication;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;

/**
 * Support for registering patched classes.
 */
public enum IncrementalSupportRuntime {

    INSTANCE;

    public final Map<String, Class<?>> patchedClasses = new HashMap<String, Class<?>>();

    public boolean isPatched(String className) {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, String.format("Asking if %s is patched : %b", className,
                    patchedClasses.containsKey(className)));
        }
        return patchedClasses.containsKey(className);
    }

    public Class<?> getPatchedClass(String className) {
        return patchedClasses.get(className);
    }

    public static IncrementalSupportRuntime get() {
        return INSTANCE;
    }

    public static Object dispatch(Object target, String methodName, String signature, Object... parameters) {
        try {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, String.format("Invoking %s with signature %s", methodName, signature));
            }
            List<Class> paramTypes = new ArrayList<Class>();
            if (parameters.length > 0) {
                paramTypes = extractParameterTypes(target.getClass().getClassLoader(), signature);
            }

            paramTypes.add(0, target.getClass());
            Class patchedClass = INSTANCE.getPatchedClass(target.getClass().getName().replaceAll("\\.", "/"));
            if (patchedClass == null) {
                throw new RuntimeException("No patched class...");
            }
            Method method = patchedClass.getDeclaredMethod(methodName, paramTypes.toArray(new Class[paramTypes.size()]));
            if (!Modifier.isStatic(method.getModifiers())) {
                method = null;
                for (Method m : target.getClass().getMethods()) {
                    if (m.getName().equals(methodName) && Modifier.isStatic(m.getModifiers())) {
                        method = m;
                        break;
                    }
                }
            }
            Object[] newParameters = new Object[parameters.length + 1];
            newParameters[0] = target;
            for (int i=0; i < parameters.length; i++) {
                newParameters[i+1] = parameters[i];
            }
            if (method != null) {
                return method.invoke(null, newParameters);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static List<Class> extractParameterTypes(ClassLoader cl, String signature)
            throws ClassNotFoundException {
        List<Class> paramTypes = new ArrayList<Class>();
        String argumentDeclaration = signature.substring(1, signature.indexOf(')'));

        while(!argumentDeclaration.isEmpty()) {
            if (argumentDeclaration.charAt(0) == 'L') {

                String argumentType = argumentDeclaration.substring(1, argumentDeclaration.indexOf(';'));
                argumentDeclaration = argumentDeclaration.substring(argumentDeclaration.indexOf(';') + 1);
                paramTypes.add(cl.loadClass(argumentType.replaceAll("/", ".")));
            } else {
                BasicType basicType = BasicType.valueOf(argumentDeclaration.substring(0, 1));
                argumentDeclaration = argumentDeclaration.substring(1);
                paramTypes.add(basicType.getJavaType());
            }
        }
        return paramTypes;
    }

    public static Object getPrivateField(Object target, String name) {
        try {
            Field declaredField = target.getClass().getDeclaredField(name);
            declaredField.setAccessible(true);
            return declaredField.get(target);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void setPrivateField(Object target, String name, Object value) {
        try {
            Field declaredField = target.getClass().getDeclaredField(name);
            declaredField.setAccessible(true);
            declaredField.set(target, value);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void addPatchedClass(String s, Class<?> enhancedClass) {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, String.format("Registering class patch %s", s));
        }
        patchedClasses.put(s, enhancedClass);
    }


}
