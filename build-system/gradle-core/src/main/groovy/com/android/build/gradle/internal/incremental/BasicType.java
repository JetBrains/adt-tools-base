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

import com.android.annotations.Nullable;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Type definitions for intrinsic types, used at runtime when doing reflection based dispatching.
 */
public enum BasicType {
    I(Integer.TYPE) {
        @Override
        public void box(GeneratorAdapter mv) {
            mv.invokeStatic(Type.getType(Integer.class),
                    Method.getMethod("Integer valueOf(int)"));
        }
    },
    J(Long.TYPE) {
        @Override
        public void box(GeneratorAdapter mv) {
            mv.invokeStatic(Type.getType(Long.class),
                    Method.getMethod("Long valueOf(long)"));
        }
    },
    Z(Boolean.TYPE) {
        @Override
        public void box(GeneratorAdapter mv) {
            mv.invokeStatic(Type.getType(Boolean.class),
                    Method.getMethod("Boolean valueOf(boolean)"));
        }
    },
    F(Float.TYPE) {
        @Override
        public void box(GeneratorAdapter mv) {
            mv.invokeStatic(Type.getType(Float.class),
                    Method.getMethod("Float valueOf(float)"));
        }
    },
    D(Double.TYPE) {
        @Override
        public void box(GeneratorAdapter mv) {
            mv.invokeStatic(Type.getType(Double.class),
                    Method.getMethod("Double valueOf(double)"));
        }
    },
    V(Void.TYPE) {
        @Override
        public void box(GeneratorAdapter mv) {
        }
    },;

    public abstract void box(GeneratorAdapter mv);

    private final Class<?> primitiveJavaType;

    BasicType(Class<?> primitiveType) {
        this.primitiveJavaType = primitiveType;
    }

    public Class getJavaType() {
        return primitiveJavaType;
    }

    @Nullable
    public static BasicType parse(String name) {
        for (BasicType basicType : BasicType.values()) {
            if (basicType.getJavaType().getName().equals(name)) {
                return basicType;
            }
        }
        return null;
    }
}
