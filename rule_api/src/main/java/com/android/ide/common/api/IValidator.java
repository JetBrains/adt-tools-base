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
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

/**
 * An IValidator can validate strings and return custom messages if validation
 * fails.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public interface IValidator {
    /**
     * Validates the given input string, and return null if the text is valid,
     * and otherwise return a description for why the text is invalid. Returning
     * an empty String ("") is acceptable (but should only be done when it is
     * obvious to the user why the String is not valid.)
     *
     * @param text The input string to be validated
     * @return Null if the text is valid, and otherwise a description (possibly
     *         empty) for why the text is not valid.
     */
    @Nullable
    String validate(@NonNull String text);
}
