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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by jedo on 7/30/15.
 */
public class ExtendedClass extends BaseClass {

    @Override
    void methodA(int a ) {
        super.methodA(a);
        System.out.println("ExtendedClass");
    }

    public static void callSuper(ExtendedClass instance, String method, String signature, Object[] parameters)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method superMethod = BaseClass.class.getDeclaredMethod("methodA");
        superMethod.invoke(instance, parameters[0]);
    }
}
