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

package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.RepoPackage;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * A dummy install listener factory that just returns the provided list.
 */
public class FakeInstallListenerFactory implements InstallerFactory.StatusChangeListenerFactory {

    private final List<PackageOperation.StatusChangeListener> mListeners;

    public FakeInstallListenerFactory(@NonNull PackageOperation.StatusChangeListener... listeners) {
        mListeners = ImmutableList.copyOf(listeners);
    }


    @Override
    @NonNull
    public List<PackageOperation.StatusChangeListener> createListeners(@NonNull RepoPackage p) {
        return mListeners;
    }
}
