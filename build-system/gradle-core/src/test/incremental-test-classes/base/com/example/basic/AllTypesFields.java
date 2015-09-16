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

public class AllTypesFields {

    private boolean privateBooleanField;

    private final boolean privateFinalBooleanField = false;

    private final boolean privateFinalBooleanFieldCtorInit;


    private int privateIntField;

    private final int privateFinalIntField = 435;

    private final int privateFinalIntFieldCtorInit;


    private long privateLongField;

    private final long privateFinalLongField = 435L;

    private final long privateFinalLongFieldCtorInit;


    private float privateFloatField;

    private final float privateFinalFloatField = 435.35f;

    private final float privateFinalFloatFieldCtorInit;


    private double privateDoubleField;

    private final double privateFinalDoubleField = 435.35d;

    private final double privateFinalDoubleFieldCtorInit;


    private String privateStringField;

    private final String privateFinalStringField = "private_field";

    private final String privateFinalStringFieldCtorInit;

    public AllTypesFields() {
        privateFinalBooleanFieldCtorInit = true;
        privateFinalLongFieldCtorInit = 1243L;
        privateFinalIntFieldCtorInit = 1243;
        privateFinalStringFieldCtorInit = "private_field_ctor_init";
        privateFinalFloatFieldCtorInit = 1243.43f;
        privateFinalDoubleFieldCtorInit = 1234.43d;
    }

    // STRING methods.
    public String getPrivateStringField() {
        return privateStringField;
    }

    public void setPrivateStringField(String value) {
        privateStringField = value;
    }

    public String getPrivateFinalStringField() {
        return privateFinalStringField;
    }

    public String getPrivateFinalStringFieldCtorInit() {
        return privateFinalStringFieldCtorInit;
    }

    public AllTypesFields chaining(String value) {
        privateStringField = value;
        return this;
    }
    // end or String methods.

    // BOOLEAN methods.
    public boolean getPrivateBooleanField() {
        return privateBooleanField;
    }

    public void setPrivateBooleanField(boolean value) {
        privateBooleanField = value;
    }

    public boolean getPrivateFinalBooleanField() {
        return privateFinalBooleanField;
    }

    public boolean getPrivateFinalBooleanFieldCtorInit() {
        return privateFinalBooleanFieldCtorInit;
    }

    public AllTypesFields chaining(boolean value) {
        privateBooleanField = value;
        return this;
    }
    // end of boolean methods.

    // INT methods.
    public int getPrivateIntField() {
        return privateIntField;
    }

    public void setPrivateIntField(int value) {
        privateIntField = value;
    }

    public int getPrivateFinalIntField() {
        return privateFinalIntField;
    }

    public int getPrivateFinalIntFieldCtorInit() {
        return privateFinalIntFieldCtorInit;
    }

    public AllTypesFields chaining(int value) {
        privateIntField = value;
        return this;
    }
    // end of int methods.

    // LONG methods.
    public long getPrivateLongField() {
        return privateLongField;
    }

    public void setPrivateLongField(long value) {
        privateLongField = value;
    }

    public long getPrivateFinalLongField() {
        return privateFinalLongField;
    }

    public long getPrivateFinalLongFieldCtorInit() {
        return privateFinalLongFieldCtorInit;
    }

    public AllTypesFields chaining(long value) {
        privateLongField = value;
        return this;
    }
    // end of long methods.

    // DOUBLE methods.
    public double getPrivateDoubleField() {
        return privateDoubleField;
    }

    public void setPrivateDoubleField(double value) {
        privateDoubleField = value;
    }

    public double getPrivateFinalDoubleField() {
        return privateFinalDoubleField;
    }

    public double getPrivateFinalDoubleFieldCtorInit() {
        return privateFinalDoubleFieldCtorInit;
    }

    public AllTypesFields chaining(double value) {
        privateDoubleField = value;
        return this;
    }
    // end of double methods.

    // FLOAT methods.
    public float getPrivateFloatField() {
        return privateFloatField;
    }

    public void setPrivateFloatField(float value) {
        privateFloatField = value;
    }

    public float getPrivateFinalFloatField() {
        return privateFinalFloatField;
    }

    public float getPrivateFinalFloatFieldCtorInit() {
        return privateFinalFloatFieldCtorInit;
    }

    public AllTypesFields chaining(float value) {
        privateFloatField = value;
        return this;
    }
    // end of double methods.

    // private methods.
    private boolean privateBooleanField(boolean value) {
        return value & privateBooleanField;
    }

    private int privateIntField(int value) {
        return privateIntField * value;
    }

    private long privateLongField(int value) {
        return privateLongField * value;
    }

    private float privateFloatField(float value) {
        return privateLongField * value;
    }

    private double privateDoubleField(double value) {
        return privateLongField * value;
    }

    private String privateStringField(String value) {
        return privateStringField + value;
    }

    // Random methods invoking private fields.
    public List<String> getAllPrivateFields() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(String.valueOf(privateBooleanField));
        builder.add(String.valueOf(privateIntField));
        builder.add(String.valueOf(privateFloatField));
        builder.add(String.valueOf(privateDoubleField));
        builder.add(String.valueOf(privateLongField));
        builder.add(privateStringField);
        return builder.build();
    }

    public List<String> getAllPrivateFinalFields() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(String.valueOf(privateFinalBooleanField));
        builder.add(String.valueOf(privateFinalIntField));
        builder.add(String.valueOf(privateFinalFloatField));
        builder.add(String.valueOf(privateFinalDoubleField));
        builder.add(String.valueOf(privateFinalLongField));
        builder.add(privateFinalStringField);
        return builder.build();
    }

    public List<String> getAllPrivateFinalCtorInitializedFields() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(String.valueOf(privateFinalBooleanFieldCtorInit));
        builder.add(String.valueOf(privateFinalIntFieldCtorInit));
        builder.add(String.valueOf(privateFinalFloatFieldCtorInit));
        builder.add(String.valueOf(privateFinalDoubleFieldCtorInit));
        builder.add(String.valueOf(privateFinalLongFieldCtorInit));
        builder.add(privateFinalStringFieldCtorInit);
        return builder.build();
    }

    public List<String> getAllPrivateMethods() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(String.valueOf(privateBooleanField(false)));
        builder.add(String.valueOf(privateIntField(2)));
        builder.add(String.valueOf(privateFloatField(2f)));
        builder.add(String.valueOf(privateDoubleField(2d)));
        builder.add(String.valueOf(privateLongField(2)));
        builder.add(privateStringField("_modified"));
        return builder.build();
    }

    public void setAll(boolean booleanValue, int intValue, long longValue, float floatValue,
            double doubleValue, String stringValue) {

        privateBooleanField = booleanValue;
        privateIntField = intValue;
        privateLongField = longValue;
        privateFloatField = floatValue;
        privateDoubleField = doubleValue;
        privateStringField = stringValue;
    }
}