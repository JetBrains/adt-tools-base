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
public class SimpleMethodDispatch {

    private int field = 5;

    public long getIntValue(int value) {
        System.out.println("getIntValue is CALLED with " + value);
        int secondValue = value * 2;
        return calculateIntValue(secondValue, 567);
    }

    public String getStringValue() {
        return "an old value;";
    }

    public long calculateIntValue(Integer value, int otherValue) {
        System.out.println("and calculate is called with " + value + " and " + otherValue);
        long toReturn = field / (value);
        field++;
        return toReturn;
    }
}
