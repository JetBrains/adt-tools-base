/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

/**
 * Represents an XML element with a name, attributes and inner elements.
 * <p/>
 * The semantic of the element name is to be a fully qualified class name of a View to inflate.
 * The element name is not expected to have a namespace.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface IDragElement {

    /**
     * Returns the element name, which must match a fully qualified class name of
     * a View to inflate.
     */
    @NonNull
    public abstract String getFqcn();

    /**
     * Returns the bounds of the element's node, if it originated from an existing
     * canvas. The rectangle is invalid and non-null when the element originated
     * from the object palette.
     *
     * The bounds are absolute for the canvas.
     */
    @NonNull
    public abstract Rect getBounds();

    /**
     * Returns the fully qualified class name of the parent, if the element originated
     * from an existing canvas. Returns null if the element has no parent, such as a top
     * level element or an element originating from the object palette.
     */
    @Nullable
    public abstract String getParentFqcn();

    /**
     * Returns the bounds of the element's parent, absolute for the canvas, or invalid if there
     * is no suitable parent. This is generally invalid when {@link #getParentFqcn()} is null.
     *
     * The returned rectangle can be invalid. It is never null.
     */
    @NonNull
    public abstract Rect getParentBounds();

    /**
     * Returns a list of attributes. The list can be empty but is never null.
     */
    @NonNull
    public abstract IDragAttribute[] getAttributes();

    /**
     * Returns the requested attribute or null if not found.
     */
    @Nullable
    public abstract IDragAttribute getAttribute(@Nullable String uri, @NonNull String localName);

    /**
     * Returns a list of inner elements. The list can be empty but is never null.
     */
    @NonNull
    public abstract IDragElement[] getInnerElements();

    /**
     * Returns true if the given {@link INode} represents this drag element
     *
     * @param node the node to be checked
     * @return true if the given node represents this drag element
     */
    public abstract boolean isSame(@NonNull INode node);

    /**
     * An XML attribute in the {@link IDragElement}.
     * <p/>
     * The attribute is always represented by a namespace URI, a name and a value.
     * The name cannot be empty.
     * The namespace URI can be empty for an attribute without a namespace but is never null.
     * The value can be empty but cannot be null.
     */
    public interface IDragAttribute {

        /**
         * Returns the namespace URI of the attribute.
         * Can be empty for an attribute without a namespace but is never null.
         */
        @NonNull
        public abstract String getUri();

        /** Returns the XML local name of the attribute. Cannot be null nor empty. */
        @NonNull
        public abstract String getName();

        /** Returns the value of the attribute. Cannot be null. Can be empty. */
        @NonNull
        public abstract String getValue();
    }
}

