/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.builder.model.ClassField;

import java.io.Serializable;

/**
 */
public final class ClassFieldImpl implements ClassField, Serializable {

    private static final long serialVersionUID = 1L;

    @NonNull
    private final String type;
    @NonNull
    private final String name;
    @NonNull
    private final String value;

    public ClassFieldImpl(@NonNull String type, @NonNull String name, @NonNull String value) {
        //noinspection ConstantConditions
        if (type == null || name == null || value == null) {
            throw new NullPointerException("Build Config field cannot have a null parameter");
        }
        this.type = type;
        this.name = name;
        this.value = value;
    }

    @Override
    @NonNull
    public String getType() {
        return type;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassFieldImpl that = (ClassFieldImpl) o;

        if (!name.equals(that.name)) return false;
        if (!type.equals(that.type)) return false;
        if (!value.equals(that.value)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }
}
