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

import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.manager.RemoteRepoLoaderImpl;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Tests for {@link RemoteRepoLoaderImpl}
 */
public class RemoteRepoTest extends TestCase {

    public void testRemoteRepo() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("../testData/testRepo.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source))), null, null);
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        RemotePackage p1 = pkgs.get("dummy;foo");
        assertEquals(new Revision(1, 2, 3), p1.getVersion());
        assertEquals("the license text", p1.getLicense().getValue().trim());
        assertEquals(3, ((RemotePackageImpl) p1).getAllArchives().size());
        assertTrue(p1.getTypeDetails() instanceof TypeDetails.GenericType);
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

    public void testChannels() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                                                             "Source UI Name", true,
                                                             ImmutableSet.of(RepoManager.getCommonModule(),
                                                                     RepoManager.getGenericModule()),
                                                             null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                               getClass().getResourceAsStream("../testData/testRepoWithChannels.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
          new FakeRepositorySourceProvider(ImmutableList.of(source))), null, null);
        FakeSettingsController settings = new FakeSettingsController(false);
        Map<String, RemotePackage> pkgs = loader
          .fetchPackages(progress, downloader, settings);
        progress.assertNoErrorsOrWarnings();

        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 3), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("dummy;bar").getVersion());

        settings.setChannel(Channel.create(1));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 4), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("dummy;bar").getVersion());

        settings.setChannel(Channel.create(2));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 5), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("dummy;bar").getVersion());

        settings.setChannel(Channel.create(3));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 5), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 7), pkgs.get("dummy;bar").getVersion());
    }

    public void testFallback() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource = new SimpleRepositorySource(legacyUrl,
                "Legacy UI Name", true, ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("../testData/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source, legacySource))), null,
                (source1, settings, progress1) -> {
                    assertEquals(legacyUrl, source1.getUrl());
                    FakePackage legacy = new FakePackage("legacy", new Revision(1, 2, 9),
                            null);
                    legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                    return ImmutableSet.of(legacy);
                });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(3, pkgs.size());
        assertEquals(new Revision(1, 2, 9), pkgs.get("legacy").getVersion());
    }

    public void testNonFallbackPreferred() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource = new SimpleRepositorySource(legacyUrl,
                "Legacy UI Name", true, ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("../testData/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source, legacySource))), null,
                (source1, settings, progress1) -> {
                    assertEquals(legacyUrl, source1.getUrl());
                    FakePackage legacy = new FakePackage("dummy;foo", new Revision(1, 2, 3),
                            null);
                    legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                    return ImmutableSet.of(legacy);
                });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        assertFalse(pkgs.get("dummy;foo") instanceof FakePackage);
    }

    private static final String TEST_LOCAL_PREFERRED_REPO = "\n"
            + "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <remotePackage path=\"dummy;foo\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision><major>%1$d</major></revision>\n"
            + "        <display-name>%2$s</display-name>\n"
            + "        <archives>\n"
            + "            <archive>\n"
            + "                <complete>\n"
            + "                    <size>2345</size>\n"
            + "                    <checksum>e1b7e62ecc3925054900b70e6eb9038bba8f702a</checksum>\n"
            + "                    <url>%3$s</url>\n"
            + "                </complete>\n"
            + "            </archive>\n"
            + "        </archives>\n"
            + "    </remotePackage>\n"
            + "</repo:repository>\n"
            + "\n";

    public void testLocalPreferred() throws Exception {
        RepositorySource httpSource = new SimpleRepositorySource("http://www.example.com",
                "HTTP Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        RepositorySource fileSource = new SimpleRepositorySource("file:///foo/bar",
                "File Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        RepositorySource fileSource2 = new SimpleRepositorySource("file:///foo/bar2",
                "File Source UI Name 2", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList
                .of(new FakeRepositorySourceProvider(
                        ImmutableList.of(httpSource, fileSource, fileSource2))), null,
                null);

        // file preferred over url: relative paths
        downloader.registerUrl(new URL("http://www.example.com"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "http", "foo").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "bar").getBytes());
        Map<String, RemotePackage> pkgs =
                loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("file", pkgs.get("dummy;foo").getDisplayName());

        // file preferred over url: absolute paths
        downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "http", "http://example.com").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar2"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "file:///foo/bar2").getBytes());
        pkgs = loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("file", pkgs.get("dummy;foo").getDisplayName());

        // newer http preferred over file
        downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 2, "http", "foo").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "bar").getBytes());
        pkgs = loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("http", pkgs.get("dummy;foo").getDisplayName());
    }

    public void testNewerFallbackPreferred() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource = new SimpleRepositorySource(legacyUrl,
                "Legacy UI Name", true, ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("../testData/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source, legacySource))), null,
                (source1, settings, progress1) -> {
                    assertEquals(legacyUrl, source1.getUrl());
                    FakePackage legacy = new FakePackage("dummy;foo", new Revision(1, 2, 4),
                            null);
                    legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                    return ImmutableSet.<RemotePackage>of(legacy);
                });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        assertTrue(pkgs.get("dummy;foo") instanceof FakePackage);
    }

}
