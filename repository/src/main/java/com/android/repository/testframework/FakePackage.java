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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.GenericFactory;
import com.android.repository.impl.meta.RepoPackageImpl;
import com.android.repository.impl.meta.TypeDetails;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * A fake {@link RepoPackage} (implementing both {@link LocalPackage} and {@link RemotePackage},
 * for use in unit tests.
 *
 * Currently not especially fully-featured.
 */
@SuppressWarnings("ConstantConditions")
public class FakePackage implements LocalPackage, RemotePackage {
    private final String mPath;
    private final Revision mVersion;
    private final Collection<Dependency> mDependencies;
    private TypeDetails mDetails;
    private Channel mChannel;
    private Archive mArchive;

    public FakePackage(@NonNull String path, @NonNull Revision version,
            @Nullable Collection<Dependency> dependencies) {
        mPath = path;
        mVersion = version;
        mDependencies = dependencies == null ? ImmutableList.<Dependency>of() : dependencies;
    }

    @NonNull
    @Override
    public RepositorySource getSource() {
        return null;
    }

    @Override
    public void setSource(@NonNull RepositorySource source) {
    }

    @Nullable
    @Override
    public Archive getArchive() {
        return mArchive;
    }

    public void setCompleteUrl(String url) {
        mArchive = new FakeArchive(url);
    }

    public void setChannel(Channel channel) {
        mChannel = channel;
    }

    @NonNull
    @Override
    public Channel getChannel() {
        return mChannel == null ? Channel.DEFAULT : mChannel;
    }

    @NonNull
    @Override
    public File getInstallDir(@NonNull RepoManager manager, @NonNull ProgressIndicator progress) {
        return new File(manager.getLocalPath(),
                getPath().replace(RepoPackage.PATH_SEPARATOR, File.separatorChar));
    }

    public void setTypeDetails(TypeDetails details) {
        mDetails = details;
    }

    @NonNull
    @Override
    public TypeDetails getTypeDetails() {
        return mDetails == null ? (TypeDetails) ((GenericFactory) RepoManager.getGenericModule()
                .createLatestFactory()).createGenericDetailsType() : mDetails;
    }

    @NonNull
    @Override
    public Revision getVersion() {
        return mVersion;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "fake package";
    }

    @Nullable
    @Override
    public License getLicense() {
        return null;
    }

    @NonNull
    @Override
    public Collection<Dependency> getAllDependencies() {
        return mDependencies;
    }

    @NonNull
    @Override
    public String getPath() {
        return mPath;
    }

    @Override
    public boolean obsolete() {
        return false;
    }

    @NonNull
    @Override
    public CommonFactory createFactory() {
        return null;
    }

    @NonNull
    @Override
    public RepoPackageImpl asMarshallable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(@NonNull RepoPackage o) {
        return ComparisonChain.start().compare(getPath(), o.getPath())
                .compare(getVersion(), o.getVersion()).result();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RepoPackage && ((RepoPackage) obj).getPath().equals(getPath())
                && ((RepoPackage) obj).getVersion().equals(getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getPath(), getVersion());
    }

    @NonNull
    @Override
    public File getLocation() {
        return null;
    }

    @Override
    public void setInstalledPath(@NonNull File root) {
    }

    @Override
    public String toString() {
        return mPath;
    }

    private static class FakeArchive extends Archive {

        private String mCompleteUrl;

        public FakeArchive(String url) {
            mCompleteUrl = url;
        }

        @NonNull
        @Override
        public CompleteType getComplete() {
            return new CompleteType() {
                @NonNull
                @Override
                public String getChecksum() {
                    return null;
                }

                @NonNull
                @Override
                public String getUrl() {
                    return mCompleteUrl;
                }

                @Override
                public long getSize() {
                    return 0;
                }
            };
        }

        @NonNull
        @Override
        public CommonFactory createFactory() {
            return null;
        }
    }
}
