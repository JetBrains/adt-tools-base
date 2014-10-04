/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.GroupableProductFlavor;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Implementation of List that create read-only GroupableProductFlavor on the fly as they are
 * queried. The list itself is immutable.
 */
public class ImmutableFlavorList implements List<GroupableProductFlavor> {

    @NonNull
    private final List<? extends GroupableProductFlavor> list;

    @NonNull
    private final ReadOnlyObjectProvider immutableObjectProvider;

    ImmutableFlavorList(
            @NonNull List<? extends GroupableProductFlavor> list,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider) {
        this.list = list;
        this.immutableObjectProvider = immutableObjectProvider;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return list.contains(o);
    }

    @NonNull
    @Override
    public Iterator<GroupableProductFlavor> iterator() {
        final Iterator<? extends GroupableProductFlavor> baseIterator = list.iterator();
        return new Iterator<GroupableProductFlavor>() {
            @Override
            public boolean hasNext() {
                return baseIterator.hasNext();
            }

            @Override
            public GroupableProductFlavor next() {
                return immutableObjectProvider.getProductFlavor(baseIterator.next());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @NonNull
    @Override
    public Object[] toArray() {
        final int count = list.size();
        Object[] array = new Object[list.size()];

        for (int i = 0 ; i < count ; i++) {
            array[i] = immutableObjectProvider.getProductFlavor(list.get(i));
        }

        return array;
    }

    @NonNull
    @Override
    public <T> T[] toArray(@NonNull T[] array) {
        final int count = list.size();
        if (array.length < count) {
            //noinspection unchecked
            array = (T[]) Array.newInstance(array.getClass().getComponentType(), count);
        }

        for (int i = 0 ; i < count ; i++) {
            //noinspection unchecked
            array[i] = (T) immutableObjectProvider.getProductFlavor(list.get(i));
        }

        for (int i = count ; i < array.length; i++) {
            array[i] = null;
        }

        return array;
    }

    @Override
    public boolean add(GroupableProductFlavor e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> objects) {
        return list.containsAll(objects);
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends GroupableProductFlavor> es) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int i, @NonNull Collection<? extends GroupableProductFlavor> es) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GroupableProductFlavor get(int i) {
        GroupableProductFlavor gpf = list.get(i);
        return immutableObjectProvider.getProductFlavor(gpf);
    }

    @Override
    public GroupableProductFlavor set(int i, GroupableProductFlavor e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int i, GroupableProductFlavor e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GroupableProductFlavor remove(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        if (o instanceof ReadOnlyProductFlavor) {
            return list.indexOf(((ReadOnlyProductFlavor) o).productFlavor);
        }
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        if (o instanceof ReadOnlyProductFlavor) {
            return list.lastIndexOf(((ReadOnlyProductFlavor) o).productFlavor);
        }
        return list.lastIndexOf(o);
    }

    @NonNull
    @Override
    public ListIterator<GroupableProductFlavor> listIterator() {
        final ListIterator<? extends GroupableProductFlavor> baseIterator = list.listIterator();
        return new ListIterator<GroupableProductFlavor>() {
            @Override
            public boolean hasNext() {
                return baseIterator.hasNext();
            }

            @Override
            public GroupableProductFlavor next() {
                return immutableObjectProvider.getProductFlavor(baseIterator.next());
            }

            @Override
            public boolean hasPrevious() {
                return baseIterator.hasPrevious();
            }

            @Override
            public GroupableProductFlavor previous() {
                return immutableObjectProvider.getProductFlavor(baseIterator.previous());
            }

            @Override
            public int nextIndex() {
                return baseIterator.nextIndex();
            }

            @Override
            public int previousIndex() {
                return baseIterator.previousIndex();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(GroupableProductFlavor productFlavor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(GroupableProductFlavor productFlavor) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @NonNull
    @Override
    public ListIterator<GroupableProductFlavor> listIterator(int i) {
        final ListIterator<? extends GroupableProductFlavor> baseIterator = list.listIterator(i);
        return new ListIterator<GroupableProductFlavor>() {
            @Override
            public boolean hasNext() {
                return baseIterator.hasNext();
            }

            @Override
            public GroupableProductFlavor next() {
                return immutableObjectProvider.getProductFlavor(baseIterator.next());
            }

            @Override
            public boolean hasPrevious() {
                return baseIterator.hasPrevious();
            }

            @Override
            public GroupableProductFlavor previous() {
                return immutableObjectProvider.getProductFlavor(baseIterator.previous());
            }

            @Override
            public int nextIndex() {
                return baseIterator.nextIndex();
            }

            @Override
            public int previousIndex() {
                return baseIterator.previousIndex();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(GroupableProductFlavor productFlavor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(GroupableProductFlavor productFlavor) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @NonNull
    @Override
    public List<GroupableProductFlavor> subList(int i, int i2) {
        throw new UnsupportedOperationException();
    }
}
