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
import com.android.repository.api.Dependency;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
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

    protected abstract void setArchives(@Nullable Archives archives);

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

    /**
     * Convenience method to add a reference to the given channel to this package.
     */
    public void setChannel(@Nullable Channel c) {
        RemotePackageImpl.ChannelRef cr = null;
        if (c != null) {
            cr = createFactory().createChannelRefType();
            cr.setRef(c);
        }
        setChannelRef(cr);
    }

    public abstract void setChannelRef(ChannelRef cr);

    /**
     * Creates a {@link RemotePackageImpl} from an arbitrary {@link RemotePackage}. Useful if you
     * have a {@link RepoPackage} of unknown concrete type and want to marshal it using JAXB.
     * Note that only the compatible archive (if any) will be included.
     */
    @NonNull
    public static RemotePackageImpl create(@NonNull RemotePackage p) {
        CommonFactory f = (CommonFactory)RepoManager.getCommonModule().createLatestFactory();
        RemotePackageImpl result = f.createRemotePackage();
        result.setVersion(p.getVersion());
        result.setLicense(p.getLicense());
        result.setPath(p.getPath());
        for (Dependency d : p.getAllDependencies()) {
            result.addDependency(d);
        }
        result.setObsolete(p.obsolete());
        result.setTypeDetails(p.getTypeDetails());
        result.setDisplayName(p.getDisplayName());
        result.setSource(p.getSource());
        result.setChannel(p.getChannel());
        Archive archive = p.getArchive();
        if (archive != null) {
            result.addArchive(archive);
        }
        return result;
    }

    public void addArchive(Archive archive) {
        Archives archives = getArchives();
        if (archives == null) {
            archives = createFactory().createArchivesType();
            setArchives(archives);
        }
        archives.getArchive().add(archive);
    }


    @XmlTransient
    public abstract static class ChannelRef {
        @NonNull
        public abstract Channel getRef();

        public abstract void setRef(@NonNull Channel channel);
    }
}
