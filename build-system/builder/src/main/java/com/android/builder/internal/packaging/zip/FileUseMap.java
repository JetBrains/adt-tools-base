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
import com.android.annotations.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * The file use map keeps track of which parts of the zip file are used which parts are not.
 * It essentially maintains an ordered set of entries ({@link FileUseMapEntry}). Each entry either has
 * some data (an entry, the Central Directory, the EOCD) or is a free entry.
 *
 * <p>For example: [0-95, "foo/"][95-260, "xpto"][260-310, free][310-360, Central Directory]
 * [360-390,EOCD]
 *
 * <p>There are a few invariants in this structure:
 * <ul>
 *  <li>there are no gaps between map entries;
 *  <li>the map is fully covered up to its size;
 *  <li>there are no two free entries next to each other; this is guaranteed by coalescing the
 *  entries upon removal (see {@link #coalesce(FileUseMapEntry)});
 *  <li>all free entries have a minimum size defined in the constructor, with the possible exception
 *  of the last one
 * </ul>
 */
class FileUseMap {
    /**
     * Size of the file according to the map. This should always match the last entry in
     * {@code #mMap}.
     */
    private long mSize;

    /**
     * Tree with all intervals ordered by position. Contains coverage from 0 up to {@link #mSize}.
     * If {@link #mSize} is zero then this set is empty. This is the only situation in which the map
     * will be empty.
     */
    @NonNull
    private TreeSet<FileUseMapEntry<?>> mMap;

    /**
     * Tree with all free blocks ordered by size. This is essentially a view over {@link #mMap}
     * containing only the free blocks, but in a different order.
     */
    @NonNull
    private TreeSet<FileUseMapEntry<?>> mFree;

    /**
     * If defined, defines the minimum size for a free entry.
     */
    private int mMinFreeSize;

    /**
     * Creates a new, empty file map.
     *
     * @param size the size of the file
     * @param minFreeSize minimum size of a free entry
     */
    FileUseMap(long size, int minFreeSize) {
        Preconditions.checkArgument(size >= 0, "size < 0");
        Preconditions.checkArgument(minFreeSize >= 0, "minFreeSize < 0");

        mSize = size;
        mMap = new TreeSet<>(FileUseMapEntry.COMPARE_BY_START);
        mFree = new TreeSet<>(FileUseMapEntry.COMPARE_BY_SIZE);
        mMinFreeSize = minFreeSize;

        if (size > 0) {
            internalAdd(FileUseMapEntry.makeFree(0, size));
        }
    }

    /**
     * Adds an entry to the internal structures.
     *
     * @param entry the entry to add
     */
    private void internalAdd(@NonNull FileUseMapEntry<?> entry) {
        mMap.add(entry);

        if (entry.isFree()) {
            mFree.add(entry);
        }
    }

    /**
     * Removes an entry from the internal structures.
     *
     * @param entry the entry to remove
     */
    private void internalRemove(@NonNull FileUseMapEntry<?> entry) {
        boolean wasRemoved = mMap.remove(entry);
        Preconditions.checkState(wasRemoved, "entry not in mMap");

        if (entry.isFree()) {
            mFree.remove(entry);
        }
    }

    /**
     * Adds a new file to the map. The interval specified by {@code entry} must fit inside an
     * empty entry in the map. That entry will be replaced by entry and additional free entries
     * will be added before and after if needed to make sure no spaces exist on the map.
     *
     * @param entry the entry to add
     */
    private void add(@NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkArgument(entry.getStart() < mSize, "entry.getStart() >= mSize");
        Preconditions.checkArgument(entry.getEnd() <= mSize, "entry.getEnd() > mSize");
        Preconditions.checkArgument(!entry.isFree(), "entry.isFree()");

        FileUseMapEntry<?> container = findContainer(entry);
        Verify.verify(container.isFree(), "!container.isFree()");

        Set<FileUseMapEntry<?>> replacements = split(container, entry);
        internalRemove(container);
        for (FileUseMapEntry<?> r : replacements) {
            internalAdd(r);
        }
    }

    /**
     * Removes a file from the map, replacing it with an empty one that is then coalesced with
     * neighbors (if the neighbors are free).
     *
     * @param entry the entry
     */
    void remove(@NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkState(mMap.contains(entry), "!mMap.contains(entry)");
        Preconditions.checkArgument(!entry.isFree(), "entry.isFree()");

        internalRemove(entry);

        FileUseMapEntry<?> replacement = FileUseMapEntry.makeFree(entry.getStart(), entry.getEnd());
        internalAdd(replacement);
        coalesce(replacement);
    }

    /**
     * Adds a new file to the map. The interval specified by ({@code start}, {@code end}) must fit
     * inside an empty entry in the map. That entry will be replaced by entry and additional free
     * entries will be added before and after if needed to make sure no spaces exist on the map.
     *
     * <p>The entry cannot extend beyong the end of the map. If necessary, extend the map using
     * {@link #extend(long)}.
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
     * encloses {@code entry}
     * @param entry the entry that will be used to split {@code container}
     * @return a set of non-overlapping entries that completely covers {@code container} and that
     * includes {@code entry}
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
     * entry. This method does nothing if {@code entry} does not have free neighbors.
     *
     * @param entry the free entry to coalesce with neighbors
     */
    private void coalesce(@NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkArgument(entry.isFree(), "!entry.isFree()");

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
            internalRemove(prevToMerge);
        }

        long newEnd = end;
        if (nextToMerge != null) {
            newEnd = nextToMerge.getEnd();
            internalRemove(nextToMerge);
        }

        internalRemove(entry);
        internalAdd(FileUseMapEntry.makeFree(newStart, newEnd));
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
            internalRemove(last);
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
            return last.getStart();
        } else {
            Verify.verify(last.getEnd() == mSize);
            return mSize;
        }
    }

    /**
     * Extends the map to guarantee it has at least {@code size} bytes. If the current size is
     * as large as {@code size}, this method does nothing.
     *
     * @param size the new size of the map that cannot be smaller that the current size
     */
    void extend(long size) {
        Preconditions.checkArgument(size >= mSize, "size < mSize");

        if (mSize == size) {
            return;
        }

        FileUseMapEntry<?> newBlock = FileUseMapEntry.makeFree(mSize, size);
        internalAdd(newBlock);

        mSize = size;

        coalesce(newBlock);
    }

    /**
     * Locates a free area in the map with at least {@code size} bytes such that
     * {@code ((start + alignOffset) % align == 0} and such that the free space before {@code start}
     * is not smaller than the minimum free entry size. This method will follow the algorithm
     * specified by {@code alg}.
     *
     * <p>If no free contiguous block exists in the map that can hold the provided
     * size then the first free index at the end of the map is provided. This means that the map
     * may need to be extended before data can be added.
     *
     * @param size the size of the contiguous area requested
     * @param alignOffset an offset to which alignment needs to be computed (see method description)
     * @param align alignment at the offset (see method description)
     * @param alg which algorithm to use
     * @return the location of the contiguous area; this may be located at the end of the map
     */
    long locateFree(long size, long alignOffset, long align, @NonNull PositionAlgorithm alg) {
        Preconditions.checkArgument(size > 0, "size <= 0");

        FileUseMapEntry<?> minimumSizedEntry = FileUseMapEntry.makeFree(0, size);
        SortedSet<FileUseMapEntry<?>> matches;

        switch (alg) {
            case BEST_FIT:
                matches = mFree.tailSet(minimumSizedEntry);
                break;
            case FIRST_FIT:
                matches = mMap;
                break;
            default:
                throw new AssertionError();
        }

        FileUseMapEntry<?> best = null;
        long bestExtraSize = 0;
        for (FileUseMapEntry<?> curr : matches) {
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
            long extraSize;
            if (align == 0) {
                extraSize = 0;
            } else {
                extraSize = (align - ((curr.getStart() + alignOffset) % align)) % align;
            }

            /*
             * We can't leave than mMinFreeSize before. So if the extraSize is less than
             * mMinFreeSize, we have to increase it by 'align' as many times as needed. For
             * example, if mMinFreeSize is 20, align 4 and extraSize is 5. We need to increase it
             * to 21 (5 + 4 * 4)
             */
            if (extraSize > 0 && extraSize < mMinFreeSize) {
                int addAlignBlocks =
                        Ints.checkedCast((mMinFreeSize - extraSize + align - 1) / align);
                extraSize += addAlignBlocks * align;
            }

            /*
             * We don't care about blocks where we don't fit in.
             */
            if (curr.getSize() < (size + extraSize)) {
                continue;
            }

            /*
             * We don't care about blocks that leave less than the minimum size after. There are
             * two exceptions: (1) this is the last block and (2) the next block is free in which
             * case, after coalescing, the free block with have at least the minimum size.
             */
            long emptySpaceLeft = curr.getSize() - (size + extraSize);
            if (emptySpaceLeft > 0 && emptySpaceLeft < mMinFreeSize) {
                FileUseMapEntry<?> next = mMap.higher(curr);
                if (next != null && !next.isFree()) {
                    continue;
                }
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

            /*
             * If we're doing first fit, we don't want to search for a better one :)
             */
            if (alg == PositionAlgorithm.FIRST_FIT) {
                break;
            }
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

            /*
             * If adding this entry at the end would create a space smaller than the minimum,
             * push it for 'align' bytes forward.
             */
            if (extra > 0) {
                if (extra < mMinFreeSize) {
                    extra += align * (((mMinFreeSize - extra) + (align - 1)) / align);
                }
            }

            return firstFree + extra;
        } else {
            return best.getStart() + bestExtraSize;
        }
    }

    /**
     * Obtains all free areas of the map, excluding any trailing free area.
     *
     * @return all free areas, an empty set if there are no free areas; the areas are returned
     * in file order, that is, if area {@code x} starts before area {@code y}, then area {@code x}
     * will be stored before area {@code y} in the list
     */
    @NonNull
    List<FileUseMapEntry<?>> getFreeAreas() {
        List<FileUseMapEntry<?>> freeAreas = Lists.newArrayList();

        for (FileUseMapEntry<?> area : mMap) {
            if (area.isFree() && area.getEnd() != mSize) {
                freeAreas.add(area);
            }
        }

        return freeAreas;
    }

    /**
     * Obtains the entry that is located before the one provided.
     *
     * @param entry the map entry to get the previous one for; must belong to the map
     * @return the entry before the provided one, {@code null} if {@code entry} is the first entry
     * in the map
     */
    @Nullable
    FileUseMapEntry<?> before(@NonNull FileUseMapEntry<?> entry) {
        Preconditions.checkNotNull(entry, "entry == null");

        return mMap.lower(entry);
    }

    @Override
    public String toString() {
        StringJoiner j = new StringJoiner(", ");
        mMap.stream()
                .map(e -> e.getStart() + " - " + e.getEnd() + ": " + e.getStore())
                .forEach(j::add);
        return "FileUseMap[" + j.toString() + "]";
    }

    /**
     * Algorithms used to position entries in blocks.
     */
    public enum PositionAlgorithm {
        /**
         * Best fit: finds the smallest free block that can receive the entry.
         */
        BEST_FIT,

        /**
         * First fit: finds the first free block that can receive the entry.
         */
        FIRST_FIT
    }
}
