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
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

import java.util.Collection;
import java.util.Map;

/**
 * A Client Rules Engine is a set of methods that {@link IViewRule}s can use to
 * access the client public API of the Rules Engine.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface IClientRulesEngine {

    /**
     * Returns the FQCN for which the rule was loaded.
     *
     * @return the fully qualified name of the rule
     */
    @NonNull
    String getFqcn();

    /**
     * Returns the most recently rendered View object for this node, if any.
     *
     * @param node the node to look up the view object for
     * @return the corresponding view object, or null
     */
    @Nullable
    Object getViewObject(@NonNull INode node);

    /**
     * Prints a debug line in the Eclipse console using the ADT formatter.
     *
     * @param msg A String format message.
     * @param params Optional parameters for the message.
     */
    void debugPrintf(@NonNull String msg, Object...params);

    /**
     * Loads and returns an {@link IViewRule} for the given FQCN.
     *
     * @param fqcn A non-null, non-empty FQCN for the rule to load.
     * @return The rule that best matches the given FQCN according to the
     *   inheritance chain. Rules are cached and requesting the same FQCN twice
     *   is fast and will return the same rule instance.
     */
    @Nullable
    IViewRule loadRule(@NonNull String fqcn);

    /**
     * Returns the metadata associated with the given fully qualified class name. Note that
     * this will always return an {@link IViewMetadata} instance, even when the class name
     * is unknown to the layout editor, such as for custom views. In that case, some
     * heuristics will be applied to return metadata information such as guesses for
     * what the most common attribute is, and so on.
     *
     * @param fqcn a fully qualified class name for an Android view class
     * @return the metadata associated with the given fully qualified class name.
     */
    @NonNull
    IViewMetadata getMetadata(@NonNull String fqcn);

    /**
     * Displays the given message string in an alert dialog with an "OK" button.
     *
     * @param message the message to be shown
     */
    void displayAlert(@NonNull String message);

    /**
     * Displays a simple input alert dialog with an OK and Cancel buttons.
     *
     * @param message The message to display in the alert dialog.
     * @param value The initial value to display in the input field. Can be null.
     * @param filter An optional filter to validate the input. Specify null (or
     *            a validator which always returns true) if you do not want
     *            input validation.
     * @return Null if canceled by the user. Otherwise the possibly-empty input string.
     */
    @Nullable
    String displayInput(@NonNull String message, @Nullable String value,
            @Nullable IValidator filter);

    /**
     * Renames the given node
     *
     * @param node the node to be renamed
     * @return true if renaming was handled
     */
    boolean rename(INode node);

    /**
     * Returns the minimum API level that the current Android project is targeting.
     *
     * @return the minimum API level to be supported, or -1 if it cannot be determined
     */
    int getMinApiLevel();

    /**
     * Returns a resource name validator for the current project
     *
     * @param resourceTypeName resource type, such as "id", "string", and so on
     * @param uniqueInProject if true, the resource name must be unique in the
     *            project (not already be defined anywhere else)
     * @param uniqueInLayout if true, the resource name must be unique at least
     *            within the current layout. This only applies to {@code @id}
     *            resources since only those resources can be defined in-place
     *            within a layout
     * @param exists if true, the resource name must already exist
     * @param allowed allowed names (optional). This can for example be used to
     *            request a unique-in-layout validator, but to remove the
     *            current value of the node being edited from consideration such
     *            that it allows you to leave the value the same
     * @return an {@link IValidator} for validating a new resource name in the
     *         current project
     */
    @Nullable
    IValidator getResourceValidator(@NonNull String resourceTypeName,
            boolean uniqueInProject, boolean uniqueInLayout, boolean exists,
            String... allowed);

    /**
     * Displays an input dialog where the user can enter an Android reference value
     *
     * @param currentValue the current reference to select
     * @return the reference selected by the user, or null
     */
    @Nullable
    String displayReferenceInput(@Nullable String currentValue);

    /**
     * Displays an input dialog where the user can enter an Android resource name of the
     * given resource type ("id", "string", "drawable", and so on.)
     *
     * @param currentValue the current reference to select
     * @param resourceTypeName resource type, such as "id", "string", and so on (never
     *            null)
     * @return the margins selected by the user in the same order as the input arguments,
     *         or null
     */
    @Nullable
    String displayResourceInput(@NonNull String resourceTypeName, @Nullable String currentValue);

    /**
     * Displays an input dialog tailored for editing margin properties.
     *
     * @param all The current, initial value display for "all" margins (applied to all
     *            sides)
     * @param left The current, initial value to display for the "left" margin
     * @param right The current, initial value to display for the "right" margin
     * @param top The current, initial value to display for the "top" margin
     * @param bottom The current, initial value to display for the "bottom" margin
     * @return an array of length 5 containing the user entered values for the all, left,
     *         right, top and bottom margins respectively, or null if canceled
     */
    @Nullable
    String[] displayMarginInput(
            @Nullable String all,
            @Nullable String left,
            @Nullable String right,
            @Nullable String top,
            @Nullable String bottom);

    /**
     * Displays an input dialog tailored for inputting the source of an {@code <include>}
     * layout tag. This is similar to {@link #displayResourceInput} for resource type
     * "layout", but should also attempt to filter out layout resources that cannot be
     * included from the current context (because it would result in a cyclic dependency).
     *
     * @return the layout resource to include, or null if canceled
     */
    @Nullable
    String displayIncludeSourceInput();

    /**
     * Displays an input dialog tailored for inputting the source of a {@code <fragment>}
     * layout tag.
     *
     * @return the fully qualified class name of the fragment activity, or null if canceled
     */
    @Nullable
    String displayFragmentSourceInput();

    /**
     * Displays an input dialog tailored for inputting the source of a {@code <view>}
     * layout tag.
     *
     * @return the fully qualified class name of the custom view class, or null if canceled
     */
    @Nullable
    String displayCustomViewClassInput();

    /**
     * Select the given nodes
     *
     * @param nodes the nodes to be selected, never null
     */
    void select(@NonNull Collection<INode> nodes);

    /**
     * Triggers a redraw
     */
    void redraw();

    /**
     * Triggers a layout refresh and redraw
     */
    void layout();

    /**
     * Converts a pixel to a dp (device independent pixel) for the current screen density
     *
     * @param px the pixel dimension
     * @return the corresponding dp dimension
     */
    int pxToDp(int px);

    /**
     * Converts a device independent pixel to a screen pixel for the current screen density
     *
     * @param dp the device independent pixel dimension
     * @return the corresponding pixel dimension
     */
    int dpToPx(int dp);

    /**
     * Converts an IDE screen pixel distance to the corresponding layout distance. This
     * can be used to draw annotations on the graphics object that should be unaffected by
     * the zoom, or handle mouse events within a certain pixel distance regardless of the
     * screen zoom.
     *
     * @param pixels the size in IDE screen pixels
     * @return the corresponding pixel distance in the layout coordinate system
     */
    int screenToLayout(int pixels);

    /**
     * Measure the preferred or actual ("wrap_content") size of the given nodes.
     *
     * @param parent the parent whose children should be measured
     * @param filter a filter to change attributes in the process of measuring, for
     *            example forcing the layout_width to wrap_content or the layout_weight to
     *            unset
     * @return the corresponding bounds of the nodes, or null if a rendering error occurs
     */
    @Nullable
    Map<INode, Rect> measureChildren(@NonNull INode parent, @Nullable AttributeFilter filter);

    /**
     * The {@link AttributeFilter} allows a client of
     * {@link IClientRulesEngine#measureChildren} to modify the actual XML values of the
     * nodes being rendered, for example to force width and height values to wrap_content
     * when measuring preferred size.
     */
    interface AttributeFilter {
        /**
         * Returns the attribute value for the given node and attribute name. This filter
         * allows a client to adjust the attribute values that a node presents to the
         * layout library.
         * <p>
         * Returns "" to unset an attribute. Returns null to return the unfiltered value.
         *
         * @param node the node for which the attribute value should be returned
         * @param namespace the attribute namespace
         * @param localName the attribute local name
         * @return an override value, or null to return the unfiltered value
         */
        @Nullable
        String getAttribute(
                @NonNull INode node,
                @Nullable String namespace,
                @NonNull String localName);
    }

    /**
     * Given a UI root node and a potential XML node name, returns the first available id
     * that matches the pattern "prefix%d".
     * <p/>
     * TabWidget is a special case and the method will always return "@android:id/tabs".
     *
     * @param fqcn The fully qualified class name of the view to generate a unique id for
     * @return A suitable generated id in the attribute form needed by the XML id tag
     *         (e.g. "@+id/something")
     */
    @NonNull
    String getUniqueId(@NonNull String fqcn);

    /**
     * Returns the namespace URI for attributes declared and used inside the
     * app. (This is not the Android namespace.)
     *
     * @return the namespace URI
     */
    @NonNull
    String getAppNameSpace();
}

