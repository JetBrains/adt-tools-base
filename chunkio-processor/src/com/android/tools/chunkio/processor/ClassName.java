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

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.PrintWriter;

/**
 * Holds a generated class name.
 */
final class ClassName {
    private static final String GENERATED_CLASS_SUFFIX = "$$ChunkIO";

    /** Package where to generate the class. */
    final String packageName;
    /** Name of the generated class. */
    final String className;
    /** Fully qualified name of the generated class. */
    final String qualifiedName;
    /** Name of the class this generated name was derived from. */
    final String sourceName;

    private ClassName(String packageName, String className, String sourceName) {
        this.packageName = packageName;
        this.className = className;
        this.qualifiedName = packageName.isEmpty() ? className : packageName + '.' + className;
        this.sourceName = sourceName;
    }

    /**
     * Generates a class name for generation from the specified type element.
     * The generated class name will have the same package as the type element.
     * The generated class name will be the type element's class name with
     * all '.' instances replaced with '$'.
     * The generated class name will be suffixed with "$$ChunkIO".
     *
     * For instance, if the source type element is a class called
     * com.myapp.MyClass, the generated class name will be:
     * com.myapp.MyClass$$ChunkIO.
     *
     * If the source type element is an inner class called
     * com.myapp.Outer.Inner, the generated class name will be:
     * com.myapp.Outer$Inner$$ChunkIO.
     */
    static ClassName from(TypeElement typeElement, Elements elementUtils) {
        PackageElement packageElement = elementUtils.getPackageOf(typeElement);
        String typeName = typeElement.getQualifiedName().toString();

        String sourceName;
        String className;
        String packageName = packageElement.getQualifiedName().toString();

        int length = packageName.length();
        if (length == 0) {
            sourceName = typeName;
            className = typeName + GENERATED_CLASS_SUFFIX;
        } else {
            sourceName = typeName.substring(length + 1);
            className = sourceName.replace('.', '$') + GENERATED_CLASS_SUFFIX;
        }

        return new ClassName(packageName, className, sourceName);
    }
}
