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
 * A feedback painter paints drop feedback during a drag &amp; drop operation.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
@Beta
public interface IFeedbackPainter {
    /**
     * Paints feedback for the given target node into the given graphics context.
     *
     * @param gc The graphics context to paint into
     * @param targetNode The node being dragged
     * @param feedback The feedback data
     */
    void paint(@NonNull IGraphics gc, @NonNull INode targetNode, @NonNull DropFeedback feedback);
}
