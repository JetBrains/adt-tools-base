/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.common.rendering.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Rendering parameters for a {@link RenderSession}.
 */
public class SessionParams extends RenderParams {

    public static enum RenderingMode {
        NORMAL(false, false),
        V_SCROLL(false, true),
        H_SCROLL(true, false),
        FULL_EXPAND(true, true);

        private final boolean mHorizExpand;
        private final boolean mVertExpand;

        private RenderingMode(boolean horizExpand, boolean vertExpand) {
            mHorizExpand = horizExpand;
            mVertExpand = vertExpand;
        }

        public boolean isHorizExpand() {
            return mHorizExpand;
        }

        public boolean isVertExpand() {
            return mVertExpand;
        }
    }

    private final ILayoutPullParser mLayoutDescription;
    private final RenderingMode mRenderingMode;
    private boolean mLayoutOnly = false;
    private Map<ResourceReference, AdapterBinding> mAdapterBindingMap;
    private boolean mExtendedViewInfoMode = false;

    /**
     *
     * @param layoutDescription the {@link ILayoutPullParser} letting the LayoutLib Bridge visit the
     * layout file.
     * @param renderingMode The rendering mode.
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param hardwareConfig the {@link HardwareConfig}.
     * @param renderResources a {@link RenderResources} object providing access to the resources.
     * @param projectCallback The {@link IProjectCallback} object to get information from
     * the project.
     * @param minSdkVersion the minSdkVersion of the project
     * @param targetSdkVersion the targetSdkVersion of the project
     * @param log the object responsible for displaying warning/errors to the user.
     */
    public SessionParams(
            ILayoutPullParser layoutDescription,
            RenderingMode renderingMode,
            Object projectKey,
            HardwareConfig hardwareConfig,
            RenderResources renderResources,
            IProjectCallback projectCallback,
            int minSdkVersion, int targetSdkVersion,
            LayoutLog log) {
        super(projectKey, hardwareConfig,
                renderResources, projectCallback, minSdkVersion, targetSdkVersion, log);

        mLayoutDescription = layoutDescription;
        mRenderingMode = renderingMode;
    }

    public SessionParams(SessionParams params) {
        super(params);
        mLayoutDescription = params.mLayoutDescription;
        mRenderingMode = params.mRenderingMode;
        if (params.mAdapterBindingMap != null) {
            mAdapterBindingMap = new HashMap<ResourceReference, AdapterBinding>(
                    params.mAdapterBindingMap);
        }
        mExtendedViewInfoMode = params.mExtendedViewInfoMode;
    }

    public ILayoutPullParser getLayoutDescription() {
        return mLayoutDescription;
    }

    public RenderingMode getRenderingMode() {
        return mRenderingMode;
    }

    public void setLayoutOnly() {
        mLayoutOnly = true;
    }

    public boolean isLayoutOnly() {
        return mLayoutOnly;
    }

    public void addAdapterBinding(ResourceReference reference, AdapterBinding data) {
        if (mAdapterBindingMap == null) {
            mAdapterBindingMap = new HashMap<ResourceReference, AdapterBinding>();
        }

        mAdapterBindingMap.put(reference, data);
    }

    public Map<ResourceReference, AdapterBinding> getAdapterBindings() {
        if (mAdapterBindingMap == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(mAdapterBindingMap);
    }

    public void setExtendedViewInfoMode(boolean mode) {
        mExtendedViewInfoMode = mode;
    }

    public boolean getExtendedViewInfoMode() {
        return mExtendedViewInfoMode;
    }
}
