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

/**
 * Represents field part of a ProGuard class specification.
 */
public class FieldSpecification {

    private final NameSpecification mName;
    private final ModifierSpecification mModifier;
    private final NameSpecification mTypeSignature;
    private final AnnotationSpecification mAnnotationType;

    public FieldSpecification(NameSpecification name, ModifierSpecification modifier,
            NameSpecification typeSignature, AnnotationSpecification annotationType) {
        mName = name;
        mModifier = modifier;
        mTypeSignature = typeSignature;
        mAnnotationType = annotationType;
    }

    public NameSpecification getName() {
        return mName;
    }

    public ModifierSpecification getModifier() {
        return mModifier;
    }

    public NameSpecification getTypeSignature() {
        return mTypeSignature;
    }

    public AnnotationSpecification getAnnotationType() {
        return mAnnotationType;
    }
}
