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

package com.android.build.gradle.internal.incremental;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class for generating and representing an efficient string switch where the expected input string is a closed set.
 * The hierarchy rooted at StringClassifier is an abstract syntax tree with a particular form.
 *
 * switch(s.hashCode()) {
 *   case 192: visitCase(s);
 *   case 312: visitCase(s);
 *   case 1024:
 *     if (s.equals("collided_method1")) {
 *         visit(s);
 *     } else if (s.equals("collided_method2")) {
 *         visit(s);
 *     }
 *     visitDefault();
 *   default:
 *     visitDefault();
 * }
 *
 * In cases where there are no hash collisions, only the hashCode level if switching is needed.
 */
public class StringClassifier {
    final int sortedHashes[];
    final List<String> sortedCases[];

    private StringClassifier(int sortedHashes[], List<String> sortedCases[]) {
        this.sortedHashes = sortedHashes;
        this.sortedCases = sortedCases;
    }

    /**
     * Create a new StringClassifier structure from the given set of strings.
     *
     * @param strings The closed set of strings that are used to build the StringClassifier.
     * @param forceCollisionModulus (typically null) When not null, the hash code is modulus-ed with the number.
     *                             This is useful for testing the case where hash code collides.
     * @return A StringClassifier structure representing an efficient switch.
     */
    static public StringClassifier of(Set<String> strings, final Integer forceCollisionModulus) {
        Multimap<Integer, String> buckets = Multimaps.index(strings,
                new Function<String, Integer>() {
                    @Override
                    public Integer apply(String input) {
                        if (forceCollisionModulus != null) {
                            return input.hashCode() % forceCollisionModulus;
                        }
                        return input.hashCode();
                    }
                });

        List<Map.Entry<Integer, Collection<String>>> sorted = Ordering.natural()
                .onResultOf(new Function<Map.Entry<Integer, Collection<String>>, Integer>() {
                    @Override
                    public Integer apply(Map.Entry<Integer, Collection<String>> entry) {
                        return entry.getKey();
                    }
                }).immutableSortedCopy(buckets.asMap().entrySet());

        int sortedHashes[] = new int[sorted.size()];
        List<String> sortedCases[] = new List[sorted.size()];
        int index = 0;
        for (Map.Entry<Integer, Collection<String>> entry : sorted) {
            sortedHashes[index] = entry.getKey();
            sortedCases[index] = Lists.newCopyOnWriteArrayList(entry.getValue());
            index++;
        }

        return new StringClassifier(sortedHashes, sortedCases);
    }
}
