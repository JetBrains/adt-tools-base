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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for {@link BasicInstallerFactory}.
 *
 * TODO: more tests.
 */
public class BasicInstallerTest extends TestCase {

    public void testDelete() throws Exception {
        MockFileOp fop = new MockFileOp();
        // Record package.xmls for two packages.
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("../testData/testPackage.xml")));
        fop.recordExistingFile("/repo/dummy/bar/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("../testData/testPackage2.xml")));

        // Set up a RepoManager.
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);

        FakeProgressRunner runner = new FakeProgressRunner();
        // Load the local packages.
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, new FakeDownloader(fop), new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages pkgs = mgr.getPackages();

        // Get one of the packages to uninstall.
        LocalPackage p = pkgs.getLocalPackages().get("dummy;foo");
        // Uninstall it
        InstallerFactory factory = new BasicInstallerFactory();
        factory.createUninstaller(p, mgr, fop).uninstall(new FakeProgressIndicator());
        File[] contents = fop.listFiles(root);
        // Verify that the deleted dir is gone.
        assertEquals(1, contents.length);
        contents = fop.listFiles(contents[0]);
        assertEquals(1, contents.length);
        assertEquals(new File("/repo/dummy/bar"), contents[0]);
    }

    // Test installing a new package
    public void testInstallFresh() throws Exception {
        MockFileOp fop = new MockFileOp();
        // We have a different package installed already.
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("../testData/testPackage.xml")));
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl, getClass().getResourceAsStream("../testData/testRepo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/dir/b"));
        zos.write("contents2".getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);

        // Ensure we picked up the local package.
        RepositoryPackages pkgs = mgr.getPackages();
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        assertEquals(1, pkgs.getLocalPackages().size());
        assertEquals(2, pkgs.getRemotePackages().size());

        // Install one of the packages.
        RemotePackage p = pkgs.getRemotePackages().get("dummy;bar");
        Installer basicInstaller = new BasicInstallerFactory().createInstaller(p, mgr, fop);
        basicInstaller.prepareInstall(downloader, runner.getProgressIndicator());
        basicInstaller.completeInstall(runner.getProgressIndicator());
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        // Reload the packages.
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        File[] contents = fop.listFiles(new File(root, "dummy"));

        // Ensure it was installed on the filesystem
        assertEquals(2, contents.length);
        assertEquals(new File(root, "dummy/bar"), contents[0]);
        assertEquals(new File(root, "dummy/foo"), contents[1]);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        assertTrue(locals.containsKey("dummy;bar"));
        LocalPackage newPkg = locals.get("dummy;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals("license text 2", newPkg.getLicense().getValue().trim());
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());
    }

    // Test installing an upgrade to an existing package.
    public void testInstallUpgrade() throws Exception {
        MockFileOp fop = new MockFileOp();
        // Record a couple existing packages.
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("../testData/testPackage.xml")));
        fop.recordExistingFile("/repo/dummy/bar/package.xml", ByteStreams.toByteArray(
                getClass().getResourceAsStream("../testData/testPackage2-lowerVersion.xml")));
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);

        // Create the archive and register the repo to be downloaded.
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");
        downloader.registerUrl(repoUrl, getClass().getResourceAsStream("../testData/testRepo.xml"));
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/dir/b"));
        zos.write("contents2".getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register the source provider
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages pkgs = mgr.getPackages();

        // Ensure the old local package was found with the right version
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        LocalPackage oldPkg = locals.get("dummy;bar");
        assertEquals(new Revision(3), oldPkg.getVersion());

        // Ensure the new package is found with the right version
        RemotePackage update = pkgs.getRemotePackages().get("dummy;bar");
        assertEquals(new Revision(4, 5, 6), update.getVersion());

        // Install the update
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Installer basicInstaller = new BasicInstallerFactory().createInstaller(update, mgr, fop);
        basicInstaller.prepareInstall(downloader, progress);
        basicInstaller.completeInstall(progress);

        // Reload the repo
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        // Ensure the files are still there
        File[] contents = fop.listFiles(new File(root, "dummy"));
        assertEquals(2, contents.length);
        assertEquals(new File(root, "dummy/bar"), contents[0]);
        assertEquals(new File(root, "dummy/foo"), contents[1]);

        // Ensure the packages are still there
        locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        assertTrue(locals.containsKey("dummy;bar"));
        LocalPackage newPkg = locals.get("dummy;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals("license text 2", newPkg.getLicense().getValue().trim());

        // Ensure the update was installed
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());

        // TODO: verify the actual contents of the update?
    }

    public void testInstallInChild() throws Exception {
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

        assertFalse(new BasicInstallerFactory().createInstaller(remote, mgr, fop)
                .prepareInstall(downloader, progress));
        boolean found = false;
        for (String warning : progress.getWarnings()) {
            if (warning.contains("child")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    public void testInstallInParent() throws Exception {
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

        assertFalse(new BasicInstallerFactory().createInstaller(remote, mgr, fop)
                .prepareInstall(downloader, progress));
        boolean found = false;
        for (String warning : progress.getWarnings()) {
            if (warning.contains("parent")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    public void testExistingDownload() throws Exception {
        MockFileOp fop = new MockFileOp();
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop) {
            @Override
            public void downloadFully(@NonNull URL url, @NonNull File target,
                    @NonNull ProgressIndicator indicator) throws IOException {
                super.downloadFully(url, target, indicator);
                throw new IOException("expected");
            }
        };
        URL repoUrl = new URL("http://example.com/dummy.xml");

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        ZipEntry e = new ZipEntry("top-level/a");
        e.setTime(123);
        zos.putNextEntry(e);
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.close();
        byte[] zipBytes = baos.toByteArray();
        ByteArrayInputStream is = new ByteArrayInputStream(zipBytes);
        downloader.registerUrl(archiveUrl, is);

        String repo = "<repo:repository\n" +
                      "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n" +
                      "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                      "    <remotePackage path=\"dummy;bar\">\n" +
                      "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n" +
                      "        <revision>\n" +
                      "            <major>4</major>\n" +
                      "            <minor>5</minor>\n" +
                      "            <micro>6</micro>\n" +
                      "        </revision>\n" +
                      "        <display-name>Test package 2</display-name>\n" +
                      "        <archives>\n" +
                      "            <archive>\n" +
                      "                <complete>\n" +
                      "                    <size>2345</size>\n" +
                      "                    <checksum>" +
                      BasicInstaller.hashFile(
                              new ByteArrayInputStream(zipBytes), zipBytes.length,
                              new FakeProgressIndicator()) +
                      "</checksum>\n" +
                      "                    <url>http://example.com/2/arch1</url>\n" +
                      "                </complete>\n" +
                      "            </archive>\n" +
                      "        </archives>\n" +
                      "    </remotePackage>\n" +
                      "</repo:repository>";

        // The repo we're going to download
        downloader.registerUrl(repoUrl, repo.getBytes());

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
          ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
          ImmutableList.<RepoManager.RepoLoadedCallback>of(),
          ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
          runner, downloader, new FakeSettingsController(false), true);

        // Install one of the packages.
        RemotePackage p = mgr.getPackages().getRemotePackages().get("dummy;bar");
        Installer basicInstaller = new BasicInstallerFactory().createInstaller(p, mgr, fop);
        FakeProgressIndicator firstInstallProgress = new FakeProgressIndicator();
        boolean result = basicInstaller
                .prepareInstall(downloader, firstInstallProgress);

        // be sure it was actually cancelled
        assertFalse(result);
        assertTrue(!firstInstallProgress.getWarnings().isEmpty());
        Downloader failingDownloader = new Downloader() {
            @Nullable
            @Override
            public InputStream downloadAndStream(@NonNull URL url,
                    @NonNull ProgressIndicator indicator) throws IOException {
                fail();
                return null;
            }

            @Nullable
            @Override
            public File downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
                    throws IOException {
                fail();
                return null;
            }

            @Override
            public void downloadFully(@NonNull URL url, @NonNull File target,
                    @NonNull ProgressIndicator indicator) throws IOException {
                fail();
            }
        };

        // Try again with the failing downloader; it should not be called.
        FakeProgressIndicator secondInstallProgress = new FakeProgressIndicator();
        result = basicInstaller.prepareInstall(failingDownloader, secondInstallProgress);
        assertTrue(result);
        result = basicInstaller.completeInstall(secondInstallProgress);

        assertTrue(result);
        secondInstallProgress.assertNoErrorsOrWarnings();

        // Reload the packages.
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
          ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
          runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        File[] contents = fop.listFiles(new File(root, "dummy"));

        // Ensure it was installed on the filesystem
        assertEquals(new File(root, "dummy/bar"), contents[0]);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("dummy;bar"));
        LocalPackage newPkg = locals.get("dummy;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());
    }
}
