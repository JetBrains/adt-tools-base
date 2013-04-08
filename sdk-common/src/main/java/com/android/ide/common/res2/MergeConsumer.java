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

package com.android.ide.common.res2;

/**
 * A consumer of merges. Used with {@link DataMerger#mergeData(MergeConsumer)}.
 */
public interface MergeConsumer<I extends DataItem> {

    public static class ConsumerException extends Exception {
        public ConsumerException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Called before the merge starts.
     * @throws ConsumerException
     */
    void start() throws ConsumerException;

    /**
     * Called after the merge ends.
     * @throws ConsumerException
     */
    void end() throws ConsumerException;

    /**
     * Adds a new item.
     *
     * @param item the new item.
     *
     * @throws ConsumerException
     */
    void addItem(I item) throws ConsumerException;

    /**
     * Removes an item. Optionally pass the item that will replace this one.
     * This methods does not do the replacement. The replaced item is just there
     * in case the removal can be optimized when it's a replacement vs. a removal.
     *
     * @param removedItem the removed item.
     * @param replacedBy the optional item that replaces the removed item.
     *
     * @throws ConsumerException
     */
    void removeItem(I removedItem, I replacedBy) throws ConsumerException;
}
