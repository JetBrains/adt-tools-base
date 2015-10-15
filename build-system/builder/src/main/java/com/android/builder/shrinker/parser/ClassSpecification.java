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
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Represents a ProGuard class specification.
 */
public class ClassSpecification {

    @NonNull private final NameSpecification mNameSpec;
    @NonNull private final ClassTypeSpecification mClassType;
    @Nullable private final AnnotationSpecification mAnnotation;
    @Nullable private KeepModifier mKeepModifier;
    @Nullable private ModifierSpecification mModifier;
    @NonNull private List<FieldSpecification> mFieldSpecifications = Lists.newArrayList();
    @NonNull private List<MethodSpecification> mMethodSpecifications = Lists.newArrayList();
    @Nullable private InheritanceSpecification mInheritanceSpecification;

    public ClassSpecification(
            @NonNull NameSpecification nameSpec,
            @NonNull ClassTypeSpecification classType,
            @Nullable AnnotationSpecification annotation) {
        mNameSpec = nameSpec;
        mClassType = classType;
        mAnnotation = annotation;
    }

    public void setKeepModifier(@Nullable KeepModifier keepModifier) {
        mKeepModifier = keepModifier;
    }

    @Nullable
    public KeepModifier getKeepModifier() {
        return mKeepModifier;
    }

    public void setModifier(@Nullable ModifierSpecification modifier) {
        mModifier = modifier;
    }

    @Nullable
    public ModifierSpecification getModifier() {
        return mModifier;
    }

    public void add(FieldSpecification fieldSpecification) {
        mFieldSpecifications.add(fieldSpecification);
    }

    public void add(MethodSpecification methodSpecification) {
        mMethodSpecifications.add(methodSpecification);
    }

    @NonNull
    public List<MethodSpecification> getMethodSpecifications() {
        return mMethodSpecifications;
    }

    public NameSpecification getName() {
        return mNameSpec;
    }

    @NonNull
    public ClassTypeSpecification getClassType() {
        return mClassType;
    }

    @Nullable
    public AnnotationSpecification getAnnotation() {
        return mAnnotation;
    }

    @NonNull
    public List<FieldSpecification> getFieldSpecifications() {
        return mFieldSpecifications;
    }

    public void setInheritance(@Nullable InheritanceSpecification inheritanceSpecification) {
        mInheritanceSpecification = inheritanceSpecification;
    }

    @Nullable
    public InheritanceSpecification getInheritance() {
        return mInheritanceSpecification;
    }
}
