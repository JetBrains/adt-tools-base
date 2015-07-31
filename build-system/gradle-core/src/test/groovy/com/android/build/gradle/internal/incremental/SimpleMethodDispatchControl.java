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

package com.android.build.gradle.internal.incremental;

/**
 * Created by jedo on 7/23/15.
 */
public class SimpleMethodDispatchControl extends BaseClass {

    private int field = 4;
    DelegationClass delegate = new DelegationClass();

    private SimpleMethodDispatchControl(){}

    public long getIntValue(int value) {
        if (IncrementalSupportRuntime.INSTANCE.isPatched(
                "com.android.build.gradle.internal.incremental.SimpleMethodDispatchTest")) {
            Object[] parameters = new Object[1];
            parameters[0] = value;
            return (Long) IncrementalSupportRuntime.dispatch(this, "getIntValue", "(I)L", parameters);
        }
        return calculateIntValue(value, 132) / value;
    }

    @Override
    void methodA(int a) {
        super.methodA(a);
    }

    public long calculateIntValue(Integer value, int other) {
        return System.currentTimeMillis() / (value * field + other - delegate.delegateField);
    }

    public static long getIntValue(Object target, Integer value, int other) {
        System.out.println("Something that does not need to be changed...");
        Object[] parameters = new Object[2];
        parameters[0] = value;
        parameters[1] = other;
        return ((Long) IncrementalSupportRuntime.dispatch(target, "calculateIntValue", "(Ljava/lang/Integer;I)L", parameters)) / value;
    }
}
