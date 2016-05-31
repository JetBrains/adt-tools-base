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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Various utilities to create and manipulate lists.
 */
public final class Lists {
    private Lists() {
    }

   /**
    * Creates an immutable copy of the specified list.
    *
    * @param list The list fo make a copy of, cannot be null
    *
    * @return An immutable copy of the specified list
    */
    public static <T> List<T> immutableCopy(List<T> list) {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }
}
