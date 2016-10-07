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
package com.android.sdklib.repository;

import com.android.prefs.AndroidLocation;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Tests for {@link RepositorySourceProvider}s.
 */
public class AddonListSourceProviderTest extends TestCase {

    public void testRemoteSource() throws Exception {
        MockFileOp fop = new MockFileOp();
        FakeDownloader downloader = new FakeDownloader(fop);
        AndroidSdkHandler handler = new AndroidSdkHandler(null, fop);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepositorySourceProvider provider = handler.getRemoteListSourceProvider(progress);

        downloader
                .registerUrl(new URL("https://dl.google.com/android/repository/addons_list-1.xml"),
                        getClass().getResourceAsStream("testdata/addons_list_sample_1.xml"));
        List<RepositorySource> sources = provider.getSources(downloader, progress, false);
        progress.assertNoErrorsOrWarnings();
        assertEquals(4, sources.size());
        assertEquals("ありがとうございます。", sources.get(1).getDisplayName());
        assertEquals(ImmutableSet.of(AndroidSdkHandler.getAddonModule()),
                sources.get(1).getPermittedModules());
        downloader
                .registerUrl(new URL("https://dl.google.com/android/repository/addons_list-2.xml"),
                        getClass().getResourceAsStream("testdata/addons_list_sample_2.xml"));

        sources = provider.getSources(downloader, progress, true);
        progress.assertNoErrorsOrWarnings();
        assertEquals(6, sources.size());
        assertEquals("ありがとうございます。", sources.get(1).getDisplayName());
        assertEquals(ImmutableSet.of(AndroidSdkHandler.getAddonModule()),
                sources.get(1).getPermittedModules());
        assertEquals(ImmutableSet.of(AndroidSdkHandler.getSysImgModule()),
                sources.get(3).getPermittedModules());
        // TODO more tests

        downloader
                .registerUrl(new URL("https://dl.google.com/android/repository/addons_list-3.xml"),
                        getClass().getResourceAsStream("testdata/addons_list_sample_3.xml"));

        sources = provider.getSources(downloader, progress, true);
        progress.assertNoErrorsOrWarnings();
        assertEquals(6, sources.size());
        assertEquals(ImmutableSet.of(AndroidSdkHandler.getAddonModule()),
                sources.get(1).getPermittedModules());
        assertEquals(ImmutableSet.of(AndroidSdkHandler.getSysImgModule()),
                sources.get(3).getPermittedModules());
        assertEquals("http://www.example.com/my_addons2.xml", sources.get(0).getUrl());
    }

    public void testLocalSource() throws Exception {
        AndroidLocation.resetFolder();
        MockFileOp fop = new MockFileOp();
        File testFile = new File(getClass().getResource("testdata/repositories.xml").toURI());
        fop.recordExistingFile(
                new File(AndroidLocation.getFolder(), AndroidSdkHandler.LOCAL_ADDONS_FILENAME)
                        .getAbsolutePath(),
                FileUtils.loadFileWithUnixLineSeparators(testFile));
        AndroidSdkHandler handler = new AndroidSdkHandler(null, fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        handler.getSdkManager(progress);
        RepositorySourceProvider provider = handler.getUserSourceProvider(progress);
        List<RepositorySource> result = provider.getSources(null, progress, false);
        progress.assertNoErrorsOrWarnings();
        assertEquals(3, result.size());
        RepositorySource s0 = result.get(0);
        assertEquals("samsung", s0.getDisplayName());
        assertEquals("http://developer.samsung.com/sdk-manager/repository/Samsung-SDK.xml",
                s0.getUrl());
        RepositorySource s2 = result.get(2);
        assertEquals("amazon", s2.getDisplayName());
        assertEquals("https://s3.amazonaws.com/android-sdk-manager/redist/addon.xml", s2.getUrl());
    }
}
