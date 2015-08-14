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

public class BaseClass {

    public int baseInt;

    public BaseClass() {
        baseInt = 0xDEAD;
    }

    public BaseClass(int i) {
        baseInt = i;
    }

    protected String protectedField ="protected";
    private String privateField="private";
    String packagePrivateField="package";
    public String publicField="public";

    public int methodA() {
        return 42;
    }

    public String getAll() {
        return publicField + protectedField + packagePrivateField + privateField;
    }

    public String invokeAll() {
        return publicMethod() + privateMethod() + protectedMethod() + packagePrivateMethod();
    }

    protected String protectedMethod() {
        return protectedField;
    }

    String packagePrivateMethod() {
        return protectedField;
    }

    private String privateMethod() {
        return privateField;
    }

    public String publicMethod() {
        return publicField;
    }
}
