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

package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Channel;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

/**
 * An implementation of {@link RemotePackage} that can be created as part of a {@link Repository}
 * by JAXB.
 */
@XmlTransient
public abstract class RemotePackageImpl extends RepoPackageImpl implements RemotePackage {
    @XmlTransient
    private RepositorySource mSource;
    @Override
    public void setSource(@NonNull RepositorySource source) {
        mSource = source;
    }

    @Override
    @Nullable
    public Archive getArchive() {
        for (Archive archive : getArchives().getArchive()) {
            if (archive.isCompatible()) {
                return archive;
            }
        }
        return null;

    }

    /**
     * Convenience method to get all the {@link Archive}s included in this package.
     */
    @VisibleForTesting
    @NonNull
    public List<Archive> getAllArchives() {
        return getArchives().getArchive();
    }

    @NonNull
    protected abstract Archives getArchives();

    @Override
    @XmlTransient
    @NonNull
    public RepositorySource getSource() {
        assert mSource != null : "Tried to get source before it was initialized!";
        return mSource;
    }

    protected abstract ChannelRef getChannelRef();

    @NonNull
    @Override
    public Channel getChannel() {
        return getChannelRef() == null ? Channel.DEFAULT : getChannelRef().getRef();
    }

    @XmlTransient
    public abstract static class ChannelRef {
        @NonNull
        public abstract Channel getRef();

        public abstract void setRef(@NonNull Channel channel);
    }
}
