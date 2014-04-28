/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * Special class used in special case for {@link ILayoutPullParser#getViewCookie()}.
 * <p/>
 * This class is used as a cookie for all items added by the framework as part of the system decor,
 * and do not have a corresponding XML tag in the user's project. For example, the Navigation Bar
 * with back, home and recents buttons, the overflow menu icon in the Action Bar.
 */
@SuppressWarnings("UnusedDeclaration")  // Used by LayoutLib.
public class SystemViewCookie {

    public static final int UNKNOWN = 0;
    public static final int ACTION_BAR_OVERFLOW = 1;

    // Unimplemented constants.
    public static final int NAVIGATION_BAR_BACK = 2;
    public static final int NAVIGATION_BAR_HOME = 3;
    public static final int NAVIGATION_BAR_RECENTS = 4;

    // One of the above constants.
    private final int mType;

    public SystemViewCookie(int type) {
        mType = type;
    }

    public int getType() {
        return mType;
    }
}
