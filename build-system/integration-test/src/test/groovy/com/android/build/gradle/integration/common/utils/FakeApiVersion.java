/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.ApiVersion;

/**
 * Custom implementation of ApiVersion.
 *
 * This version is more loose in its equals method, and accepts any
 * implementation of ApiVersion.
 *
 * This is used to test other implementation of ApiVersion in during tests in
 * assertEquals
 */
final class FakeApiVersion implements ApiVersion {
    private final int mApiLevel;

    @Nullable
    private final String mCodename;

    public FakeApiVersion(int apiLevel, @Nullable String codename) {
        mApiLevel = apiLevel;
        mCodename = codename;
    }

    public FakeApiVersion(int apiLevel) {
        this(apiLevel, null);
    }

    public FakeApiVersion(@NonNull String codename) {
        this(1, codename);
    }

    public static ApiVersion create(@NonNull Object value) {
        if (value instanceof Integer) {
            return new FakeApiVersion((Integer) value, null);
        } else if (value instanceof String) {
            return new FakeApiVersion(1, (String) value);
        }

        return null;
    }

    @Override
    public int getApiLevel() {
        return mApiLevel;
    }

    @Nullable
    @Override
    public String getCodename() {
        return mCodename;
    }

    @NonNull
    @Override
    public String getApiString() {
        return mCodename != null ? mCodename : Integer.toString(mApiLevel);
    }

    @Override
    public boolean equals(Object o) {
        /**
         * Normally equals only test for the same exact class, but here me make it accept
         * ApiVersion since we're comparing it against implementations that are serialized
         * across Gradle's tooling api.
         */
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiVersion)) {
            return false;
        }

        ApiVersion that = (ApiVersion) o;

        if (mApiLevel != that.getApiLevel()) {
            return false;
        }
        if (mCodename != null ? !mCodename.equals(that.getCodename()) : that.getCodename() != null) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "FakeApiVersion{" +
                "mApiLevel=" + mApiLevel +
                ", mCodename='" + mCodename + '\'' +
                '}';
    }
}
