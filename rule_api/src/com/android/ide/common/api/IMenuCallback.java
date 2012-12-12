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

import java.util.List;

/**
 * Callback interface for a {@link RuleAction}. The callback performs the actual
 * work of the action, and this level of indirection allows multiple actions (which
 * typically do not have their own class, only their own instances) to share a single
 * implementation callback class.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface IMenuCallback {
    /**
     * Performs the actual work promised by the {@link RuleAction}.
     * @param action The action being applied.
     * @param selectedNodes The nodes to apply the action to
     * @param valueId For a Choices action, the string id of the selected choice
     * @param newValue For a toggle or for a flag, true if the item is being
     *            checked, false if being unchecked. For enums this is not
     *            useful; however for flags it allows one to add or remove items
     *            to the flag's choices.
     */
    void action(
            @NonNull RuleAction action,
            @NonNull List<? extends INode> selectedNodes,
            @Nullable String valueId,
            @Nullable Boolean newValue);

    /** Callback which does nothing */
    @NonNull
    public static final IMenuCallback NONE = new IMenuCallback() {
        @Override
        public void action(
                @NonNull RuleAction action,
                @NonNull
                List<? extends INode> selectedNodes,
                @Nullable String valueId,
                @Nullable Boolean newValue) {
        }
    };
}
