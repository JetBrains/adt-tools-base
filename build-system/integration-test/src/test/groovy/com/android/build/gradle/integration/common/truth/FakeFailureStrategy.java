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

package com.android.build.gradle.integration.common.truth;

import com.google.common.truth.FailureStrategy;

/**
 * Implementation of FailureStrategy to test custom Truth Subjects.
 */
class FakeFailureStrategy extends FailureStrategy {

    String message;
    Throwable throwable;
    CharSequence expected;
    CharSequence actual;

    @Override
    public void fail(String message) {
        this.message = message;
    }

    @Override
    public void fail(String message, Throwable cause) {
        this.message = message;
        this.throwable = cause;
    }

    @Override
    public void failComparing(String message, CharSequence expected, CharSequence actual) {
        this.message = message;
        this.expected = expected;
        this.actual = actual;
    }
}