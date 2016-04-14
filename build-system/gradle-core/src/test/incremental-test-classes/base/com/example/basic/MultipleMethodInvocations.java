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

/**
 * Class with methods delegating multiple times to other methods within the same class or delegate
 * objects.
 */
public class MultipleMethodInvocations {

    public String doSomething(String aString, int aValue, Object something) {
        return aString + "-" + aValue + "-" + something.toString();
    }

    private String doSomethingElse() {
        return "bar";
    }

    public String doAll() {
        return doSomething("foo", 4, "bar") + doSomethingElse();
    }
}
