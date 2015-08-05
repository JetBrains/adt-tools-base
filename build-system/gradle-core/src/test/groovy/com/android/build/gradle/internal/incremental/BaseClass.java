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

/**
 * Created by jedo on 7/30/15.
 */
public class BaseClass {

    public static void load() {
        System.out.println("1");
        IncrementalSupportRuntime runtime = IncrementalSupportRuntime.get();
        ClassLoader loader = BaseClass.class.getClassLoader();
        System.out.println("loaded runtime " + 2);
        try {
            Class<?> aClass = loader.loadClass("foo.bar");
            runtime.addPatchedClass("foo.bar".replaceAll("\\.", "/"), aClass);
            System.out.println("patched with " + aClass);
            aClass = loader.loadClass("bar.foo");
            runtime.addPatchedClass("bar.foo".replaceAll("\\.", "/"), aClass);
            System.out.println("patched with " + aClass);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    void methodA(int a) {
    }
}
