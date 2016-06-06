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

package com.android.repository.impl.installer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;

import org.junit.Test;

import java.io.File;

/**
 * Tests for {@link AbstractInstaller}
 */
public class AbstractInstallerTest {
    @Test
    public void cantInstallInChild() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/sdk/foo/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);

        FakePackage remote = new FakePackage("foo;bar", new Revision(1), null);
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(fop);

        assertFalse(new TestInstaller(remote, mgr, downloader, fop).prepare(progress));
        assertTrue(progress.getWarnings().stream().anyMatch(warning -> warning.contains("child")));
    }

    @Test
    public void cantInstallInParent() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/sdk/foo/bar/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo;bar\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);

        FakePackage remote = new FakePackage("foo", new Revision(1), null);
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(fop);

        assertFalse(new TestInstaller(remote, mgr, downloader, fop).prepare(progress));
        assertTrue(progress.getWarnings().stream().anyMatch(warning -> warning.contains("parent")));
    }

    @Test
    public void deleteUnusedDirs() throws Exception {
        MockFileOp fop = new MockFileOp();
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(new File("/sdk"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        FakePackage remote = new FakePackage("foo;bar", new Revision(1), null);
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(fop);
        // Consume temp dir 1
        FileOpUtils.getNewTempDir(AbstractPackageOperation.TEMP_DIR_PREFIX, fop);
        // prepare() will create an keep a reference to temp dir 2
        new TestInstaller(remote, mgr, downloader, fop).prepare(progress);
        File tempDir;
        // Create the remaining temp dirs
        do {
            tempDir = FileOpUtils.getNewTempDir(AbstractPackageOperation.TEMP_DIR_PREFIX, fop);
        } while (tempDir != null);
        FakePackage remote2 = new FakePackage("foo;baz", new Revision(1), null);
        TestInstaller installer = new TestInstaller(remote2, mgr, downloader, fop);
        // This will cause the unreferenced temp dirs to be GCd (and a new one created)
        installer.prepare(progress);
        // This will cause the newly created temp dir to be deleted.
        installer.complete(progress);
        for (int i = 1; i < 100; i++) {
            File dir = FileOpUtils.getTempDir(AbstractPackageOperation.TEMP_DIR_PREFIX, i);
            // Only temp dir 2 should remain, since it's still referenced by the incomplete install.
            assertTrue(fop.exists(dir) == (i == 2));
        }
    }

    private static class TestInstaller extends AbstractInstaller {

        public TestInstaller(
                @NonNull RemotePackage p,
                @NonNull RepoManager manager,
                @NonNull Downloader downloader,
                @NonNull FileOp fop) {
            super(p, manager, downloader, fop);
        }

        @Override
        protected boolean doComplete(@Nullable File installTemp,
                @NonNull ProgressIndicator progress) {
            return true;
        }

        @Override
        protected boolean doPrepare(@NonNull File installTempPath,
                @NonNull ProgressIndicator progress) {
            return true;
        }
    }
}
