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

package com.android.build.gradle.integration.common.truth;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import java.util.Optional;

/**
 * Truth Subject for Java 8 Optional.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class Java8OptionalSubject extends Subject<Java8OptionalSubject, Optional<?>> {

    public static final SubjectFactory<Java8OptionalSubject, Optional<?>> FACTORY =
            new SubjectFactory<Java8OptionalSubject, Optional<?>>() {
                @Override
                public Java8OptionalSubject getSubject(FailureStrategy fs, Optional<?> that) {
                    return new Java8OptionalSubject(fs, that);
                }
            };

    public Java8OptionalSubject(FailureStrategy failureStrategy, Optional<?> subject) {
        super(failureStrategy, subject);
    }

    public void isPresent() {
        if (!getSubject().isPresent()) {
            fail("is present");
        }
    }

    public void isAbsent() {
        if (getSubject().isPresent()) {
            fail("is not present");
        }
    }
}
