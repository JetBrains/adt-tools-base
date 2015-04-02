/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.common.rendering.legacy;

import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.resources.ResourceType;
import com.android.util.Pair;

/**
 * Intermediary class implementing parts of both the old and new project call back from the
 * layout lib API.
 *
 * Clients should use this instead of {@link IProjectCallback} to target both old and new
 * Layout Libraries.
 *
 */
@SuppressWarnings("deprecation")
public abstract class LegacyCallback implements
        com.android.ide.common.rendering.api.IProjectCallback,
        com.android.layoutlib.api.IProjectCallback {

    // ------ implementation of the old interface using the new interface.

    @Override
    public final Integer getResourceValue(String type, String name) {
        return getResourceId(ResourceType.getEnum(type), name);
    }

    @Override
    public final String[] resolveResourceValue(int id) {
        Pair<ResourceType, String> info = resolveResourceId(id);
        if (info != null) {
            return new String[] { info.getSecond(), info.getFirst().getName() };
        }

        return null;
    }

    @Override
    public final String resolveResourceValue(int[] id) {
        return resolveResourceId(id);
    }

    // ------
}
