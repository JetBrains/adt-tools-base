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
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.truth.CollectionSubject;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;

/**
 * Truth support for AndroidProject.
 */
public class ModelSubject extends Subject<ModelSubject, AndroidProject> {

    static class Factory extends SubjectFactory<ModelSubject, AndroidProject> {

        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public ModelSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull AndroidProject subject) {
            return new ModelSubject(failureStrategy, subject);
        }
    }

    public ModelSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull AndroidProject subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public CollectionIssueSubject issues() {
        return new CollectionIssueSubject(failureStrategy, getSubject().getSyncIssues());
    }

    @NonNull
    public CollectionSubject issuesAsCollection() {
        return Truth.assertThat(getSubject().getSyncIssues());
    }

    @NonNull
    public CollectionSubject bootClasspath() {
        return Truth.assertThat(getSubject().getBootClasspath());
    }

    @NonNull
    public VariantSubject variant(@NonNull String variantName) {
        Variant variant = ModelHelper.getVariant(getSubject().getVariants(), variantName);
        if (variant == null) {
            fail("Could not find variant with name " + variantName);
            assert false; // to make inspections happy.
        }

        return TruthHelper.assertThat(variant);
    }
}
