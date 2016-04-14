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

package com.verifier.tests;

/**
 * Class that test that an unchanged class initializer is not flagged by the verifier.
 */
public class UnchangedClassInitializer1 {

    static final int VALUE;

    int someMethodToChangeStaticInitializerLineNumbers(int i) {
        int k = 0;
        for (int j=0; j<i; j++) {
            k+=j;
        }
        return k;
    }

    static {
        VALUE = 40;
    }
}
