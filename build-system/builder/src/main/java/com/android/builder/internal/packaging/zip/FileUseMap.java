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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Sets;

import java.util.Set;
import java.util.TreeSet;

/**
 * The file use map keeps track of which parts of the zip file are used which parts are not.
 * It essentially maintains an ordered set of entries ({@link FileUseMapEntry}). Each entry either has
 * some data (an entry, the Central Directory, the EOCD) or is a free entry.
 * <p>
 * For example: [0-95, "foo/"][95-260, "xpto"][260-310, free][310-360, Central Directory]
 * [360-390,EOCD]
 * <p>
 * There are a couple of invariants in this structure: there are no gaps between map entries. The
 * map is fully covered up to its size. There are no two free entries next to each other, this
 * is guaranteed by coalescing the entries upon removal (see {@link #coalesce(FileUseMapEntry)}).
 */
class FileUseMap {
    /**
     * Size of the file according to the map.
     */
    private long mSize;

    /**
     * Tree with all intervals. Contains coverage from 0 up to {@link #mSize}. If
     * {@link #mSize} is zero then this set is empty. This is the only situation in which the map
     * will be empty.
     */
    @NonNull
    private TreeSet<FileUseMapEntry<?>> mMap;

    /**
     * Creates a new, empty file map.
     *
     * @param size the size of the file
     */
    FileUseMap(int size) {
        Preconditions.checkArgument(size >= 0, "size < 0");

        mSize = size;
        mMap = new TreeSet<FileUseMapEntry<?>>(FileUseMapEntry.COMPARE_BY_START);

        if (size > 0) {
            mMap.add(FileUseMapEntry.makeFree(0, size));
        }
    }

    /**
     * Adds a new file to the map. The interval specified by <em>entry</em> must fit inside an
     * empty entry in the map. That entry will be replaced by entry and additional free entries
     * will be added before and after if needed to make sure no spaces exist on the map.
     *
     * @param entry the entry to add
     */
    private void add(@NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkArgument(entry.getStart() < mSize, "entry.getStart() >= mSize");
        Preconditions.checkArgument(entry.getEnd() <= mSize, "entry.getEnd() > mSize");
        Preconditions.checkArgument(!entry.isFree(), "entry.isFree()");

        FileUseMapEntry container = findContainer(entry);
        Verify.verify(container.isFree(), "!container.isFree()");

        Set<FileUseMapEntry<?>> replacements = split(container, entry);
        mMap.remove(container);
        mMap.addAll(replacements);
    }

    /**
     * Removes a file from the map, replacing it with an empty one that is then coalesced with
     * neighbors (if the neighbors are free).
     *
     * @param entry the entry
     */
    void remove(@NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkState(mMap.contains(entry), "!mMap.contains(entry)");

        mMap.remove(entry);
        entry = FileUseMapEntry.makeFree(entry.getStart(), entry.getEnd());
        mMap.add(entry);
        coalesce(entry);
    }

    /**
     * Adds a new file to the map. The interval specified by (<em>start</em>,<em>end</em>) must fit
     * inside an empty entry in the map. That entry will be replaced by entry and additional free
     * entries will be added before and after if needed to make sure no spaces exist on the map.
     * <p>
     * The entry cannot extend beyong the end of the map. If necessary, extend the map using
     * {@link #extend(long)}.
     * <p>
     * It is assumed that (<em>start</em>,<em>end</em>) will fall in an empty block in the map.
     *
     * @param start the start of this entry
     * @param end the end of the entry
     * @param store extra data to store with the entry
     * @param <T> the type of data to store in the entry
     * @return the new entry
     */
    <T> FileUseMapEntry<T> add(long start, long end, @NonNull T store) {
        Preconditions.checkArgument(start >= 0, "start < 0");
        Preconditions.checkArgument(end > start, "end < start");
        Preconditions.checkArgument(store != null, "store != null");

        FileUseMapEntry<T> entry = FileUseMapEntry.makeUsed(start, end, store);
        add(entry);
        return entry;
    }

    /**
     * Finds the entry that fully contains the given one. It is assumed that one exists.
     *
     * @param entry the entry whose container we're looking for
     * @return the container
     */
    @NonNull
    private FileUseMapEntry<?> findContainer(@NonNull FileUseMapEntry<?> entry) {
        FileUseMapEntry container = mMap.floor(entry);
        Verify.verifyNotNull(container);
        Verify.verify(container.getStart() <= entry.getStart());
        Verify.verify(container.getEnd() >= entry.getEnd());

        return container;
    }

    /**
     * Splits a container to add an entry, adding new free entries before and after the provided
     * entry if needed.
     *
     * @param container the container entry, a free entry that is in {@link #mMap} that that
     * encloses <em>entry</em>
     * @param entry the entry that will be used to split <em>container</em>
     * @return a set of non-overlapping entries that completely covers <em>container</em> and that
     * includes <em>entry</em>
     */
    @NonNull
    private static Set<FileUseMapEntry<?>> split(@NonNull FileUseMapEntry<?> container,
            @NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkArgument(container.isFree(), "!container.isFree()");

        long farStart = container.getStart();
        long start = entry.getStart();
        long end = entry.getEnd();
        long farEnd = container.getEnd();

        Verify.verify(farStart <= start, "farStart > start");
        Verify.verify(start < end, "start >= end");
        Verify.verify(farEnd >= end, "farEnd < end");

        Set<FileUseMapEntry<?>> result = Sets.newHashSet();
        if (farStart < start) {
            result.add(FileUseMapEntry.makeFree(farStart, start));
        }

        result.add(entry);

        if (end < farEnd) {
            result.add(FileUseMapEntry.makeFree(end, farEnd));
        }

        return result;
    }

    /**
     * Coalesces a free entry replacing it and neighboring free entries with a single, larger
     * entry. This method does nothing if <em>entry</em> does not have free neighbors.
     *
     * @param entry the free entry to coalesce with neighbors
     */
    private void coalesce(@NonNull FileUseMapEntry<?> entry) {
        FileUseMapEntry<?> prevToMerge = null;
        long start = entry.getStart();
        if (start > 0) {
            /*
             * See if we have a previous entry to merge with this one.
             */
            prevToMerge = mMap.floor(FileUseMapEntry.makeFree(start - 1, start));
            Verify.verifyNotNull(prevToMerge);
            if (!prevToMerge.isFree()) {
                prevToMerge = null;
            }
        }

        FileUseMapEntry<?> nextToMerge = null;
        long end = entry.getEnd();
        if (end < mSize) {
            /*
             * See if we have a next entry to merge with this one.
             */
            nextToMerge = mMap.ceiling(FileUseMapEntry.makeFree(end, end + 1));
            Verify.verifyNotNull(nextToMerge);
            if (!nextToMerge.isFree()) {
                nextToMerge = null;
            }
        }

        if (prevToMerge == null && nextToMerge == null) {
            return;
        }

        long newStart = start;
        if (prevToMerge != null) {
            newStart = prevToMerge.getStart();
            mMap.remove(prevToMerge);
        }

        long newEnd = end;
        if (nextToMerge != null) {
            newEnd = nextToMerge.getEnd();
            mMap.remove(nextToMerge);
        }

        mMap.remove(entry);
        mMap.add(FileUseMapEntry.makeFree(newStart, newEnd));
    }

    /**
     * Truncates map removing the top entry if it is free and reducing the map's size.
     */
    void truncate() {
        if (mSize == 0) {
            return;
        }

        /*
         * Find the last entry.
         */
        FileUseMapEntry<?> last = mMap.last();
        Verify.verifyNotNull(last, "last == null");
        if (last.isFree()) {
            mMap.remove(last);
            mSize = last.getStart();
        }
    }

    /**
     * Obtains the size of the map.
     *
     * @return the size
     */
    long size() {
        return mSize;
    }

    /**
     * Obtains the largest used offset in the map. This will be size of the map after truncation.
     *
     * @return the size of the file discounting the last block if it is empty
     */
    long usedSize() {
        if (mSize == 0) {
            return 0;
        }

        /*
         * Find the last entry to see if it is an empty entry. If it is, we need to remove its size
         * from the returned value.
         */
        FileUseMapEntry<?> last = mMap.last();
        Verify.verifyNotNull(last, "last == null");
        if (last.isFree()) {
            mMap.remove(last);
            return last.getStart();
        }

        return mSize;
    }

    /**
     * Extends the map to guarantee it has at least <em>size</em> bytes. If the current size is
     * as large as <em>size</em>, this method does nothing.
     *
     * @param size the new size of the map that cannot be smaller that the current size
     */
    void extend(long size) {
        Preconditions.checkArgument(size >= mSize, "size < mSize");

        if (mSize == size) {
            return;
        }

        FileUseMapEntry<?> newBlock = FileUseMapEntry.makeFree(mSize, size);
        mMap.add(newBlock);

        mSize = size;

        coalesce(newBlock);
    }

    /**
     * Locates a free area in the map with at least <em>size</em> bytes such that
     * {@code ((start + alignOffset) % align == 0}. This method will try a
     * best-fit algorithm. If no free contiguous block exists in the map that can hold the provided
     * size then the first free index at the end of the map is provided. This means that the map
     * may need to be extended before data can be added.
     *
     * @param size the size of the contiguous area requested
     * @param alignOffset an offset to which alignment needs to be computed (see method description)
     * @param align alignment at the offset (see method description)
     * @return the location of the contiguous area; this may be located at the end of the map
     */
    long locateFree(long size, long alignOffset, long align) {
        Preconditions.checkArgument(size > 0, "size <= 0");

        FileUseMapEntry<?> best = null;
        long bestExtraSize = 0;
        for (FileUseMapEntry<?> curr : mMap) {
            /*
             * We don't care about blocks that aren't free.
             */
            if (!curr.isFree()) {
                continue;
            }

            /*
             * Compute any extra size we need in this block to make sure we verify the alignment.
             * There must be a better to do this...
             */
            long extraSize = (align - ((curr.getStart() + alignOffset) % align)) % align;

            /*
             * We don't care about blocks where we don't fit in.
             */
            if (curr.getSize() < (size + extraSize)) {
                continue;
            }

            /*
             * We don't care about blocks that are bigger than the best so far (otherwise this
             * wouldn't be a best-fit algorithm).
             */
            if (best != null && best.getSize() < curr.getSize()) {
                continue;
            }

            best = curr;
            bestExtraSize = extraSize;
        }

        /*
         * If no entry that could hold size is found, get the first free byte.
         */
        long firstFree = mSize;
        if (best == null && !mMap.isEmpty()) {
            FileUseMapEntry<?> last = mMap.last();
            if (last.isFree()) {
                firstFree = last.getStart();
            }
        }

        /*
         * We're done: either we found something or we didn't, in which the new entry needs to
         * be added to the end of the map.
         */
        if (best == null) {
            long extra = (align - ((firstFree + alignOffset) % align)) % align;
            return firstFree + extra;
        } else {
            return best.getStart() + bestExtraSize;
        }
    }
}
