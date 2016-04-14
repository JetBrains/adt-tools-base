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
 * Package Private class with PackagePrivate methods.
 */
class PackagePrivateClass implements Provider<String> {

    private final String stringValue;
    final String packagePrivateValue;
    PackagePrivateClass(String param) {
        this.stringValue = param;
        packagePrivateValue = new StringBuilder().append(param).reverse().toString();
    }

    String getStringValue() {
        return stringValue;
    }

    @Override
    public String getValue() {
        return stringValue;
    }

    PackagePrivateInterface getPackagePrivateInterface() {
        return new PackagePrivateInterface() {
            @Override
            public String getValue() {
                return "packagePrivateInterface";
            }
        };
    }
}
