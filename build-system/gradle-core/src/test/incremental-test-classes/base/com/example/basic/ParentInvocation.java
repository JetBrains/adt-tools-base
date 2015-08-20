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

    public String childMethod() {
        return "child_method";
    }

    @Override
    protected String protectedMethod() {
        return super.protectedMethod() + "_child";
    }

    @Override
    String packagePrivateMethod() {
        return super.packagePrivateMethod() + "_child";
    }

    @Override
    public String publicMethod() {
        return super.publicMethod() + "_child";
    }

    public List<String> invokeAllFromSubclass() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(packagePrivateMethod());
        builder.add(protectedMethod());
        builder.add(publicMethod());
        return builder.build();
    }

    public List<String> invokeAllParent() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(super.packagePrivateMethod());
        builder.add(super.protectedMethod());
        builder.add(super.publicMethod());
        return builder.build();
    }
}
