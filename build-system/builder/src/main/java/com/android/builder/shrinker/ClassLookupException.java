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

package com.android.builder.shrinker;

/**
 * Checked exception thrown by all graph operations that may fail due to invalid class being
 * referenced.
 */
public class ClassLookupException extends Exception {

    private final String mClassName;

    public ClassLookupException(String className) {
        this.mClassName = className;
    }

    public String getClassName() {
        return mClassName;
    }

    @Override
    public String getMessage() {
        return "Invalid class reference: " + mClassName;
    }
}
