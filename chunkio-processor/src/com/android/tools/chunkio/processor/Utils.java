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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.PrintWriter;
import java.io.StringWriter;

final class Utils {
    private Utils() {
    }

    /**
     * Returns true if the specified string contains multiple lines
     * delimited by '\n'.
     */
    static boolean isMultiline(String statement) {
        for (int i = 0; i < statement.length(); i++) {
            if (statement.charAt(i) == '\n') return true;
        }
        return false;
    }

    /**
     * Creates a variable name from a simple class name (not fully qualified).
     * This simply ensures the first character is lower case.
     */
    static String variableName(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1).replace('.', '_');
    }

    static boolean isStatic(Element element) {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    static boolean isAbstract(Element element) {
        return element.getModifiers().contains(Modifier.ABSTRACT);
    }

    static boolean isTopLevel(TypeElement typeElement) {
        return typeElement.getEnclosingElement().getKind() == ElementKind.PACKAGE;
    }

    static String stackTraceToString(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
