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

package com.android.repository.impl.sources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.SchemaModule;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link RemoteListSourceProvider}
 */
public class RemoteListSourceProviderTest extends TestCase {

    public void testSimple() throws Exception {
        MockFileOp fop = new MockFileOp();

        Map<Class<? extends RepositorySource>, Collection<SchemaModule>> permittedModules =
                Maps.newHashMap();
        permittedModules.put(RemoteListSourceProvider.GenericSite.class,
                ImmutableList.of(RepoManager.getCommonModule()));
        RemoteListSourceProvider provider = RemoteListSourceProvider
                .create("http://example.com/sourceList.xml", null, permittedModules);
        FakeDownloader downloader = new FakeDownloader(fop);
        downloader.registerUrl(new URL("http://example.com/sourceList.xml"),
                getClass().getResourceAsStream("../testData/testSourceList-1.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        List<RepositorySource> sources = provider.getSources(downloader, progress, false);
        progress.assertNoErrorsOrWarnings();
        RepositorySource source1 = sources.get(0);
        assertEquals("My Example Add-ons.", source1.getDisplayName());
        assertEquals("http://www.example.com/my_addons2.xml", source1.getUrl());
        assertEquals(ImmutableList.of(RepoManager.getCommonModule()),
                source1.getPermittedModules());

        RepositorySource source2 = sources.get(1);
        assertEquals("ありがとうございます。", source2.getDisplayName());
        assertEquals("http://www.example.co.jp/addons.xml", source2.getUrl());
        assertEquals(ImmutableList.of(RepoManager.getCommonModule()),
                source2.getPermittedModules());
    }

    public void testCache() throws Exception {
        MockFileOp fop = new MockFileOp();

        Map<Class<? extends RepositorySource>, Collection<SchemaModule>> permittedModules =
                Maps.newHashMap();
        permittedModules.put(RemoteListSourceProvider.GenericSite.class,
                ImmutableList.of(RepoManager.getCommonModule()));
        RemoteListSourceProvider provider = RemoteListSourceProvider
                .create("http://example.com/sourceList.xml", null, permittedModules);
        FakeDownloader downloader = new FakeDownloader(fop);
        downloader.registerUrl(new URL("http://example.com/sourceList.xml"),
                getClass().getResourceAsStream("../testData/testSourceList-1.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        provider.getSources(downloader, progress, false);
        progress.assertNoErrorsOrWarnings();

        Downloader failingDownloader = new Downloader() {
            @NonNull
            @Override
            public InputStream downloadAndStream(@NonNull URL url,
                    @NonNull ProgressIndicator indicator) throws IOException {
                fail("shouldn't be downloading again");
                return null;
            }

            @NonNull
            @Override
            public File downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
                    throws IOException {
                fail("shouldn't be downloading again");
                return null;
            }

            @Override
            public void downloadFully(@NonNull URL url, @Nullable File target,
                    @Nullable String checksum, @NonNull ProgressIndicator indicator)
                    throws IOException {
                fail("shouldn't be downloading again");
            }
        };

        List<RepositorySource> sources = provider.getSources(failingDownloader, progress, false);
        progress.assertNoErrorsOrWarnings();
        RepositorySource source1 = sources.get(0);
        assertEquals("My Example Add-ons.", source1.getDisplayName());
        assertEquals("http://www.example.com/my_addons2.xml", source1.getUrl());
        assertEquals(ImmutableList.of(RepoManager.getCommonModule()),
                source1.getPermittedModules());

        RepositorySource source2 = sources.get(1);
        assertEquals("ありがとうございます。", source2.getDisplayName());
        assertEquals("http://www.example.co.jp/addons.xml", source2.getUrl());
        assertEquals(ImmutableList.of(RepoManager.getCommonModule()),
                source2.getPermittedModules());
    }

    public void testForceRefresh() throws Exception {
        MockFileOp fop = new MockFileOp();

        Map<Class<? extends RepositorySource>, Collection<SchemaModule>> permittedModules =
                Maps.newHashMap();
        permittedModules.put(RemoteListSourceProvider.GenericSite.class,
                ImmutableList.of(RepoManager.getCommonModule()));
        RemoteListSourceProvider provider = RemoteListSourceProvider
                .create("http://example.com/sourceList.xml", null, permittedModules);
        FakeDownloader downloader = new FakeDownloader(fop);
        downloader.registerUrl(new URL("http://example.com/sourceList.xml"),
                getClass().getResourceAsStream("../testData/testSourceList-1.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        provider.getSources(downloader, progress, false);
        progress.assertNoErrorsOrWarnings();

        downloader.registerUrl(new URL("http://example.com/sourceList.xml"),
                getClass().getResourceAsStream("../testData/testSourceList2-1.xml"));

        List<RepositorySource> sources = provider.getSources(downloader, progress, true);
        progress.assertNoErrorsOrWarnings();
        RepositorySource source1 = sources.get(0);
        assertEquals("A different displayname from testSourceList-1", source1.getDisplayName());
        assertEquals("http://www.example.com/different_site.xml", source1.getUrl());
        assertEquals(ImmutableList.of(RepoManager.getCommonModule()),
                source1.getPermittedModules());

        RepositorySource source2 = sources.get(1);
        assertEquals("今日は土曜日です", source2.getDisplayName());
        assertEquals("http://www.example.co.jp/different_site.xml", source2.getUrl());
        assertEquals(ImmutableList.of(RepoManager.getCommonModule()),
                source2.getPermittedModules());
    }
}
