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

package com.android.builder.shrinker.parser;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Method part of a ProGuard class specification.
 */
public class MethodSpecification {

    @NonNull private final NameSpecification mNameSpecification;
    @Nullable private final ModifierSpecification mModifiers;
    @Nullable private final AnnotationSpecification mAnnotationType;

    public MethodSpecification(
            @NonNull NameSpecification nameSpecification,
            @Nullable ModifierSpecification modifiers,
            @Nullable AnnotationSpecification annotationType) {
        mNameSpecification = nameSpecification;
        mModifiers = modifiers;
        mAnnotationType = annotationType;
    }

    @Nullable
    public ModifierSpecification getModifiers() {
        return mModifiers;
    }

    @Nullable
    public AnnotationSpecification getAnnotations() {
        return mAnnotationType;
    }

    @NonNull
    public NameSpecification getName() {
        return mNameSpecification;
    }
}
