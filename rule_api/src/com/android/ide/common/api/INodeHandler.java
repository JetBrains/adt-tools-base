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

/**
 * A node handler is a callback which operates on a Node, such as for example
 * the implementation of an XML editing operation via
 * {@link INode#editXml(String, INodeHandler)}.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface INodeHandler {
    /**
     * Operates on the given node.
     *
     * @param node The node to be operated on
     */
    void handle(@NonNull INode node);
}
