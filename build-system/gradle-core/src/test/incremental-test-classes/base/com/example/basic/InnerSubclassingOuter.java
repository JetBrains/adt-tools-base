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
 * Semi public inner class calling its parent (also its outer class !) private constructor.
 */
class InnerSubclassingOuter {

    final int value;

    private InnerSubclassingOuter(int value) {
        this.value = value;
    }

    int getValue() {
        return value;
    }

    static class Innerclass extends InnerSubclassingOuter {
        final String field;
        Innerclass(int value, String field) {
            super(value);
            this.field = field;
        }

        String getField() {
            return field;
        }
    }
}
