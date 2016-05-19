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

package com.android.tools.chunkio.processor;

import com.android.tools.chunkio.Chunked;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import java.util.List;

public class Validator {
    private final Types mTypeUtils;
    private final ErrorHandler mErrorHandler;

    public Validator(Types typeUtils, Environment environment) {
        mTypeUtils = typeUtils;
        mErrorHandler = environment.errorHandler;
    }

    /**
     * Validates to specified type element to ensure we can process the
     * class annotated with {@link Chunked}.
     *
     * A class is not valid if it:
     * - has a super class
     * - is a non-static inner class
     * - is declared in a method
     * - does not have an empty constructor
     * - is abstract
     */
    public boolean validate(Element element) {
        TypeElement typeElement = (TypeElement) element;

        if (hasSuperclass(typeElement)) return false;
        if (isAbstract(typeElement)) return false;
        if (isDependent(typeElement)) return false;

        return hasEmptyConstructor(typeElement);
    }

    private boolean hasSuperclass(TypeElement typeElement) {
        TypeMirror superType = typeElement.getSuperclass();
        TypeElement superClass = (TypeElement) mTypeUtils.asElement(superType);
        String superName = superClass.getQualifiedName().toString();

        // TODO: support super classes
        if (!superName.equals(Object.class.getCanonicalName())) {
            mErrorHandler.error(typeElement,
                    "Class %s annotated with @%s cannot extend another class",
                    typeElement.getQualifiedName());
            return true;
        }
        return false;
    }

    private boolean isAbstract(TypeElement typeElement) {
        if (Utils.isAbstract(typeElement)) {
            mErrorHandler.error(typeElement, "Class %s annotated with @%s cannot be abstract",
                    typeElement.getQualifiedName());
            return true;
        }
        return false;
    }

    private boolean isDependent(TypeElement typeElement) {
        ElementKind enclosingKind = typeElement.getEnclosingElement().getKind();
        switch (enclosingKind) {
            case CLASS:
                if (!Utils.isTopLevel(typeElement) && !Utils.isStatic(typeElement)) {
                    mErrorHandler.error(typeElement,
                            "Class %s must be static", typeElement.getQualifiedName());
                    return true;
                }
                break;
            case METHOD:
                mErrorHandler.error(typeElement, "Class %s cannot be declared in a method",
                        typeElement.getQualifiedName());
                return true;
        }

        return false;
    }

    private boolean hasEmptyConstructor(TypeElement typeElement) {
        boolean hasEmptyConstructor = false;
        List<ExecutableElement> constructors =
                ElementFilter.constructorsIn(typeElement.getEnclosedElements());

        for (ExecutableElement constructor : constructors) {
            if (constructor.getParameters().isEmpty()) {
                hasEmptyConstructor = true;
            }
        }

        if (!hasEmptyConstructor) {
            mErrorHandler.error(typeElement, "Class %s must have an empty constructor",
                    typeElement.getQualifiedName());
            return false;
        }

        return true;
    }
}
