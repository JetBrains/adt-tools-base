/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.google.common.annotations.Beta;

import java.util.List;

/**
 * Metadata about a particular view. The metadata for a View can be found by asking the
 * {@link IClientRulesEngine} for the metadata for a given class via
 * {@link IClientRulesEngine#getMetadata}.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public interface IViewMetadata {
    /**
     * Returns the display name views of this type (a name suitable to display to the
     * user, normally capitalized and usually but not necessarily tied to the
     * implementation class). To be clear, a specific view may have an id attribute and a
     * text attribute, <b>neither</b> of these is the display name. Instead, the class
     * android.widget.ZoomControls may have the display name "Zoom Controls", and an
     * individual view created into a layout can for example have the id "ZoomControl01".
     *
     * @return the user visible name of views of this type (never null)
     */
    @NonNull
    public String getDisplayName();

    /**
     * Gets the insets for this view
     *
     * @return the insets for this view
     */
    @NonNull
    public Margins getInsets();

    /**
     * Returns the {@link FillPreference} of this view
     *
     * @return the {@link FillPreference} of this view, never null but may be
     *     {@link FillPreference#NONE}
     */
    @NonNull
    public FillPreference getFillPreference();

    /**
     * Returns the most common attributes for this view.
     *
     * @return a list of attribute names (not including a namespace prefix) that
     *         are commonly set for this type of view, never null
     */
    @NonNull
    public List<String> getTopAttributes();

    /**
     * Types of fill behavior that views can prefer.
     * <p>
     * TODO: Consider better names. FillPolicy? Stretchiness?
     */
    public enum FillPreference {
        /** This view does not want to fill */
        NONE,
        /** This view wants to always fill both horizontal and vertical */
        BOTH,
        /** This view wants to fill horizontally but not vertically */
        WIDTH,
        /** This view wants to fill vertically but not horizontally */
        HEIGHT,
        /**
         * This view wants to fill in the opposite dimension of the context, e.g. in a
         * vertical context it wants to fill horizontally, and vice versa
         */
        OPPOSITE,
        /** This view wants to fill horizontally, but only in a vertical context */
        WIDTH_IN_VERTICAL,
        /** This view wants to fill vertically, but only in a horizontal context */
        HEIGHT_IN_HORIZONTAL;

        /**
         * Returns true if this view wants to fill horizontally, if the context is
         * vertical or horizontal as indicated by the parameter.
         *
         * @param verticalContext If true, the context is vertical, otherwise it is
         *            horizontal.
         * @return true if this view wants to fill horizontally
         */
        public boolean fillHorizontally(boolean verticalContext) {
            return (this == BOTH || this == WIDTH ||
                    (verticalContext && (this == OPPOSITE || this == WIDTH_IN_VERTICAL)));
        }

        /**
         * Returns true if this view wants to fill vertically, if the context is
         * vertical or horizontal as indicated by the parameter.
         *
         * @param verticalContext If true, the context is vertical, otherwise it is
         *            horizontal.
         * @return true if this view wants to fill vertically
         */
        public boolean fillVertically(boolean verticalContext) {
            return (this == BOTH || this == HEIGHT ||
                    (!verticalContext && (this == OPPOSITE || this == HEIGHT_IN_HORIZONTAL)));
        }
    }
}
