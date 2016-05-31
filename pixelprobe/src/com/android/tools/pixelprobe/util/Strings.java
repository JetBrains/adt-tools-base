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

package com.android.tools.pixelprobe.util;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Various utilities to manipulate and generate strings.
 */
public final class Strings {
    private Strings() {
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
    public static <T> String join(Collection<T> list, String delimiter) {
        return list.stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }
}
