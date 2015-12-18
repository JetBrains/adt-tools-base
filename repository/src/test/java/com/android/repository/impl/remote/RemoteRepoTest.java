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

package com.android.repository.impl.remote;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SettingsController;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import junit.framework.TestCase;

import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Tests for {@link com.android.repository.impl.manager.RemoteRepoLoader}
 */
public class RemoteRepoTest extends TestCase {

    public void testRemoteRepo() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getCommonModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("../testData/testRepo.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoader(ImmutableList.<RepositorySourceProvider>of(
                new FakeRepositorySourceProvider(ImmutableList.of(source))), null, null);
        Multimap<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        RemotePackage p1 = pkgs.get("dummy;foo").iterator().next();
        assertEquals(new Revision(1, 2, 3), p1.getVersion());
        assertEquals("the license text", p1.getLicense().getValue().trim());
        assertEquals(3, ((RemotePackageImpl) p1).getAllArchives().size());
        assertTrue(p1.getTypeDetails() == null);
        Collection<Archive> archives = ((RemotePackageImpl) p1).getAllArchives();
        assertEquals(3, archives.size());
        Iterator<Archive> archiveIter = ((RemotePackageImpl) p1).getAllArchives().iterator();
        Archive a1 = archiveIter.next();
        assertEquals(1234, a1.getComplete().getSize());
        Archive a2 = archiveIter.next();
        Iterator<Archive.PatchType> patchIter = a2.getAllPatches().iterator();
        Archive.PatchType patch = patchIter.next();
        assertEquals(new Revision(1, 3, 2), patch.getBasedOn().toRevision());
        patch = patchIter.next();
        assertEquals(new Revision(2), patch.getBasedOn().toRevision());
    }

    private static class FakeRepositorySourceProvider implements RepositorySourceProvider {

        private List<RepositorySource> mSources;

        public FakeRepositorySourceProvider(List<RepositorySource> sources) {
            mSources = sources;
        }

        @NonNull
        @Override
        public List<RepositorySource> getSources(Downloader downloader, SettingsController settings,
                ProgressIndicator logger, boolean forceRefresh) {
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
}
