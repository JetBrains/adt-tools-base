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

/**
 * Support for registering patched classes.
 */
public class IncrementalSupportRuntime {

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
}
