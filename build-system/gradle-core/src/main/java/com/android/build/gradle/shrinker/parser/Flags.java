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

package com.android.build.gradle.shrinker.parser;


import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Class representing a ProGuard config file.
 *
 * <p>Mostly copied from Jack.
 */
public class Flags {

    @NonNull
    private final List<ClassSpecification> keepClassSpecs = Lists.newArrayList();

    @NonNull
    private final List<ClassSpecification> keepClassesWithMembersSpecs = Lists.newArrayList();

    @NonNull
    private final List<ClassSpecification> keepClassMembersSpecs = Lists.newArrayList();

    @NonNull
    private final List<FilterSpecification> dontWarnSpecs = Lists.newArrayList();

    private boolean mIgnoreWarnings;

    @NonNull
    public List<ClassSpecification> getKeepClassSpecs() {
        return keepClassSpecs;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassesWithMembersSpecs() {
        return keepClassesWithMembersSpecs;
    }

    @NonNull
    public List<ClassSpecification> getKeepClassMembersSpecs() {
        return keepClassMembersSpecs;
    }

    public void addKeepClassSpecification(@NonNull ClassSpecification classSpecification) {
        keepClassSpecs.add(classSpecification);
    }

    public void addKeepClassesWithMembers(@NonNull ClassSpecification classSpecification) {
        keepClassesWithMembersSpecs.add(classSpecification);
    }

    public void addKeepClassMembers(@NonNull ClassSpecification classSpecification) {
        keepClassMembersSpecs.add(classSpecification);
    }

    public void dontWarn(@NonNull FilterSpecification classSpec) {
        dontWarnSpecs.add(classSpec);
    }

    @NonNull
    public List<FilterSpecification> getDontWarnSpecs() {
        return dontWarnSpecs;
    }

    public void setIgnoreWarnings(boolean ignoreWarnings) {
        mIgnoreWarnings = ignoreWarnings;
    }

    public boolean isIgnoreWarnings() {
        return mIgnoreWarnings;
    }
}

