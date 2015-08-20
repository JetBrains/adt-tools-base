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

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Class with all types of access methods.
 */
public class AllAccessMethods {

    private String privateMethod() {
        return "private_method";
    }

    protected String protectedMethod() {
        return "protected_method";
    }

    String packagePrivateMethod() {
        return "package_private_method";
    }

    public String publicMethod() {
        return "public_method";
    }

    private String privateMethodDispath() {
        return privateMethod();
    }

    protected String protectedMethodDispatch() {
        return protectedMethod();
    }

    String packagePrivateMethodDispatch() {
        return packagePrivateMethod();
    }

    public String publicMethodDispatch() {
        return publicMethod();
    }

    // methods on the super class to check the overriden methods are invoked property from the
    // super class methods.
    private String doNotOverridePrivateMethodDispath() {
        return privateMethod();
    }

    protected String doNotOverrideProtectedMethodDispatch() {
        return protectedMethod();
    }

    String doNotOverridePackagePrivateMethodDispatch() {
        return packagePrivateMethod();
    }

    public String doNotOverridePublicMethodDispatch() {
        return publicMethod();
    }

    public List<String> invokeAll() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(privateMethod());
        builder.add(protectedMethod());
        builder.add(packagePrivateMethod());
        builder.add(publicMethod());
        return builder.build();
    }

    public List<String> invokeAllDispatches() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(privateMethodDispath());
        builder.add(protectedMethodDispatch());
        builder.add(packagePrivateMethodDispatch());
        builder.add(publicMethodDispatch());
        return builder.build();
    }

    public List<String> invokeAllDoNotOverrideDispatches() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(doNotOverridePrivateMethodDispath());
        builder.add(doNotOverrideProtectedMethodDispatch());
        builder.add(doNotOverridePackagePrivateMethodDispatch());
        builder.add(doNotOverridePublicMethodDispatch());
        return builder.build();
    }
}
