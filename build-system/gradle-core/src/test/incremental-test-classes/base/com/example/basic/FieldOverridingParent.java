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

import java.util.ArrayList;
import java.util.Collection;

/**
 * Class to test private field overriding.
 */
public class FieldOverridingParent {

    private static String staticField = "static parent";
    private static Collection<String> staticCollectionField;

    private String field = "parent";
    private Collection<String> collectionField;

    public FieldOverridingParent() {
        collectionField = new ArrayList<String>();
        collectionField.add("parent");

        staticCollectionField = new ArrayList<String>();
        staticCollectionField.add("static parent");
    }

    public String getField() {
        return field;
    }

    public static String getStaticField() {
        return staticField;
    }

    public Collection<String> getCollection() {
        return collectionField;
    }

    public Collection<String> getStaticCollection() {
        return staticCollectionField;
    }
}
