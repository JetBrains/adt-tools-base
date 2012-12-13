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

import java.util.List;


/**
 * An {@link IViewRule} describes the rules that apply to a given Layout or View object
 * in the Graphical Layout Editor.
 * <p/>
 * Rules are implemented by builtin layout helpers, or 3rd party layout rule implementations
 * provided with or for a given 3rd party widget.
 * <p/>
 * A 3rd party layout rule should use the same fully qualified class name as the layout it
 * represents, plus "Rule" as a suffix. For example, the layout rule for the
 * LinearLayout class is LinearLayoutRule, in the same package.
 * <p/>
 * Rule instances are stateless. They are created once per View class to handle and are shared
 * across platforms or editor instances. As such, rules methods should never cache editor-specific
 * arguments that they might receive.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
public interface IViewRule {

    /**
     * This method is called by the rule engine when the script is first loaded.
     * It gives the rule a chance to initialize itself.
     *
     * @param fqcn The fully qualified class name of the Layout or View that will be managed by
     *   this rule. This can be cached as it will never change for the lifetime of this rule
     *   instance. This may or may not match the script's filename as it may be the fqcn of a
     *   class derived from the one this rule can handle.
     * @param engine The engine that is managing the rules. A rule can store a reference to
     *   the engine during initialization and then use it later to invoke some of the
     *   {@link IClientRulesEngine} methods for example to request user input.
     * @return True if this rule can handle the given FQCN. False if the rule can't handle the
     *   given FQCN, in which case the rule engine will find another rule matching a parent class.
     */
    boolean onInitialize(@NonNull String fqcn, @NonNull IClientRulesEngine engine);

    /**
     * This method is called by the rules engine just before the script is unloaded.
     */
    void onDispose();

    /**
     * Returns the class name to display when an element is selected in the layout editor.
     * <p/>
     * If null is returned, the layout editor will automatically shorten the class name using its
     * own heuristic, which is to keep the first 2 package components and the class name.
     * The class name is the <code>fqcn</code> argument that was given
     * to {@link #onInitialize(String,IClientRulesEngine)}.
     *
     * @return Null for the default behavior or a shortened string.
     */
    @Nullable
    String getDisplayName();

    /**
     * Invoked by the Rules Engine to produce a set of actions to customize
     * the context menu displayed for this view. The result is not cached and the
     * method is invoked every time the context menu is about to be shown.
     * <p>
     * The order of the menu items is determined by the sort priority set on
     * the actions.
     * <p/>
     * Most rules should consider calling super.{@link #addContextMenuActions(List, INode)}
     * as well.
     * <p/>
     * Menu actions are either toggles or fixed lists with one currently-selected
     * item. It is expected that the rule will need to recreate the actions with
     * different selections when a menu is going to shown, which is why the result
     * is not cached. However rules are encouraged to cache some or all of the result
     * to speed up following calls if it makes sense.
     *
     * @param actions a list of actions to add new context menu actions into. The order
     *    of the actions in this list is not important; it will be sorted by
     *    {@link RuleAction#getSortPriority()} later.
     * @param node the node to add actions for.
     */
    void addContextMenuActions(@NonNull List<RuleAction> actions, @NonNull INode node);

    /**
     * Returns the id of the default action to invoke for this view, typically when the
     * user presses F2. The id should correspond to the {@link RuleAction#getId()} returned
     * by one of the actions added by {@link #addContextMenuActions(List, INode)}.
     *
     * @param node the primary selected node
     * @return the id of default action, or null if none is default
     */
    @Nullable
    String getDefaultActionId(@NonNull INode node);

    /**
     * Invoked by the Rules Engine to ask the parent layout for the set of layout actions
     * to display in the layout bar. The layout rule should add these into the provided
     * list. The order the items are added in does not matter; the
     * {@link RuleAction#getSortPriority()} values will be used to sort the actions prior
     * to display, which makes it easier for parent rules and deriving rules to interleave
     * their respective actions.
     *
     * @param actions the list of actions to add newly registered actions into
     * @param parentNode the parent of the selection, or the selection itself if the root
     * @param targets the targeted/selected nodes, if any
     */
    void addLayoutActions(
            @NonNull List<RuleAction> actions,
            @NonNull INode parentNode,
            @NonNull List<? extends INode> targets);

    // ==== Selection ====

    /**
     * Returns a list of strings that will be displayed when a single child is being
     * selected in a layout corresponding to this rule. This gives the container a chance
     * to describe the child's layout attributes or other relevant information.
     * <p/>
     * Note that this is called only for single selections.
     * <p/>
     *
     * @param parentNode The parent of the node selected. Never null.
     * @param childNode The child node that was selected. Never null.
     * @return a list of strings to be displayed, or null or empty to display nothing
     */
    @Nullable
    List<String> getSelectionHint(@NonNull INode parentNode, @NonNull INode childNode);

    /**
     * Paints any layout-specific selection feedback for the given parent layout.
     *
     * @param graphics the graphics context to paint into
     * @param parentNode the parent layout node
     * @param childNodes the child nodes selected in the parent layout
     * @param view An instance of the view to be painted (may be null)
     */
    void paintSelectionFeedback(
            @NonNull IGraphics graphics,
            @NonNull INode parentNode,
            @NonNull List<? extends INode> childNodes,
            @Nullable Object view);

    // ==== Drag'n'drop support ====

    /**
     * Called when the d'n'd starts dragging over the target node. If
     * interested, returns a DropFeedback passed to onDrop/Move/Leave/Paint. If
     * not interested in drop, return null. Followed by a paint.
     *
     * @param targetNode the {@link INode} for the target layout receiving a
     *            drop event
     * @param targetView the corresponding View object for the target layout, or
     *            null if not known
     * @param elements an array of {@link IDragElement} element descriptors for
     *            the dragged views. When there are more than one element, the
     *            first element will always be the "primary" element (e.g. the
     *            one that the mouse is actively dragging.)
     * @return a {@link DropFeedback} object with drop state (which will be
     *         supplied to a follow-up {@link #onDropMove} call), or null if the
     *         drop should be ignored
     */
    @Nullable
    DropFeedback onDropEnter(@NonNull INode targetNode, @Nullable Object targetView,
            @Nullable IDragElement[] elements);

    /**
     * Called after onDropEnter. Returns a DropFeedback passed to
     * onDrop/Move/Leave/Paint (typically same as input one). Returning null
     * will invalidate the drop workflow.
     *
     * @param targetNode the {@link INode} for the target layout receiving a
     *            drop event
     * @param elements an array of {@link IDragElement} element descriptors for
     *            the dragged views.  When there are more than one element, the
     *            first element will always be the "primary" element (e.g. the
     *            one that the mouse is actively dragging.)
     * @param feedback the {@link DropFeedback} object created by
     *            {@link #onDropEnter(INode, Object, IDragElement[])}
     * @param where the current mouse drag position
     * @return a {@link DropFeedback} (which is usually just the same one passed
     *         into this method)
     */
    @Nullable
    DropFeedback onDropMove(
            @NonNull INode targetNode,
            @NonNull IDragElement[] elements,
            @Nullable DropFeedback feedback,
            @NonNull Point where);

    /**
     * Called when drop leaves the target without actually dropping.
     * <p/>
     * When switching between views, onDropLeave is called on the old node *after* onDropEnter
     * is called after a new node that returned a non-null feedback. The feedback received here
     * is the one given by the previous onDropEnter on the same target.
     * <p/>
     * E.g. call order is:
     * <pre>
     * - onDropEnter(node1) => feedback1
     * <i>...user moves to new view...</i>
     * - onDropEnter(node2) => feedback2
     * - onDropLeave(node1, feedback1)
     * <i>...user leaves canvas...</i>
     * - onDropLeave(node2, feedback2)
     * </pre>
     * @param targetNode the {@link INode} for the target layout receiving a
     *            drop event
     * @param elements an array of {@link IDragElement} element descriptors for
     *            the dragged views.  When there are more than one element, the
     *            first element will always be the "primary" element (e.g. the
     *            one that the mouse is actively dragging.)
     * @param feedback the {@link DropFeedback} object created by
     *            {@link #onDropEnter(INode, Object, IDragElement[])}
     */
    void onDropLeave(
            @NonNull INode targetNode,
            @NonNull IDragElement[] elements,
            @Nullable DropFeedback feedback);

    /**
     * Called when drop is released over the target to perform the actual drop.
     * <p>
     * TODO: Document that this method will be called under an edit lock so you can
     * directly manipulate the nodes without wrapping it in an
     * {@link INode#editXml(String, INodeHandler)} call.
     *
     * @param targetNode the {@link INode} for the target layout receiving a
     *            drop event
     * @param elements an array of {@link IDragElement} element descriptors for
     *            the dragged views.  When there are more than one element, the
     *            first element will always be the "primary" element (e.g. the
     *            one that the mouse is actively dragging.)
     * @param feedback the {@link DropFeedback} object created by
     *            {@link #onDropEnter(INode, Object, IDragElement[])}
     * @param where the mouse drop position
     */
    void onDropped(
            @NonNull INode targetNode,
            @NonNull IDragElement[] elements,
            @Nullable DropFeedback feedback,
            @NonNull Point where);

    /**
     * Called when pasting elements in an existing document on the selected target.
     *
     * @param targetNode The first node selected.
     * @param targetView the corresponding View object for the target layout, or
     *            null if not known
     * @param pastedElements The elements being pasted.
     */
    void onPaste(@NonNull INode targetNode, @Nullable Object targetView,
            @NonNull IDragElement[] pastedElements);

    // ==== XML Creation ====

    /**
     * Called when a view for this rule is being created. This allows for the rule to
     * customize the newly created object. Note that this method is called not just when a
     * view is created from a palette drag, but when views are constructed via a drag-move
     * (where views are created in the destination and then deleted from the source), and
     * even when views are constructed programmatically from other view rules. The
     * {@link InsertType} parameter can be used to distinguish the context for the
     * insertion. For example, the <code>DialerFilterRule</code> will insert EditText children
     * when a DialerFilter is first created, but not during a copy/paste or a move.
     *
     * @param node the newly created node (which will always be a View that applies to
     *            this {@link IViewRule})
     * @param parent the parent of the node (which may not yet contain the newly created
     *            node in its child list)
     * @param insertType whether this node was created as part of a newly created view, or
     *            as a copy, or as a move, etc.
     */
    void onCreate(@NonNull INode node, @NonNull INode parent, @NonNull InsertType insertType);

    /**
     * Called when a child for this view has been created and is being inserted into the
     * view parent for which this {@link IViewRule} applies. Allows the parent to perform
     * customizations of the object. As with {@link #onCreate}, the {@link InsertType}
     * parameter can be used to handle new creation versus moves versus copy/paste
     * operations differently.
     *
     * @param child the newly created node
     * @param parent the parent of the newly created node (which may not yet contain the
     *            newly created node in its child list)
     * @param insertType whether this node was created as part of a newly created view, or
     *            as a copy, or as a move, etc.
     */
    void onChildInserted(@NonNull INode child, @NonNull INode parent,
            @NonNull InsertType insertType);

    /**
     * Called when one or more children are about to be deleted by the user.
     * Note that children deleted programmatically from view rules (via
     * {@link INode#removeChild(INode)}) will not notify about deletion.
     * <p>
     * Note that this method will be called under an edit lock, so rules can
     * directly add/remove nodes and attributes as part of the deletion handling
     * (and their actions will be part of the same undo-unit.)
     * <p>
     * Note that when children are moved (such as when you drag a child within a
     * LinearLayout to move it from one position among the children to another),
     * that will also result in a
     * {@link #onChildInserted(INode, INode, InsertType)} (with the
     * {@code InsertType} set to {@link InsertType#MOVE_WITHIN}) and a remove
     * via this {@link #onRemovingChildren(List, INode, boolean)} method. When
     * the deletion is occurring as part of a local move (insert + delete), the
     * {@code moved} parameter to this method is set to true.
     *
     * @param deleted a nonempty list of children about to be deleted
     * @param parent the parent of the deleted children (which still contains
     *            the children since this method is called before the deletion
     *            is performed)
     * @param moved when true, the nodes are being deleted as part of a local
     *            move (where copies are inserted elsewhere)
     */
    void onRemovingChildren(@NonNull List<INode> deleted, @NonNull INode parent,
            boolean moved);

    /**
     * Called by the IDE on the parent layout when a child widget is being resized. This
     * is called once at the beginning of the resizing operation. A horizontal edge,
     * or a vertical edge, or both, can be resized simultaneously.
     *
     * @param child the widget being resized
     * @param parent the layout containing the child
     * @param horizEdge The horizontal edge being resized, or null
     * @param verticalEdge the vertical edge being resized, or null
     * @param childView an instance of the resized node view, or null if not known
     * @param parentView an instance of the parent layout view object, or null if not known
     * @return a {@link DropFeedback} object which performs an update painter callback
     *         etc.
     */
    @Nullable
    DropFeedback onResizeBegin(
            @NonNull INode child,
            @NonNull INode parent,
            @Nullable SegmentType horizEdge,
            @Nullable SegmentType verticalEdge,
            @Nullable Object childView,
            @Nullable Object parentView);

    /**
     * Called by the IDE on the parent layout when a child widget is being resized. This
     * is called repeatedly during the resize as the mouse is dragged to update the drag
     * bounds, recompute guidelines, etc. The resize has not yet been "committed" so the
     * XML should not be edited yet.
     *
     * @param feedback the {@link DropFeedback} object created in {@link #onResizeBegin}
     * @param child the widget being resized
     * @param parent the layout containing the child
     * @param newBounds the new bounds the user has chosen to resize the widget to,
     *    in absolute coordinates
     * @param modifierMask The modifier keys currently pressed by the user, as a bitmask
     *    of the constants {@link DropFeedback#MODIFIER1}, {@link DropFeedback#MODIFIER2}
     *    and {@link DropFeedback#MODIFIER3}.
     */
    void onResizeUpdate(
            @Nullable DropFeedback feedback,
            @NonNull INode child,
            @NonNull INode parent,
            @NonNull Rect newBounds,
            int modifierMask);

    /**
     * Called by the IDE on the parent layout when a child widget is being resized. This
     * is called once at the end of the resize operation, if it was not canceled.
     * This method can call {@link INode#editXml} to update the node to reflect the
     * new bounds.
     *
     * @param feedback the {@link DropFeedback} object created in {@link #onResizeBegin}
     * @param child the widget being resized
     * @param parent the layout containing the child
     * @param newBounds the new bounds the user has chosen to resize the widget to,
     *    in absolute coordinates
     */
    void onResizeEnd(
            @Nullable DropFeedback feedback,
            @NonNull INode child,
            @NonNull INode parent,
            @NonNull Rect newBounds);
}
