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

package com.example.basic;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * reflective API user.
 */
public class ReflectiveUser {

    public String value;

    public ReflectiveUser() {
    }

    public ReflectiveUser(String useReflection)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method hiddenGem = getClass().getDeclaredMethod("hiddenGem", String.class);
        value = (String) hiddenGem.invoke(this, "desserts");
    }

    public boolean amIWhoIThinkIam() {
        return getClass().equals(ReflectiveUser.class);
    }

    // this should never be invoked as the use of reflection is blacklisted.
    public String useReflectionOnPublicMethod()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method hiddenGem = getClass().getDeclaredMethod("hiddenGem", String.class);
        return (String) hiddenGem.invoke(this, "desserts");
    }

    public String hiddenGem(String param) {
        return new StringBuilder(param).reverse().toString();
    }

    public String noReflectionUse() {
        return hiddenGem("desserts");
    }
}