/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 */
public class StringHelper {

    @NonNull
    public static String capitalize(@NonNull String string) {
        StringBuilder sb = new StringBuilder();
        sb.append(string.substring(0, 1).toUpperCase(Locale.US)).append(string.substring(1));

        return sb.toString();
    }

    @NonNull
    public static String combineAsCamelCase(@NonNull Iterable<String> stringList) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String str : stringList) {
            if (first) {
                sb.append(str);
                first = false;
            } else {
                sb.append(StringHelper.capitalize(str));
            }
        }
        return sb.toString();
    }

    /**
     * Returns a list of Strings containing the objects passed in argument.
     *
     * If the objects are strings, they are directly added to the list.
     * If the objects are collections of strings, the strings are added.
     * For other objects, the result of their toString() is added.
     * @param objects the objects to add
     * @return the list of objects.
     */
    @NonNull
    public static List<String> toStrings(@NonNull Object... objects) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Object path : objects) {
            if (path instanceof String) {
                builder.add((String) path);
            } else if (path instanceof Collection) {
                Collection pathCollection = (Collection) path;
                for (Object item : pathCollection) {
                    if (item instanceof String) {
                        builder.add((String) item);
                    } else {
                        builder.add(path.toString());
                    }
                }
            } else {
                builder.add(path.toString());
            }
        }

        return builder.build();
    }

    public static void appendCamelCase(@NonNull StringBuilder sb, @Nullable String word) {
        if (word != null) {
            if (sb.length() == 0) {
                sb.append(word);
            } else {
                sb.append(StringHelper.capitalize(word));
            }
        }
    }

}
