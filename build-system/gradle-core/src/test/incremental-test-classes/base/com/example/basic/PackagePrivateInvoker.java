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
 * Class that uses package private class and methods.
 */
public class PackagePrivateInvoker {

    public String createPackagePrivateObject() {
        PackagePrivateClass packagePrivateClass = new PackagePrivateClass("foo");
        return packagePrivateClass.getStringValue();
    }

    public String invokeTernaryOperator(boolean select) {
        Provider<String> provider = select
                ? new PackagePrivateClass("package_private")
                : new AnotherPackagePrivateClass<String>("another_package_private");
        return provider.getValue();
    }

    public String ternaryOperatorInConstructorParams(boolean select) {
        return new AnotherPackagePrivateClass<String>(select ? "true" : "false").getValue();
    }

    public String invokePackagePrivateInterface() {
        return new PackagePrivateClass("random").getPackagePrivateInterface().getValue();
    }
}
