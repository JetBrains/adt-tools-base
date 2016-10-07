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

package com.android.build.gradle.shrinker;

import com.android.build.gradle.internal.incremental.ByteCodeUtils;
import com.android.build.gradle.shrinker.parser.FilterSpecification;
import com.android.utils.Pair;
import com.google.common.collect.Sets;

import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/**
 * Shrinker-specific logger that can be configured with -dontwarn flag.
 */
public class ShrinkerLogger {
    private final List<FilterSpecification> mDontWarnSpecs;
    private final Logger mLogger;
    private final Set<Pair<String, String>> mWarningsEmitted;

    public ShrinkerLogger(List<FilterSpecification> dontWarnSpecs, Logger logger) {
        mDontWarnSpecs = dontWarnSpecs;
        mLogger = logger;
        mWarningsEmitted = Sets.newHashSet();
    }

    synchronized void invalidClassReference(String from, String to) {
        if (from.contains(".")) {
            from = ByteCodeUtils.getClassName(from);
        }

        if (mWarningsEmitted.contains(Pair.of(from, to))) {
            return;
        }

        for (FilterSpecification dontWarnSpec : mDontWarnSpecs) {
            if (dontWarnSpec.matches(from) || dontWarnSpec.matches(to)) {
                return;
            }
        }

        mWarningsEmitted.add(Pair.of(from, to));
        mLogger.warn("{} references unknown class: {}", from, to);
    }

    synchronized void invalidMemberReference(String from, String to) {
        if (mWarningsEmitted.contains(Pair.of(from, to))) {
            return;
        }

        String fromClassName;
        if (from.contains(".")) {
            fromClassName = ByteCodeUtils.getClassName(from);
        } else {
            fromClassName = from;
        }
        String toClassName = ByteCodeUtils.getClassName(to);
        for (FilterSpecification dontWarnSpec : mDontWarnSpecs) {
            if (dontWarnSpec.matches(fromClassName) || dontWarnSpec.matches(toClassName)) {
                return;
            }
        }

        mWarningsEmitted.add(Pair.of(from, to));
        mLogger.warn("{} references unknown class member: {}", from, to);
    }

    public int getWarningsCount() {
        return mWarningsEmitted.size();
    }
}
