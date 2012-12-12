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
import com.android.ide.common.api.IDragElement.IDragAttribute;
import com.google.common.annotations.Beta;

import java.util.List;


/**
 * Represents a view in the XML layout being edited.
 * Each view or layout maps to exactly one XML node, thus the name.
 * <p/>
 * The primordial characteristic of a node is the fully qualified View class name that
 * it represents (a.k.a FQCN), for example "android.view.View" or "android.widget.Button".
 * <p/>
 * There are 2 kind of nodes:
 * - Nodes matching a view actually rendered in the layout canvas have a valid "bounds"
 *   rectangle that describe their position in pixels in the canvas. <br/>
 * - Nodes created by IViewRule scripts but not yet rendered have an invalid bounds rectangle
 *   since they only exist in the uncommitted XML model and not yet in the rendered View model.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface INode {

    /**
     * Returns the FQCN of the view class represented by this node.
     */
    @NonNull
    String getFqcn();

    /**
     * Returns the bounds of this node.
     * <p/>
     * The bounds are valid when this node maps a view that is already rendered.
     * Typically, if the node is the target of a drag'n'drop operation then you can be
     * guaranteed that its bounds are known and thus are valid.
     * <p/>
     * However the bounds are invalid (e.g. not known yet) for new XML elements
     * that have just been created, e.g. by the {@link #appendChild(String)} method.
     *
     * @return A non-null rectangle, in canvas coordinates.
     */
    @NonNull
    Rect getBounds();

    /**
     * Returns the margins for this node.
     *
     * @return the margins for this node, never null
     */
    @NonNull
    Margins getMargins();

    /**
     * Returns the baseline of this node, or -1 if it has no baseline.
     * The baseline is the distance from the top down to the baseline.
     *
     * @return the baseline, or -1 if not applicable
     */
    int getBaseline();

    // ---- Hierarchy handling ----

    /**
     * Returns the root element of the view hierarchy.
     * <p/>
     * When a node is not attached to a hierarchy, it is its own root node.
     * This may return null if the {@link INode} was not created using a correct UiNode,
     * which is unlikely.
     */
    @Nullable
    INode getRoot();

    /**
     * Returns the parent node of this node, corresponding to the parent view in the layout.
     * The returned parent can be null when the node is the root element, or when the node is
     * not yet or no longer attached to the hierarchy.
     */
    @Nullable
    INode getParent();

    /**
     * Returns the list of valid children nodes. The list can be empty but not null.
     */
    @NonNull
    INode[] getChildren();


    // ---- XML Editing ---

    /**
     * Absolutely <em>all</em> calls that are going to edit the XML must be wrapped
     * by an editXml() call. This call creates both an undo context wrapper and an
     * edit-XML wrapper.
     *
     * @param undoName The UI name that will be given to the undo action.
     * @param callback The code to execute.
     */
    void editXml(@NonNull String undoName, @NonNull INodeHandler callback);

    // TODO define an exception that methods below will throw if editXml() is not wrapping
    // these calls.

    /**
     * Creates a new XML element as a child of this node's XML element.
     * <p/>
     * For this to work, the editor must have a descriptor for the given FQCN.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param viewFqcn The FQCN of the element to create. The actual XML local name will
     *  depend on whether this is an Android view or a custom project view.
     * @return The node for the newly created element. Can be null if we failed to create it.
     */
    @NonNull
    INode appendChild(@NonNull String viewFqcn);

    /**
     * Creates a new XML element as a child of this node's XML element and inserts
     * it at the specified position in the children list.
     * <p/>
     * For this to work, the editor must have a descriptor for the given FQCN.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param viewFqcn The FQCN of the element to create. The actual XML local name will
     *  depend on whether this is an Android view or a custom project view.
     * @param index Index of the child to insert before. If the index is out of bounds
     *  (less than zero or larger that current last child), appends at the end.
     * @return The node for the newly created element. Can be null if we failed to create it.
     */
    @NonNull
    INode insertChildAt(@NonNull String viewFqcn, int index);

    /**
     * Removes the given XML element child from this node's list of children.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param node The child to be deleted.
     */
    void removeChild(@NonNull INode node);

    /**
     * Sets an attribute for the underlying XML element.
     * Attributes are not written immediately -- instead the XML editor batches edits and
     * then commits them all together at once later.
     * <p/>
     * Custom attributes will be created on the fly.
     * <p/>
     * Passing an empty value actually <em>removes</em> an attribute from the XML.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param uri The XML namespace URI of the attribute.
     * @param localName The XML <em>local</em> name of the attribute to set.
     * @param value It's value. Cannot be null. An empty value <em>removes</em> the attribute.
     * @return Whether the attribute was actually set or not.
     */
    boolean setAttribute(@Nullable String uri, @NonNull String localName, @Nullable String value);

    /**
     * Returns a given XML attribute.
     * <p/>
     * This looks up an attribute in the <em>current</em> XML source, not the in-memory model.
     * That means that if called in the context of {@link #editXml(String, INodeHandler)}, the value
     * returned here is not affected by {@link #setAttribute(String, String, String)} until
     * the editXml closure is completed and the actual XML is updated.
     *
     * @param uri The XML name-space URI of the attribute.
     * @param attrName The <em>local</em> name of the attribute.
     * @return the attribute as a {@link String}, if it exists, or <code>null</code>.
     */
    @Nullable
    String getStringAttr(@Nullable String uri, @NonNull String attrName);

    /**
     * Returns the {@link IAttributeInfo} for a given attribute.
     * <p/>
     * The information is useful to determine the format of an attribute (e.g. reference, string,
     * float, enum, flag, etc.) and in the case of enums and flags also gives the possible values.
     * <p/>
     * Note: in Android resources, an enum can only take one of the possible values (e.g.
     * "visibility" can be either "visible" or "none"), whereas a flag can accept one or more
     * value (e.g. "align" can be "center_vertical|center_horizontal".)
     * <p/>
     * Note that this method does not handle custom non-android attributes. It may either
     * return null for these or it may return a synthetic "string" format attribute depending
     * on how the attribute was loaded.
     *
     * @param uri The XML name-space URI of the attribute.
     * @param attrName The <em>local</em> name of the attribute.
     * @return the {@link IAttributeInfo} if the attribute is known, or <code>null</code>.
     */
    @Nullable
    public IAttributeInfo getAttributeInfo(@Nullable String uri, @NonNull String attrName);

    /**
     * Returns the list of all attributes declared by this node's descriptor.
     * <p/>
     * This returns a fixed list of all attributes known to the view or layout descriptor.
     * This list does not depend on whether the attributes are actually used in the
     * XML for this node.
     * <p/>
     * Note that for views, the list of attributes also includes the layout attributes
     * inherited from the parent view. This means callers must not cache this list based
     * solely on the type of the node, as its attribute list changes depending on the place
     * of the view in the view hierarchy.
     * <p/>
     * If you want attributes actually written in the XML and their values, please use
     * {@link #getStringAttr(String, String)} or {@link #getLiveAttributes()} instead.
     *
     * @return A non-null possibly-empty list of {@link IAttributeInfo}.
     */
    @NonNull
    public IAttributeInfo[] getDeclaredAttributes();

    /**
     * Returns the list of classes (fully qualified class names) that are
     * contributing properties to the {@link #getDeclaredAttributes()} attribute
     * list, in order from most specific to least specific (in other words,
     * android.view.View will be last in the list.) This is usually the same as
     * the super class chain of a view, but it skips any views that do not
     * contribute attributes.
     *
     * @return a list of views classes that contribute attributes to this node,
     *         which is never null because at least android.view.View will
     *         contribute attributes.
     */
    @NonNull
    public List<String> getAttributeSources();

    /**
     * Returns the list of all attributes defined in the XML for this node.
     * <p/>
     * This looks up an attribute in the <em>current</em> XML source, not the in-memory model.
     * That means that if called in the context of {@link #editXml(String, INodeHandler)}, the value
     * returned here is not affected by {@link #setAttribute(String, String, String)} until
     * the editXml closure is completed and the actual XML is updated.
     * <p/>
     * If you want a list of all possible attributes, whether used in the XML or not by
     * this node, please see {@link #getDeclaredAttributes()} instead.
     *
     * @return A non-null possibly-empty list of {@link IAttribute}.
     */
    @NonNull
    public IAttribute[] getLiveAttributes();

    // -----------

    /**
     * An XML attribute in an {@link INode} with a namespace URI, a name and its current value.
     * <p/>
     * The name cannot be empty.
     * The namespace URI can be empty for an attribute without a namespace but is never null.
     * The value can be empty but cannot be null.
     */
    public static interface IAttribute extends IDragAttribute { }
}
