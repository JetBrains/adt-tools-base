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

package com.android.build.gradle.internal.scope;

import com.android.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Decorated {@link Supplier} that contains the reference to the supplier's task.
 */
public interface SupplierTask<T> extends Supplier<T> {

    /**
     * Returns the task that produced the supplied element. It can be null which mean
     * no task was associated with providing the element.
     * @return the supplied element task or null if none.
     */
    @Nullable
    AndroidTask<?> getBuilderTask();
}
