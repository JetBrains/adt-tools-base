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

import com.example.basic.AllTypesFields;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Class that will have methods that use forbidden APIs.
 */
@SuppressWarnings({"MethodMayBeStatic", "unused"})
public class InstantRunMethodVerifierTarget {

    public void classNewInstance() throws IllegalAccessException, InstantiationException {
        Object.class.newInstance();
    }

    public void methodInvoke()
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method toString = Object.class.getMethod("toString");
        toString.invoke(new Object());
    }

    public void contructorInvoke()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            InstantiationException {
        Constructor<String> constructor = String.class.getConstructor(String.class);
        String foo = constructor.newInstance("Foo");
    }

    public void objectSetFåield() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateStringField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), "foo");
    }

    public void booleanSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateBooleanField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), true);
    }

    public void byteSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateByteField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), (byte) 0);
    }

    public void charSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateCharField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), 'c');
    }

    public void doubleSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateDoubleField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), 1d);
    }

    public void floatSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateFloatField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), 1f);
    }

    public void intSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateIntField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), 12);
    }

    public void longSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateLongField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), 12L);
    }

    public void shortSetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateShortField");
        field.setAccessible(true);
        field.set(new AllTypesFields(), (short) 12);
    }

    public String objectGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateStringField");
        field.setAccessible(true);
        return (String) field.get(new AllTypesFields());
    }

    public boolean booleanGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateBooleanField");
        field.setAccessible(true);
        return field.getBoolean(new AllTypesFields());
    }

    public byte byteGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateByteField");
        field.setAccessible(true);
        return field.getByte(new AllTypesFields());
    }

    public char charGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateCharField");
        field.setAccessible(true);
        return field.getChar(new AllTypesFields());
    }

    public double doubleGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateDoubleField");
        field.setAccessible(true);
        return field.getDouble(new AllTypesFields());
    }

    public float floatGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateFloatField");
        field.setAccessible(true);
        return field.getFloat(new AllTypesFields());
    }

    public int intGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateIntField");
        field.setAccessible(true);
        return field.getInt(new AllTypesFields());
    }

    public long longGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateLongField");
        field.setAccessible(true);
        return field.getLong(new AllTypesFields());
    }

    public short shortGetField() throws NoSuchFieldException, IllegalAccessException {
        Field field = AllTypesFields.class.getField("privateShortField");
        field.setAccessible(true);
        return field.getShort(new AllTypesFields());
    }
}
