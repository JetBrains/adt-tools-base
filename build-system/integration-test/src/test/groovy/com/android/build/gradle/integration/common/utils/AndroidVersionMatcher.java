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

package com.android.build.gradle.integration.common.utils;

import com.android.sdklib.AndroidVersion;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Matcher;

public class AndroidVersionMatcher {

    public static Matcher<AndroidVersion> thatUsesDalvik() {
        return atMost(19);
    }

    public static Matcher<AndroidVersion> thatUsesArt() {
        return atLeast(21);
    }

    public static Matcher<AndroidVersion> atLeast(final int version) {
        return new BaseMatcher<AndroidVersion>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof AndroidVersion &&
                        ((AndroidVersion) item).isGreaterOrEqualThan(version);
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("Android versions ").appendValue(version)
                        .appendText(" and above.");
            }
        };
    }

    public static Matcher<AndroidVersion> atMost(final int version) {
        return new BaseMatcher<AndroidVersion>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof AndroidVersion &&
                        ((AndroidVersion) item).compareTo(version, null) <= 0;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("Android versions ").appendValue(version)
                        .appendText(" and below.");
            }
        };
    }

}
