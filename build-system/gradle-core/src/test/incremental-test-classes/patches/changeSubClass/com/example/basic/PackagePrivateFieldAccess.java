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

import com.google.common.base.Joiner;

import java.util.ArrayList;

/**
 * Class that uses package private methods and fields from another object.
 */
public class PackagePrivateFieldAccess {

    AllAccessFields allAccessFields = new AllAccessFields();

    // reverse public and protected order
    public String accessIntFields() {
        return String.valueOf(allAccessFields.publicInt
                + allAccessFields.packagePrivateInt
                + allAccessFields.protectedInt);
    }

    // reverse public and protected order
    public String accessStringFields() {
        return String.valueOf(allAccessFields.publicString
                + allAccessFields.packagePrivateString
                + allAccessFields.protectedString);
    }

    // reverse public and protected order
    public String accessArrayFields() {
        ArrayList<String> values = new ArrayList<String>();
        for (int i : allAccessFields.publicIntArray) {
            values.add(String.valueOf(i));
        }
        for (int i : allAccessFields.packagePrivateIntArray) {
            values.add(String.valueOf(i));
        }
        for (int i : allAccessFields.protectedIntArray) {
            values.add(String.valueOf(i));
        }
        return Joiner.on(",").join(values);
    }

    // reverse public and protected order
    public String accessArrayOfStringFields() {
        ArrayList<String> values = new ArrayList<String>();
        for (String s : allAccessFields.publicStringArray) {
            values.add(s);
        }
        for (String s : allAccessFields.packagePrivateStringArray) {
            values.add(s);
        }
        for (String s : allAccessFields.protectedStringArray) {
            values.add(s);
        }
        return Joiner.on(",").join(values);
    }
}