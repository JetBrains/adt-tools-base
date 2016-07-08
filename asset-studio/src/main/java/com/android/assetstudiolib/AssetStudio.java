/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.assetstudiolib;

import static com.android.SdkConstants.DOT_XML;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Iterator;
import java.util.Map;

public final class AssetStudio {

    public static final String MATERIAL_DESIGN_ICONS_PATH = "images/material_design_icons/";

    private static final Iterable<String> MATERIAL_DESIGN_ICON_CATEGORIES = ImmutableSet.of(
            "action",
            "alert",
            "av",
            "communication",
            "content",
            "device",
            "editor",
            "file",
            "hardware",
            "image",
            "maps",
            "navigation",
            "notification",
            "places",
            "social",
            "toggle");

    private AssetStudio() {
    }

    @Nullable
    public static String getPathForBasename(@NonNull String basename) {
        Generator generator = path -> GraphicGenerator.getResourcesNames(path, DOT_XML);
        return getBasenameToPathMap(generator).get(basename);
    }

    @NonNull
    @VisibleForTesting
    static Map<String, String> getBasenameToPathMap(@NonNull Generator generator) {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        int dotXmlLength = DOT_XML.length();

        for (String category : MATERIAL_DESIGN_ICON_CATEGORIES) {
            String path = MATERIAL_DESIGN_ICONS_PATH + category + '/';

            for (Iterator<String> i = generator.getResourceNames(path); i.hasNext(); ) {
                String name = i.next();
                builder.put(name.substring(0, name.length() - dotXmlLength), path + name);
            }
        }

        return builder.build();
    }

    @VisibleForTesting
    interface Generator {

        @NonNull
        Iterator<String> getResourceNames(@NonNull String path);
    }
}
