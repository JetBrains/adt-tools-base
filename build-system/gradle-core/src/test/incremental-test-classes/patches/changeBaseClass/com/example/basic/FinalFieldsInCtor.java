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
 * Class that sets a final fields in ctor
 */
public class FinalFieldsInCtor {

    public final String publicField;
    protected final String protectedField;
    private final String privateField;
    final String packagePrivateField;

    public FinalFieldsInCtor(String packagePrivateField, String privateField,
            String protectedField, String publicField) {
        this.packagePrivateField = "modified " + packagePrivateField;
        this.privateField = "modified " + privateField;
        this.protectedField = "modified " + protectedField;
        this.publicField = "modified " + publicField;
    }

    public String getPublicField() {
        return "method " + publicField;
    }

    public String getProtectedField() {
        return "method " + protectedField;
    }

    public String getPrivateField() {
        return "method " + privateField;
    }

    public String getPackagePrivateField() {
        return "method " + packagePrivateField;
    }
}
