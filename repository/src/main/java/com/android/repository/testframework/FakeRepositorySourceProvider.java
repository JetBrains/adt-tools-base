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
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;

import java.util.List;

/**
 * A {@link RepositorySourceProvider} that just returns a given list of sources, for testing.
 */
public class FakeRepositorySourceProvider implements RepositorySourceProvider {

    private List<RepositorySource> mSources;

    public FakeRepositorySourceProvider(List<RepositorySource> sources) {
        mSources = sources;
    }

    @NonNull
    @Override
    public List<RepositorySource> getSources(Downloader downloader, ProgressIndicator logger,
            boolean forceRefresh) {
        return mSources;
    }

    @Override
    public boolean addSource(@NonNull RepositorySource source) {
        return false;
    }

    @Override
    public boolean isModifiable() {
        return false;
    }

    @Override
    public void save(@NonNull ProgressIndicator progress) {

    }

    @Override
    public boolean removeSource(@NonNull RepositorySource source) {
        return false;
    }
}
