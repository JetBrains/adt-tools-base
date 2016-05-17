/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.pixelprobe;

import java.util.Collection;

/**
 * Various utilities to manipulate and generate strings.
 */
final class StringUtils {
    private StringUtils() {
    }

    /**
     * Joins the specified collection's items in a single string, using
     * a delimiter between each item.
     *
     * @param list The list to join
     * @param delimiter The delimiter to insert between each item
     *
     * @return A String containing the String representation of each item
     *         in the collection
     */
    static <T> String join(Collection<T> list, String delimiter) {
        StringBuilder builder = new StringBuilder();

        int count = 0;
        for (T element : list) {
            builder.append(element.toString());
            if (count != list.size() - 1) {
                builder.append(delimiter);
            }
            count++;
        }

        return builder.toString();
    }
}
