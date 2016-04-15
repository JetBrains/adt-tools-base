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

package com.android.builder.dependency;

import com.android.builder.model.Library;

/**
 * A library that can be skipped.
 *
 * This can happen in testing artifacts when the same dependency is present in
 * both the tested artifact and the test artifact.
 *
 * @see Library#isSkipped()
 */
public interface SkippableLibrary extends Library {

    /**
     * Skips the library.
     */
    void skip();
}
