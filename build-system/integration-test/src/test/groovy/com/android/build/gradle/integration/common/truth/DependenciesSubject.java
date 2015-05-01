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

import com.android.annotations.NonNull;
import com.android.builder.model.Dependencies;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;


public class DependenciesSubject extends Subject<DependenciesSubject, Dependencies> {

    static class Factory extends
            SubjectFactory<DependenciesSubject, Dependencies> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public DependenciesSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull Dependencies subject) {
            return new DependenciesSubject(failureStrategy, subject);
        }
    }

    public DependenciesSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull Dependencies subject) {
        super(failureStrategy, subject);
    }
}
