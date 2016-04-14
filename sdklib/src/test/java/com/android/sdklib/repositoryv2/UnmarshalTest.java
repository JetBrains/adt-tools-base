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
package com.android.sdklib.repositoryv2;

import com.android.repository.Revision;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Tests for unmarshalling an xml repository
 */
public class UnmarshalTest extends TestCase {

    public void testLoadRepo() throws Exception {
        String filename = "testdata/repository2_sample_1.xml";
        InputStream xmlStream = getClass().getResourceAsStream(filename);
        assertNotNull("Missing test file: " + filename, xmlStream);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File(filename), new MockFileOp());
        SchemaModule repoEx = AndroidSdkHandler.getRepositoryModule();
        SchemaModule addonEx = AndroidSdkHandler.getAddonModule();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepoManager mgr = handler.getSdkManager(progress);
        Repository repo = (Repository) SchemaModuleUtil.unmarshal(xmlStream,
                ImmutableList.of(repoEx, addonEx, RepoManager.getGenericModule()),
                mgr.getResourceResolver(progress), true, progress);
        progress.assertNoErrorsOrWarnings();
        List<? extends License> licenses = repo.getLicense();
        assertEquals(licenses.size(), 2);
        Map<String, String> licenseMap = Maps.newHashMap();
        for (License license : licenses) {
            licenseMap.put(license.getId(), license.getValue());
        }
        assertEquals(licenseMap.get("license1").trim(),
                "This is the license for this platform.");
        assertEquals(licenseMap.get("license2").trim(),
                "Licenses are only of type 'text' right now, so this is implied.");

        List<? extends RemotePackage> packages = repo.getRemotePackage();
        assertEquals(3, packages.size());
        Map<String, RemotePackage> packageMap = Maps.newHashMap();
        for (RemotePackage p : packages) {
            packageMap.put(p.getPath(), p);
        }

        RemotePackage platform22 = packageMap.get("platforms;android-22");
        assertEquals(platform22.getDisplayName(), "Lollipop MR1");

        assertTrue(platform22.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType);
        DetailsTypes.PlatformDetailsType details = (DetailsTypes.PlatformDetailsType) platform22
                .getTypeDetails();
        assertEquals(1, details.getApiLevel());
        assertEquals(5, details.getLayoutlib().getApi());

        List<Archive> archives = ((RemotePackageImpl) platform22).getAllArchives();
        assertEquals(2, archives.size());
        Archive archive = archives.get(1);
        assertEquals(64, archive.getHostBits().intValue());
        assertEquals("windows", archive.getHostOs());
        Archive.PatchType patch = archive.getAllPatches().get(0);
        assertEquals(new Revision(1), patch.getBasedOn().toRevision());
        assertEquals(4321, patch.getSize());
        assertEquals("something", patch.getUrl());
        Archive.CompleteType complete = archive.getComplete();
        assertEquals(65536, complete.getSize());
        assertEquals("1234ae37115ebf13412bbef91339ee0d9454525e", complete.getChecksum());

        // TODO: add other extension types as below
/*
        filename = "/com/android/sdklib/testdata/addon2_sample_1.xml";
        xmlStream = getClass().getResourceAsStream(filename);
        repo = SchemaModule.unmarshal(xmlStream, ImmutableList.of(repoEx, addonEx));
        assertTrue(repo.getPackage().get(0).getTypeDetails() instanceof DetailsTypes.AddonDetailsType);*/
    }

    private static final String INVALID_XML =
            "<repo:repository\n"
                    + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                    + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                    + "    <localPackage path=\"dummy;foo\" obsolete=\"true\">\n"
                    + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                    + "        <revision>\n"
                    + "            <major>1</major>\n"
                    + "            <minor>2</minor>\n"
                    + "            <micro>3</micro>\n"
                    + "        </revision>\n"
                    + "        <foo bar=\"baz\"/>"
                    + "        <display-name>Test package</display-name>\n"
                    + "    </localPackage>\n"
                    + "</repo:repository>";

    public void testLeniency() throws Exception {
        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), new MockFileOp());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepoManager mgr = handler.getSdkManager(progress);
        Repository repo = (Repository) SchemaModuleUtil
                .unmarshal(new ByteArrayInputStream(INVALID_XML.getBytes()),
                        ImmutableList.of(RepoManager.getGenericModule()),
                        mgr.getResourceResolver(progress), false, progress);
        assertFalse(progress.getWarnings().isEmpty());
        LocalPackage local = repo.getLocalPackage();
        assertEquals("dummy;foo", local.getPath());
        assertEquals(new Revision(1, 2, 3), local.getVersion());

        try {
            SchemaModuleUtil.unmarshal(new ByteArrayInputStream(INVALID_XML.getBytes()),
                    ImmutableList.of(RepoManager.getGenericModule()),
                    mgr.getResourceResolver(progress), true, progress);
            fail();
        }
        catch (Exception e) {
            // expected
        }
    }

    private static final String FUTURE_XML =
            "<repo:repository\n"
                    + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/99\"\n"
                    + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                    + "    <localPackage path=\"dummy;foo\" obsolete=\"true\">\n"
                    + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                    + "        <revision>\n"
                    + "            <major>1</major>\n"
                    + "            <minor>2</minor>\n"
                    + "            <micro>3</micro>\n"
                    + "        </revision>\n"
                    + "        <display-name>Test package</display-name>\n"
                    + "    </localPackage>\n"
                    + "</repo:repository>";

    public void testNamespaceFallback() throws Exception {
        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), new MockFileOp());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepoManager mgr = handler.getSdkManager(progress);
        Repository repo = (Repository) SchemaModuleUtil
                .unmarshal(new ByteArrayInputStream(FUTURE_XML.getBytes()),
                        ImmutableList
                                .of(RepoManager.getGenericModule(), RepoManager.getCommonModule()),
                        mgr.getResourceResolver(progress), false, progress);
        assertFalse(progress.getWarnings().isEmpty());
        LocalPackage local = repo.getLocalPackage();
        assertEquals("dummy;foo", local.getPath());
        assertEquals(new Revision(1, 2, 3), local.getVersion());

        try {
            SchemaModuleUtil.unmarshal(new ByteArrayInputStream(FUTURE_XML.getBytes()),
                    ImmutableList.of(RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                    mgr.getResourceResolver(progress), true, progress);
            fail();
        }
        catch (Exception e) {
            // expected
        }
    }

}
