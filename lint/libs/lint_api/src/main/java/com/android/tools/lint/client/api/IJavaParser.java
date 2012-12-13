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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;

import lombok.ast.Node;

/**
 * A wrapper for a Java parser. This allows tools integrating lint to map directly
 * to builtin services, such as already-parsed data structures in Java editors.
 * <p/>
 * <b>NOTE: This is not a or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public interface IJavaParser {
    /**
     * Parse the file pointed to by the given context.
     *
     * @param context the context pointing to the file to be parsed, typically
     *            via {@link Context#getContents()} but the file handle (
     *            {@link Context#file} can also be used to map to an existing
     *            editor buffer in the surrounding tool, etc)
     * @return the compilation unit node for the file
     */
    @Nullable
    Node parseJava(@NonNull JavaContext context);

    /**
     * Returns a {@link Location} for the given node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    @NonNull
    Location getLocation(@NonNull JavaContext context, @NonNull Node node);

    /**
     * Creates a light-weight handle to a location for the given node. It can be
     * turned into a full fledged location by
     * {@link com.android.tools.lint.detector.api.Location.Handle#resolve()}.
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location handle
     *            for
     * @return a location handle
     */
    @NonNull
    Location.Handle createLocationHandle(@NonNull JavaContext context, @NonNull Node node);

    /**
     * Dispose any data structures held for the given context.
     * @param context information about the file previously parsed
     * @param compilationUnit the compilation unit being disposed
     */
    void dispose(@NonNull JavaContext context, @NonNull Node compilationUnit);
}
