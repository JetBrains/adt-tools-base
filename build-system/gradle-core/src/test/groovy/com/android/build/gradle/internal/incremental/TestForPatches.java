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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by jedo on 8/5/15.
 */
public class TestForPatches {

    public static void main(String[] args) {
        try {
            Class<?> aClass = TestForPatches.class.getClassLoader()
                    .loadClass("com.android.build.Patches");
            System.out.println("patches loaded " + aClass);
            Method patchedClassesMethod = aClass.getDeclaredMethod("load");
            if (patchedClassesMethod != null) {
                patchedClassesMethod.invoke(null);
            } else {
                System.out.println("Cannot find patchedClasses");
            }
            for (Map.Entry<String, Class<?>> stringClassEntry : IncrementalSupportRuntime.INSTANCE.patchedClasses
                    .entrySet()) {
                System.out.println("patched " + stringClassEntry.getKey() + " with " + stringClassEntry.getValue());
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

}
