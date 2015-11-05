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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a (revisionless) package, either local, remote, or both. If both a local and remote
 * package are specified, they should represent exactly the same package, excepting the revision.
 * That is, the result of installing the remote package should be (a possibly updated version of)
 * the local package.
 */
public class UpdatablePackage implements Comparable<UpdatablePackage> {

    private LocalPackage mLocalInfo;

    private TreeSet<RemotePackage> mRemoteInfos = Sets.newTreeSet();

    public UpdatablePackage(@NonNull LocalPackage localInfo) {
        init(localInfo, null);
    }

    public UpdatablePackage(@NonNull RemotePackage remoteInfo) {
        init(null, remoteInfo);
    }

    public UpdatablePackage(@NonNull LocalPackage localInfo, @NonNull RemotePackage remoteInfo) {
        init(localInfo, remoteInfo);
    }

    private void init(@Nullable LocalPackage localPkg, @Nullable RemotePackage remotePkg) {
        assert localPkg != null || remotePkg != null;
        mLocalInfo = localPkg;
        if (remotePkg != null) {
            addRemote(remotePkg);
        }
    }

    /**
     * Adds the given remote package if this package doesn't already have a remote, or if the given
     * remote is more recent. If it is a preview, it will be returned by {@link #getRemote(boolean)}
     * only if it is specified that preview packages are desired.
     *
     * @param remote The remote package.
     */
    public void addRemote(@NonNull RemotePackage remote) {
        mRemoteInfos.add(remote);
    }

    @Nullable
    public LocalPackage getLocalInfo() {
        return mLocalInfo;
    }

    @Nullable
    public RemotePackage getRemote(boolean includePreview) {
        Iterator<RemotePackage> iter = mRemoteInfos.descendingIterator();
        while (iter.hasNext()) {
            RemotePackage p = iter.next();
            if (!p.getVersion().isPreview() || includePreview) {
                return p;
            }
        }
        return null;
    }

    public boolean hasPreview() {
        RemotePackage remote = getRemote(true);
        return remote != null && remote.getVersion().isPreview();
    }

    public boolean hasRemote(boolean includePreview) {
        return getRemote(includePreview) != null;
    }

    public boolean hasLocal() {
        return mLocalInfo != null;
    }

    @Override
    public int compareTo(@NonNull UpdatablePackage o) {
        return getRepresentative(true).compareTo(o.getRepresentative(true));
    }

    /**
     * Gets a {@link RepoPackage} (either local or remote) corresponding to this updatable package.
     * This will be the first of:
     * <ol>
     *     <li>The {@link LocalPackage} if the package is installed</li>
     *     <li>The preview {@link RemotePackage} if there is a remote preview and includePreview
     *     is true </li>
     *     <li>The remote package otherwise, or null if there is no non-preview remote.</li>
     * </ol>
     */
    @NonNull
    public RepoPackage getRepresentative(boolean includePreview) {
        if (hasLocal()) {
            return mLocalInfo;
        }
        return getRemote(includePreview);
    }

    public boolean isUpdate(boolean includePreview) {
        RemotePackage remote = getRemote(includePreview);
        return mLocalInfo != null && remote != null && mLocalInfo.compareTo(remote) < 0;
    }

    @NonNull
    public Set<RemotePackage> getAllRemotes() {
        return mRemoteInfos;
    }
}
