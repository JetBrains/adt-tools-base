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

package com.android.sdklib.repository.installer;

import static org.junit.Assert.assertArrayEquals;

import com.android.repository.Revision;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoManager.RepoLoadedCallback;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeInstallListenerFactory;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for {@link MavenInstallListener}
 */
public class MavenInstallListenerTest extends TestCase {

    private static final String POM_1_2_3_CONTENTS =
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                    + "  <modelVersion>4.0.0</modelVersion>\n"
                    + "  <groupId>com.android.group1</groupId>\n"
                    + "  <artifactId>artifact1</artifactId>\n"
                    + "  <version>1.2.3</version>\n"
                    + "  <packaging>pom</packaging>\n"
                    + "  <name>test package 1 version 1.2.3</name>\n"
                    + "</project>";

    private static final String POM_1_0_0_CONTENTS =
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                    + "  <modelVersion>4.0.0</modelVersion>\n"
                    + "  <groupId>com.android.group1</groupId>\n"
                    + "  <artifactId>artifact1</artifactId>\n"
                    + "  <version>1.0.0</version>\n"
                    + "  <packaging>pom</packaging>\n"
                    + "  <name>test package 1 version 1.2.3</name>\n"
                    + "</project>";

    public void testInstallFirst() throws Exception {
        File root = new File("/repo");
        MockFileOp fop = new MockFileOp();
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl,
                getClass().getResourceAsStream("testdata/remote_maven_repo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/artifact1-1.2.3.pom"));
        zos.write(POM_1_2_3_CONTENTS.getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(AndroidSdkHandler.getAddonModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, ImmutableList.<RepoLoadedCallback>of(),
                ImmutableList.<RepoLoadedCallback>of(), ImmutableList.<Runnable>of(), runner,
                downloader, new FakeSettingsController(false), true);

        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        RepositoryPackages pkgs = mgr.getPackages();

        RemotePackage p = pkgs.getRemotePackages()
                .get("m2repository;com;android;group1;artifact1;1.2.3");
        // Install
        BasicInstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(
                new MavenInstallListener(new AndroidSdkHandler(root, fop))));
        Installer installer = factory.createInstaller(p, mgr, downloader, fop);
        installer.prepare(runner.getProgressIndicator());
        installer.complete(runner.getProgressIndicator());
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        File artifactRoot = new File(root, "m2repository/com/android/group1/artifact1");
        File mavenMetadata = new File(artifactRoot, "maven-metadata.xml");
        MavenInstallListener.MavenMetadata metadata = MavenInstallListener.unmarshal(mavenMetadata,
                MavenInstallListener.MavenMetadata.class, runner.getProgressIndicator(), fop);

        assertEquals("artifact1", metadata.artifactId);
        assertEquals("com.android.group1", metadata.groupId);
        assertEquals("1.2.3", metadata.versioning.release);
        assertEquals(ImmutableList.of("1.2.3"), metadata.versioning.versions.version);

        File[] contents = fop
                .listFiles(new File(root, "m2repository/com/android/group1/artifact1/1.2.3"));

        // Ensure it was installed on the filesystem
        assertArrayEquals(new File[]{
                        new File(root, "m2repository/com/android/group1/artifact1/1.2.3/a"),
                        new File(root, "m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom"),
                        new File(root, "m2repository/com/android/group1/artifact1/1.2.3/package.xml")},
                contents);

        // Reload
        mgr.load(0, ImmutableList.<RepoLoadedCallback>of(), ImmutableList.<RepoLoadedCallback>of(),
                ImmutableList.<Runnable>of(), runner, downloader, new FakeSettingsController(false),
                true);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("m2repository;com;android;group1;artifact1;1.2.3"));
        LocalPackage newPkg = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertEquals("maven package", newPkg.getDisplayName());
        assertEquals(new Revision(3), newPkg.getVersion());

    }

    public void testInstallAdditional() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile("/repo/m2repository/com/android/group1/artifact1/maven-metadata.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<metadata>\n"
                        + "  <groupId>com.android.group1</groupId>\n"
                        + "  <artifactId>artifact1</artifactId>\n"
                        + "  <release>1.0.0</release>\n"
                        + "  <versioning>\n"
                        + "    <versions>\n"
                        + "      <version>1.0.0</version>\n"
                        + "    </versions>\n"
                        + "    <lastUpdated>20151006162600</lastUpdated>\n"
                        + "  </versioning>\n"
                        + "</metadata>\n");
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.0.0/artifact1-1.0.0.pom",
                POM_1_0_0_CONTENTS);
        File root = new File("/repo");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl,
                getClass().getResourceAsStream("testdata/remote_maven_repo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/artifact1-1.2.3.pom"));
        zos.write(POM_1_2_3_CONTENTS.getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(AndroidSdkHandler.getAddonModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.<RepoLoadedCallback>of(), ImmutableList.<RepoLoadedCallback>of(),
                ImmutableList.<Runnable>of(), runner,
                downloader, new FakeSettingsController(false), true);

        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        RepositoryPackages pkgs = mgr.getPackages();

        RemotePackage remotePackage = pkgs.getRemotePackages()
                .get("m2repository;com;android;group1;artifact1;1.2.3");

        // Install
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(
                new MavenInstallListener(new AndroidSdkHandler(root, fop))));
        Installer installer = factory.createInstaller(remotePackage, mgr, downloader, fop);
        installer.prepare(runner.getProgressIndicator());
        installer.complete(runner.getProgressIndicator());
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        File artifactRoot = new File(root, "m2repository/com/android/group1/artifact1");
        File mavenMetadata = new File(artifactRoot, "maven-metadata.xml");
        MavenInstallListener.MavenMetadata metadata = MavenInstallListener
                .unmarshal(mavenMetadata, MavenInstallListener.MavenMetadata.class,
                        runner.getProgressIndicator(), fop);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        assertEquals("artifact1", metadata.artifactId);
        assertEquals("com.android.group1", metadata.groupId);
        assertEquals("1.2.3", metadata.versioning.release);
        assertEquals(ImmutableList.of("1.0.0", "1.2.3"), metadata.versioning.versions.version);

        File[] contents = fop
                .listFiles(new File(root, "m2repository/com/android/group1/artifact1/1.2.3"));

        // Ensure it was installed on the filesystem
        assertArrayEquals(new File[]{
                        new File(root, "m2repository/com/android/group1/artifact1/1.2.3/a"),
                        new File(root, "m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom"),
                        new File(root, "m2repository/com/android/group1/artifact1/1.2.3/package.xml")},
                contents);
        // Reload
        mgr.load(0, ImmutableList.<RepoLoadedCallback>of(), ImmutableList.<RepoLoadedCallback>of(),
                ImmutableList.<Runnable>of(), runner, downloader, new FakeSettingsController(false),
                true);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("m2repository;com;android;group1;artifact1;1.2.3"));
        LocalPackage newPkg = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertEquals("maven package", newPkg.getDisplayName());
        assertEquals(new Revision(3), newPkg.getVersion());
    }

    public void testRemove() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.2.3/package.xml",
                "<repo:sdk-addon\n"
                        + "        xmlns:repo=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"m2repository;com;android;group1;artifact1;1.2.3\">\n"
                        + "        <type-details xsi:type=\"repo:extraDetailsType\">\n"
                        + "            <vendor>\n"
                        + "                <id>cyclop</id>\n"
                        + "                <display>The big bus</display>\n"
                        + "            </vendor>\n"
                        + "        </type-details>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>A Maven artifact</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:sdk-addon>"
        );
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom",
                POM_1_2_3_CONTENTS);
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.0.0/package.xml",
                "<repo:sdk-addon\n"
                        + "        xmlns:repo=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"m2repository;com;android;group1;artifact1;1.0.0\">\n"
                        + "        <type-details xsi:type=\"repo:extraDetailsType\">\n"
                        + "            <vendor>\n"
                        + "                <id>cyclop</id>\n"
                        + "                <display>The big bus</display>\n"
                        + "            </vendor>\n"
                        + "        </type-details>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>Another Maven artifact</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:sdk-addon>"
        );
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.0.0/artifact1-1.0.0.pom",
                POM_1_0_0_CONTENTS);

        String metadataPath
                = "/repo/m2repository/com/android/group1/artifact1/maven-metadata.xml";
        fop.recordExistingFile(
                metadataPath,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<metadata>\n"
                        + "  <groupId>com.android.group1</groupId>\n"
                        + "  <artifactId>artifact1</artifactId>\n"
                        + "  <release>1.2.3</release>\n"
                        + "  <versioning>\n"
                        + "    <versions>\n"
                        + "      <version>1.2.3</version>\n"
                        + "      <version>1.0.0</version>\n"
                        + "    </versions>\n"
                        + "    <lastUpdated>20151006162600</lastUpdated>\n"
                        + "  </versioning>\n"
                        + "</metadata>\n");

        File root = new File("/repo");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(root);
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());

        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(fop);
        // Reload
        mgr.load(0, ImmutableList.<RepoLoadedCallback>of(), ImmutableList.<RepoLoadedCallback>of(),
                ImmutableList.<Runnable>of(), runner, downloader, new FakeSettingsController(false),
                true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        LocalPackage p = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertNotNull(p);
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(
                new MavenInstallListener(new AndroidSdkHandler(root, fop))));
        Uninstaller uninstaller = factory.createUninstaller(p, mgr, fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        uninstaller.prepare(progress);
        uninstaller.complete(progress);
        progress.assertNoErrorsOrWarnings();
        MavenInstallListener.MavenMetadata metadata = MavenInstallListener
                .unmarshal(new File(metadataPath), MavenInstallListener.MavenMetadata.class,
                        progress, fop);
        progress.assertNoErrorsOrWarnings();
        assertNotNull(metadata);
        assertEquals(ImmutableList.of("1.0.0"), metadata.versioning.versions.version);
        assertEquals("1.0.0", metadata.versioning.release);
    }

    public void testRemoveAll() throws Exception {
        MockFileOp fop = new MockFileOp();
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.2.3/package.xml",
                "<repo:sdk-addon\n"
                        + "        xmlns:repo=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"m2repository;com;android;group1;artifact1;1.2.3\">\n"
                        + "        <type-details xsi:type=\"repo:extraDetailsType\">\n"
                        + "            <vendor>\n"
                        + "                <id>cyclop</id>\n"
                        + "                <display>The big bus</display>\n"
                        + "            </vendor>\n"
                        + "        </type-details>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>A Maven artifact</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:sdk-addon>"
        );
        fop.recordExistingFile(
                "/repo/m2repository/com/android/group1/artifact1/1.2.3/artifact1-1.2.3.pom",
                POM_1_2_3_CONTENTS);

        String metadataPath
                = "/repo/m2repository/com/android/group1/artifact1/maven-metadata.xml";
        fop.recordExistingFile(
                metadataPath,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<metadata>\n"
                        + "  <groupId>com.android.group1</groupId>\n"
                        + "  <artifactId>artifact1</artifactId>\n"
                        + "  <release>1.2.3</release>\n"
                        + "  <versioning>\n"
                        + "    <versions>\n"
                        + "      <version>1.2.3</version>\n"
                        + "    </versions>\n"
                        + "    <lastUpdated>20151006162600</lastUpdated>\n"
                        + "  </versioning>\n"
                        + "</metadata>\n");

        File root = new File("/repo");
        RepoManager mgr = new RepoManagerImpl(fop);
        mgr.setLocalPath(root);
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getAddonModule());

        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(fop);
        // Reload
        mgr.load(0, ImmutableList.<RepoLoadedCallback>of(), ImmutableList.<RepoLoadedCallback>of(),
                ImmutableList.<Runnable>of(), runner, downloader, new FakeSettingsController(false),
                true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        LocalPackage p = locals.get("m2repository;com;android;group1;artifact1;1.2.3");
        assertNotNull(p);
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new FakeInstallListenerFactory(
                new MavenInstallListener(new AndroidSdkHandler(root, fop))));
        Uninstaller uninstaller = factory.createUninstaller(p, mgr, fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        uninstaller.prepare(progress);
        uninstaller.complete(progress);
        progress.assertNoErrorsOrWarnings();
        assertFalse(fop.exists(new File(metadataPath)));
    }

}
