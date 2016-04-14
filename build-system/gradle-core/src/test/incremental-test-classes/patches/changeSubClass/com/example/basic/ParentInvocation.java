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
 * Tests related to invoking parent methods from a subclass.
 */
public class ParentInvocation extends AllAccessMethods {

    public String childMethod(double a, String b, int c) {
        return "patched_child_method:" + a + b + c;
    }

    @Override
    protected String protectedMethod(double a, String b, int c) {
        return super.protectedMethod(a, b, c) + "_child";
    }

    @Override
    String packagePrivateMethod(double a, String b, int c) {
        return super.packagePrivateMethod(a, b, c) + "_child";
    }

    @Override
    public String publicStringMethod(double a, String b, int c) {
        return super.publicStringMethod(a, b, c) + "_child";
    }

    @Override
    public String abstractMethod(double a, String b, int c) {
        return "abstract_patched: " + a + b + c;
    }

    public List<String> invokeAllFromSubclass(double a, String b, int c) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(packagePrivateMethod(a, b, c));
        builder.add(protectedMethod(a, b, c));
        builder.add(publicStringMethod(a, b, c));
        builder.add(abstractMethod(a, b, c));
        return builder.build();
    }

    public List<String> invokeAllParent(double a, String b, int c) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(super.packagePrivateMethod(a, b, c));
        builder.add(super.protectedMethod(a, b, c));
        builder.add(super.publicStringMethod(a, b, c));
        return builder.build();
    }

    public List<String> invokeDoNoOverrideMethodsDirectly(double a, String b, int c) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        //TODO allow calling supers that don't exist on the child class.
        //builder.add(super.doNotOverridePackagePrivateMethodDispatch(a, b, c));
        //builder.add(super.doNotOverrideProtectedMethodDispatch(a, b, c));
        //builder.add(super.doNotOverridePublicMethodDispatch(a, b, c));
        return builder.build();
    }
}
