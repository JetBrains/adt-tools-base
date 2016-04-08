/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ndk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;

import org.gradle.api.InvalidUserDataException;

/**
 * Enum of STL for NDK.
 */
public enum Stl {
    SYSTEM("system", "system", true),
    STLPORT_STATIC("stlport_static", "stlport", true),
    STLPORT_SHARED("stlport_shared", "stlport", false),
    GNUSTL_STATIC("gnustl_static", "gnustl", true),
    GNUSTL_SHARED("gnustl_shared", "gnustl", false),
    GABIPP_STATIC("gabi++_static", "gabi++", true),
    GABIPP_SHARED("gabi++_shared", "gabi++", false),
    CPP_STATIC("c++_static", "c++", true),
    CPP_SHARED("c++_shared", "c++", false);

    public static final Stl DEFAULT = SYSTEM;

    private String id;

    private String name;

    private boolean istStatic;

    Stl(String id, String name, boolean isStatic) {
        this.id = id;
        this.name = name;
        this.istStatic = isStatic;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isStatic() {
        return istStatic;
    }

    /**
     * Return the Stl enum from the ID.
     *
     * ID is the name used by ndk-build.  Accepted IDs are:
     *   - system
     *   - stlport_static
     *   - stlport_shared
     *   - gnustl_static
     *   - gnustl_shared
     *   - gabi++_static
     *   - gabi++_shared
     *   - c++_static
     *   - c++_shared
     */
    @NonNull
    public static Stl getById(@Nullable String id) {
        if (Strings.isNullOrEmpty(id)) {
            return DEFAULT;
        }
        for (Stl stl : values()) {
            if (stl.id.equals(id)) {
                return stl;
            }
        }
        throw new InvalidUserDataException("Invalid STL: " + id);
    }

    @Override
    public String toString() {
        return getId();
    }
}
