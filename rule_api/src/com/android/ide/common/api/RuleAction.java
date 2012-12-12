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
import com.android.utils.Pair;
import com.google.common.annotations.Beta;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A {@link RuleAction} represents an action provided by an {@link IViewRule}, typically
 * shown in a context menu or in the layout actions bar.
 * <p/>
 * Each action should have a reasonably unique ID. This is used when multiple nodes
 * are selected to filter the actions down to just those actions that are supported
 * across all selected nodes. If an action does not support multiple nodes, it can
 * return false from {@link #supportsMultipleNodes()}.
 * <p/>
 * Actions can be grouped into a hierarchy of sub-menus using the {@link NestedAction} class,
 * or into a flat submenu using the {@link Choices} class.
 * <p/>
 * Actions (including separators) all have a "sort priority", and this is used to
 * sort the menu items or toolbar buttons into a specific order.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public class RuleAction implements Comparable<RuleAction> {
    /**
     * Character used to split multiple checked choices.
     * The pipe character "|" is used, to natively match Android resource flag separators.
     */
    public final static String CHOICE_SEP = "|"; //$NON-NLS-1$

    /**
     * Same as {@link #CHOICE_SEP} but safe for use in regular expressions.
     */
    public final static String CHOICE_SEP_PATTERN = Pattern.quote(CHOICE_SEP);

    /**
     * The unique id of the action.
     * @see #getId()
     */
    private final String mId;
    /**
     * The UI-visible title of the action.
     */
    private final String mTitle;

    /** A URL pointing to an icon, or null */
    private URL mIconUrl;

    /**
     * A callback executed when the action is selected in the context menu.
     */
    private final IMenuCallback mCallback;

    /**
     * The sorting priority of this item; actions can be sorted according to these
     */
    protected final int mSortPriority;

    /**
     * Whether this action supports multiple nodes, see
     * {@link #supportsMultipleNodes()} for details.
     */
    private final boolean mSupportsMultipleNodes;

    /**
     * Special value which will insert a separator in the choices' submenu.
     */
    public final static String SEPARATOR = "----";

    // Factories

    /**
     * Constructs a new separator which will be shown in places where separators
     * are supported such as context menus
     *
     * @param sortPriority a priority used for sorting this action
     * @return a new separator
     */
    @NonNull
    public static Separator createSeparator(int sortPriority) {
        return new Separator(sortPriority, true /* supportsMultipleNodes*/);
    }

    /**
     * Constructs a new base {@link RuleAction} with its ID, title and action callback.
     *
     * @param id The unique ID of the action. Must not be null.
     * @param title The title of the action. Must not be null.
     * @param callback The callback executed when the action is selected.
     *            Must not be null.
     * @param iconUrl a URL pointing to an icon to use for this action, or null
     * @param sortPriority a priority used for sorting this action
     * @param supportsMultipleNodes whether this action supports multiple nodes,
     *            see {@link #supportsMultipleNodes()} for details
     * @return the new {@link RuleAction}
     */
    @NonNull
    public static RuleAction createAction(
            @NonNull String id,
            @NonNull String title,
            @NonNull IMenuCallback callback,
            @Nullable URL iconUrl,
            int sortPriority,
            boolean supportsMultipleNodes) {
        RuleAction action = new RuleAction(id, title, callback, sortPriority,
                supportsMultipleNodes);
        action.setIconUrl(iconUrl);

        return action;
    }

    /**
     * Creates a new immutable toggle action.
     *
     * @param id The unique id of the action. Cannot be null.
     * @param title The UI-visible title of the context menu item. Cannot be null.
     * @param isChecked Whether the context menu item has a check mark.
     * @param callback A callback to execute when the context menu item is
     *            selected.
     * @param iconUrl a URL pointing to an icon to use for this action, or null
     * @param sortPriority a priority used for sorting this action
     * @param supportsMultipleNodes whether this action supports multiple nodes,
     *            see {@link #supportsMultipleNodes()} for details
     * @return the new {@link Toggle}
     */
    @NonNull
    public static Toggle createToggle(
            @NonNull String id,
            @NonNull String title,
            boolean isChecked,
            @NonNull IMenuCallback callback,
            @Nullable URL iconUrl,
            int sortPriority,
            boolean supportsMultipleNodes) {
        Toggle toggle = new Toggle(id, title, isChecked, callback, sortPriority,
                supportsMultipleNodes);
        toggle.setIconUrl(iconUrl);
        return toggle;
    }

    /**
     * Creates a new immutable multiple-choice action with a defined ordered set
     * of action children.
     *
     * @param id The unique id of the action. Cannot be null.
     * @param title The title of the action to be displayed to the user
     * @param provider Provides the actions to be shown as children of this
     *            action
     * @param callback A callback to execute when the context menu item is
     *            selected.
     * @param iconUrl the icon to use for the multiple choice action itself
     * @param sortPriority the sorting priority to use for the multiple choice
     *            action itself
     * @param supportsMultipleNodes whether this action supports multiple nodes,
     *            see {@link #supportsMultipleNodes()} for details
     * @return the new {@link NestedAction}
     */
    @NonNull
    public static NestedAction createChoices(
            @NonNull String id,
            @NonNull String title,
            @NonNull IMenuCallback callback,
            @Nullable URL iconUrl,
            int sortPriority,
            boolean supportsMultipleNodes,
            @NonNull ActionProvider provider) {
        NestedAction choices = new NestedAction(id, title, provider, callback,
                sortPriority, supportsMultipleNodes);
        choices.setIconUrl(iconUrl);
        return choices;
    }

    /**
     * Creates a new immutable multiple-choice action with a defined ordered set
     * of children.
     *
     * @param id The unique id of the action. Cannot be null.
     * @param title The title of the action to be displayed to the user
     * @param iconUrls The icon urls for the children items (may be null)
     * @param ids The internal ids for the children
     * @param current The id(s) of the current choice(s) that will be check
     *            marked. Can be null. Can be an id not present in the choices
     *            map. There can be more than one id separated by
     *            {@link #CHOICE_SEP}.
     * @param callback A callback to execute when the context menu item is
     *            selected.
     * @param titles The UI-visible titles of the children
     * @param iconUrl the icon to use for the multiple choice action itself
     * @param sortPriority the sorting priority to use for the multiple choice
     *            action itself
     * @param supportsMultipleNodes whether this action supports multiple nodes,
     *            see {@link #supportsMultipleNodes()} for details
     * @return the new {@link Choices}
     */
    @NonNull
    public static Choices createChoices(
            @NonNull String id,
            @NonNull String title,
            @NonNull IMenuCallback callback,
            @NonNull List<String> titles,
            @Nullable List<URL> iconUrls,
            @NonNull List<String> ids,
            @Nullable String current,
            @Nullable URL iconUrl,
            int sortPriority,
            boolean supportsMultipleNodes) {
        Choices choices = new Choices(id, title, callback, titles, iconUrls,
                ids, current, sortPriority, supportsMultipleNodes);
        choices.setIconUrl(iconUrl);

        return choices;
    }

    /**
     * Creates a new immutable multiple-choice action with a defined ordered set
     * of children.
     *
     * @param id The unique id of the action. Cannot be null.
     * @param title The title of the action to be displayed to the user
     * @param iconUrls The icon urls for the children items (may be null)
     * @param current The id(s) of the current choice(s) that will be check
     *            marked. Can be null. Can be an id not present in the choices
     *            map. There can be more than one id separated by
     *            {@link #CHOICE_SEP}.
     * @param callback A callback to execute when the context menu item is
     *            selected.
     * @param iconUrl the icon to use for the multiple choice action itself
     * @param sortPriority the sorting priority to use for the multiple choice
     *            action itself
     * @param supportsMultipleNodes whether this action supports multiple nodes,
     *            see {@link #supportsMultipleNodes()} for details
     * @param idsAndTitles a list of pairs (of ids and titles) to use for the
     *            menu items
     * @return the new {@link Choices}
     */
    @NonNull
    public static Choices createChoices(
            @NonNull String id,
            @NonNull String title,
            @NonNull IMenuCallback callback,
            @Nullable List<URL> iconUrls,
            @Nullable String current,
            @Nullable URL iconUrl,
            int sortPriority,
            boolean supportsMultipleNodes,
            @NonNull List<Pair<String, String>> idsAndTitles) {
        int itemCount = idsAndTitles.size();
        List<String> titles = new ArrayList<String>(itemCount);
        List<String> ids = new ArrayList<String>(itemCount);
        for (Pair<String, String> pair : idsAndTitles) {
            ids.add(pair.getFirst());
            titles.add(pair.getSecond());
        }
        Choices choices = new Choices(id, title, callback, titles, iconUrls,
                ids, current, sortPriority, supportsMultipleNodes);
        choices.setIconUrl(iconUrl);
        return choices;
    }

    /**
     * Creates a new immutable multiple-choice action with lazily computed children.
     *
     * @param id The unique id of the action. Cannot be null.
     * @param title The title of the multiple-choice itself
     * @param callback A callback to execute when the context menu item is
     *            selected.
     * @param provider the provider which provides choices lazily
     * @param current The id(s) of the current choice(s) that will be check
     *            marked. Can be null. Can be an id not present in the choice
     *            alternatives. There can be more than one id separated by
     *            {@link #CHOICE_SEP}.
     * @param iconUrl the icon to use for the multiple choice action itself
     * @param sortPriority the sorting priority to use for the multiple choice
     *            action itself
     * @param supportsMultipleNodes whether this action supports multiple nodes,
     *            see {@link #supportsMultipleNodes()} for details
     * @return the new {@link Choices}
     */
    @NonNull
    public static Choices createChoices(
            @NonNull String id,
            @NonNull String title,
            IMenuCallback callback,
            @NonNull ChoiceProvider provider,
            @Nullable String current,
            @Nullable URL iconUrl,
            int sortPriority,
            boolean supportsMultipleNodes) {
        Choices choices = new DelayedChoices(id, title, callback,
                current, provider, sortPriority, supportsMultipleNodes);
        choices.setIconUrl(iconUrl);
        return choices;
    }

    /**
     * Creates a new {@link RuleAction} with the given id and the given title.
     * Actions which have the same id and the same title are deemed equivalent.
     *
     * @param id The unique id of the action, which must be similar for all actions that
     *           perform the same task. Cannot be null.
     * @param title The UI-visible title of the action.
     * @param callback A callback to execute when the context menu item is
     *            selected.
     * @param sortPriority a priority used for sorting this action
     * @param supportsMultipleNodes the new return value for
     *            {@link #supportsMultipleNodes()}
     */
    private RuleAction(
            @NonNull String id,
            @NonNull String title,
            @NonNull IMenuCallback callback,
            int sortPriority,
            boolean supportsMultipleNodes) {
        mId = id;
        mTitle = title;
        mSortPriority = sortPriority;
        mSupportsMultipleNodes = supportsMultipleNodes;
        mCallback = callback;
    }

    /**
     * Returns the unique id of the action. In the context of a multiple selection,
     * actions which have the same id are collapsed together and must represent the same
     * action. Cannot be null.
     *
     * @return the unique id of the action, never null
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Returns the UI-visible title of the action, shown in the context menu.
     * Cannot be null.
     *
     * @return the user name of the action, never null
     */
    @NonNull
    public String getTitle() {
        return mTitle;
    }

    /**
     * Actions which have the same id and the same title are deemed equivalent.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RuleAction) {
            RuleAction rhs = (RuleAction) obj;

            if (mId != rhs.mId && !(mId != null && mId.equals(rhs.mId))) return false;
            if (mTitle != rhs.mTitle &&
                    !(mTitle != null && mTitle.equals(rhs.mTitle))) return false;
            return true;
        }
        return false;
    }

    /**
     * Whether this action supports multiple nodes. An action which supports
     * multiple nodes can be applied to different nodes by passing in different
     * nodes to its callback. Some actions are hardcoded for a specific node (typically
     * one that isn't selected, such as an action which affects the parent of a selected
     * node), and these actions will not be added to the context menu when more than
     * one node is selected.
     *
     * @return true if this node supports multiple nodes
     */
    public boolean supportsMultipleNodes() {
        return mSupportsMultipleNodes;
    }

    /**
     * Actions which have the same id and the same title have the same hash code.
     */
    @Override
    public int hashCode() {
        int h = mId == null ? 0 : mId.hashCode();
        h = h ^ (mTitle == null ? 0 : mTitle.hashCode());
        return h;
    }

    /**
     * Gets a URL pointing to an icon to use for this action, if any.
     *
     * @return a URL pointing to an icon to use for this action, or null
     */
    public URL getIconUrl() {
        return mIconUrl;
    }

    /**
     * Sets a URL pointing to an icon to use for this action, if any.
     *
     * @param iconUrl a URL pointing to an icon to use for this action, or null
     * @return this action, to allow setter chaining
     */
    @NonNull
    public RuleAction setIconUrl(URL iconUrl) {
        mIconUrl = iconUrl;

        return this;
    }

    /**
     * Return a priority used for sorting this action
     *
     * @return a priority used for sorting this action
     */
    public int getSortPriority() {
        return mSortPriority;
    }

    /**
     * Returns the callback executed when the action is selected in the
     * context menu. Cannot be null.
     *
     * @return the callback, never null
     */
    @NonNull
    public IMenuCallback getCallback() {
        return mCallback;
    }

    // Implements Comparable<MenuAction>
    @Override
    public int compareTo(RuleAction other) {
        if (mSortPriority != other.mSortPriority) {
            return mSortPriority - other.mSortPriority;
        }

        return mTitle.compareTo(other.mTitle);
    }

    @NonNull
    @Override
    public String toString() {
        return "RuleAction [id=" + mId + ", title=" + mTitle + ", priority=" + mSortPriority + "]";
    }

    /** A separator to display between actions */
    public static class Separator extends RuleAction {
        /** Construct using the factory {@link #createSeparator(int)} */
        private Separator(int sortPriority, boolean supportsMultipleNodes) {
            super("_separator", "", IMenuCallback.NONE, sortPriority,  //$NON-NLS-1$ //$NON-NLS-2$
                    supportsMultipleNodes);
        }
    }

    /**
     * A toggle is a simple on/off action, displayed as an item in a context menu
     * with a check mark if the item is checked.
     * <p/>
     * Two toggles are equal if they have the same id, title and group-id.
     * It is expected for the checked state and action callback to be different.
     */
    public static class Toggle extends RuleAction {
        /**
         * True if the item is displayed with a check mark.
         */
        private final boolean mIsChecked;

        /**
         * Creates a new immutable toggle action.
         *
         * @param id The unique id of the action. Cannot be null.
         * @param title The UI-visible title of the context menu item. Cannot be null.
         * @param isChecked Whether the context menu item has a check mark.
         * @param callback A callback to execute when the context menu item is
         *            selected.
         */
        private Toggle(
                @NonNull String id,
                @NonNull String title,
                boolean isChecked,
                @NonNull IMenuCallback callback,
                int sortPriority,
                boolean supportsMultipleNodes) {
            super(id, title, callback, sortPriority, supportsMultipleNodes);
            mIsChecked = isChecked;
        }

        /**
         * Returns true if the item is displayed with a check mark.
         *
         * @return true if the item is displayed with a check mark.
         */
        public boolean isChecked() {
            return mIsChecked;
        }

        /**
         * Two toggles are equal if they have the same id and title.
         * It is acceptable for the checked state and action callback to be different.
         */
        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        /**
         * Two toggles have the same hash code if they have the same id and title.
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    /**
     * An ordered list of choices the user can choose between. For choosing between
     * actions, there is a {@link NestedAction} class.
     */
    public static class Choices extends RuleAction {
        protected List<String> mTitles;
        protected List<URL> mIconUrls;
        protected List<String> mIds;
        private boolean mRadio;

        /**
         * One or more id for the checked choice(s) that will be check marked.
         * Can be null. Can be an id not present in the choices map.
         */
        protected final String mCurrent;

        private Choices(
                @NonNull String id,
                @NonNull String title,
                @NonNull IMenuCallback callback,
                @NonNull List<String> titles,
                @Nullable List<URL> iconUrls,
                @NonNull List<String> ids,
                @Nullable String current,
                int sortPriority,
                boolean supportsMultipleNodes) {
            super(id, title, callback, sortPriority, supportsMultipleNodes);
            mTitles = titles;
            mIconUrls = iconUrls;
            mIds = ids;
            mCurrent = current;
        }

        /**
         * Returns the list of urls to icons to display for each choice, or null
         *
         * @return the list of urls to icons to display for each choice, or null
         */
        @Nullable
        public List<URL> getIconUrls() {
            return mIconUrls;
        }

        /**
         * Returns the list of ids for the menu choices, never null
         *
         * @return the list of ids for the menu choices, never null
         */
        @NonNull
        public List<String> getIds() {
            return mIds;
        }

        /**
         * Returns the titles to be displayed for the menu choices, never null
         *
         * @return the titles to be displayed for the menu choices, never null
         */
        @NonNull
        public List<String> getTitles() {
            return mTitles;
        }

        /**
         * Returns the current value of the choice
         *
         * @return the current value of the choice, possibly null
         */
        @Nullable
        public String getCurrent() {
            return mCurrent;
        }

        /**
         * Set whether this choice list is best visualized as a radio group (instead of a
         * dropdown)
         *
         * @param radio true if this choice list should be visualized as a radio group
         */
        public void setRadio(boolean radio) {
            mRadio = radio;
        }

        /**
         * Returns true if this choice list is best visualized as a radio group (instead
         * of a dropdown)
         *
         * @return true if this choice list should be visualized as a radio group
         */
        public boolean isRadio() {
            return mRadio;
        }
    }

    /**
     * An ordered list of actions the user can choose between. Similar to
     * {@link Choices} but for actions instead.
     */
    public static class NestedAction extends RuleAction {
        /** The provider to produce the list of nested actions when needed */
        private final ActionProvider mProvider;

        private NestedAction(
                @NonNull String id,
                @NonNull String title,
                @NonNull ActionProvider provider,
                @NonNull IMenuCallback callback,
                int sortPriority,
                boolean supportsMultipleNodes) {
            super(id, title, callback, sortPriority, supportsMultipleNodes);
            mProvider = provider;
        }

        /**
         * Returns the nested actions available for the given node
         *
         * @param node the node to look up nested actions for
         * @return a list of nested actions
         */
        @NonNull
        public List<RuleAction> getNestedActions(@NonNull INode node) {
            return mProvider.getNestedActions(node);
        }
    }

    /** Like {@link Choices}, but the set of choices is computed lazily */
    private static class DelayedChoices extends Choices {
        private final ChoiceProvider mProvider;
        private boolean mInitialized;

        private DelayedChoices(
                @NonNull String id,
                @NonNull String title,
                @NonNull IMenuCallback callback,
                @Nullable String current,
                @NonNull ChoiceProvider provider,
                int sortPriority, boolean supportsMultipleNodes) {
            super(id, title, callback, new ArrayList<String>(), new ArrayList<URL>(),
                    new ArrayList<String>(), current, sortPriority, supportsMultipleNodes);
            mProvider = provider;
        }

        private void ensureInitialized() {
            if (!mInitialized) {
                mInitialized = true;
                mProvider.addChoices(mTitles, mIconUrls, mIds);
            }
        }

        @Override
        public List<URL> getIconUrls() {
            ensureInitialized();
            return mIconUrls;
        }

        @Override
        public @NonNull List<String> getIds() {
            ensureInitialized();
            return mIds;
        }

        @Override
        public @NonNull List<String> getTitles() {
            ensureInitialized();
            return mTitles;
        }
    }

    /**
     * Provides the set of nested action choices associated with a {@link NestedAction}
     * object when they are needed. Useful for lazy initialization of context
     * menus and popup menus until they are actually needed.
     */
    public interface ActionProvider {
        /**
         * Returns the nested actions available for the given node
         *
         * @param node the node to look up nested actions for
         * @return a list of nested actions
         */
        @NonNull
        public List<RuleAction> getNestedActions(@NonNull INode node);
    }

    /**
     * Provides the set of choices associated with an {@link Choices}
     * object when they are needed. Useful for lazy initialization of context
     * menus and popup menus until they are actually needed.
     */
    public interface ChoiceProvider {
        /**
         * Adds in the needed titles, iconUrls (if any) and ids.
         * Use {@link RuleAction#SEPARATOR} to create separators.
         *
         * @param titles a list of titles that the provider should append to
         * @param iconUrls a list of icon URLs that the provider should append to
         * @param ids a list of ids that the provider should append to
         */
        public void addChoices(
                @NonNull List<String> titles,
                @NonNull List<URL> iconUrls,
                @NonNull List<String> ids);
    }
}
