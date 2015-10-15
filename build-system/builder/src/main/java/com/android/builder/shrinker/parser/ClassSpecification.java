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

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Represents a ProGuard class specification.
 */
public class ClassSpecification {

    private final NameSpecification mNameSpec;
    private final ClassTypeSpecification mClassType;
    private final AnnotationSpecification mAnnotation;
    private KeepModifier mKeepModifier;
    private ModifierSpecification mModifier;
    private List<FieldSpecification> mFieldSpecifications = Lists.newArrayList();
    private List<MethodSpecification> mMethodSpecifications = Lists.newArrayList();

    private InheritanceSpecification mInheritanceSpecification;

    public ClassSpecification(
            NameSpecification nameSpec,
            ClassTypeSpecification classType,
            AnnotationSpecification annotation) {
        mNameSpec = nameSpec;
        mClassType = classType;
        mAnnotation = annotation;
    }

    public void setKeepModifier(KeepModifier keepModifier) {
        mKeepModifier = keepModifier;
    }

    public KeepModifier getKeepModifier() {
        return mKeepModifier;
    }

    public void setModifier(ModifierSpecification modifier) {
        mModifier = modifier;
    }

    public ModifierSpecification getModifier() {
        return mModifier;
    }

    public void add(FieldSpecification fieldSpecification) {
        mFieldSpecifications.add(fieldSpecification);
    }

    public void add(MethodSpecification methodSpecification) {
        mMethodSpecifications.add(methodSpecification);
    }

    public List<MethodSpecification> getMethodSpecifications() {
        return mMethodSpecifications;
    }

    public NameSpecification getNameSpec() {
        return mNameSpec;
    }

    public ClassTypeSpecification getClassType() {
        return mClassType;
    }

    public AnnotationSpecification getAnnotation() {
        return mAnnotation;
    }

    public List<FieldSpecification> getFieldSpecifications() {
        return mFieldSpecifications;
    }

    public void setInheritance(InheritanceSpecification inheritanceSpecification) {
        mInheritanceSpecification = inheritanceSpecification;
    }

    public InheritanceSpecification getInheritanceSpecification() {
        return mInheritanceSpecification;
    }
}
