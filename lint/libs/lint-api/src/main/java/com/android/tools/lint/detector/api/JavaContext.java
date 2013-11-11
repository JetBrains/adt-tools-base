/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintDriver;

import java.io.File;

import lombok.ast.ClassDeclaration;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.MethodDeclaration;
import lombok.ast.Node;

/**
 * A {@link Context} used when checking Java files.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class JavaContext extends Context {
    /** The parse tree */
    public Node compilationUnit;
    /** The parser which produced the parse tree */
    public IJavaParser parser;

    /**
     * Constructs a {@link JavaContext} for running lint on the given file, with
     * the given scope, in the given project reporting errors to the given
     * client.
     *
     * @param driver the driver running through the checks
     * @param project the project to run lint on which contains the given file
     * @param main the main project if this project is a library project, or
     *            null if this is not a library project. The main project is
     *            the root project of all library projects, not necessarily the
     *            directly including project.
     * @param file the file to be analyzed
     */
    public JavaContext(
            @NonNull LintDriver driver,
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File file) {
        super(driver, project, main, file);
    }

    /**
     * Returns a location for the given node
     *
     * @param node the AST node to get a location for
     * @return a location for the given node
     */
    @NonNull
    public Location getLocation(@NonNull Node node) {
        if (parser != null) {
            return parser.getLocation(this, node);
        }

        return new Location(file, null, null);
    }

    @Override
    public void report(@NonNull Issue issue, @Nullable Location location,
            @NonNull String message, @Nullable Object data) {
        if (mDriver.isSuppressed(issue, compilationUnit)) {
            return;
        }
        super.report(issue, location, message, data);
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue the issue to report
     * @param scope the AST node scope the error applies to. The lint infrastructure
     *    will check whether there are suppress annotations on this node (or its enclosing
     *    nodes) and if so suppress the warning without involving the client.
     * @param location the location of the issue, or null if not known
     * @param message the message for this warning
     * @param data any associated data, or null
     */
    public void report(
            @NonNull Issue issue,
            @Nullable Node scope,
            @Nullable Location location,
            @NonNull String message,
            @Nullable Object data) {
        if (scope != null && mDriver.isSuppressed(issue, scope)) {
            return;
        }
        super.report(issue, location, message, data);
    }


    @Nullable
    public static Node findSurroundingMethod(Node scope) {
        while (scope != null) {
            Class<? extends Node> type = scope.getClass();
            // The Lombok AST uses a flat hierarchy of node type implementation classes
            // so no need to do instanceof stuff here.
            if (type == MethodDeclaration.class || type == ConstructorDeclaration.class) {
                return scope;
            }

            scope = scope.getParent();
        }

        return null;
    }

    @Nullable
    public static ClassDeclaration findSurroundingClass(Node scope) {
        while (scope != null) {
            Class<? extends Node> type = scope.getClass();
            // The Lombok AST uses a flat hierarchy of node type implementation classes
            // so no need to do instanceof stuff here.
            if (type == ClassDeclaration.class) {
                return (ClassDeclaration) scope;
            }

            scope = scope.getParent();
        }

        return null;
    }

}
