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

import com.android.SdkConstants;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

/**
 * Tests parsing and rewriting legacy local packages.
 */
public class LegacyLocalRepoTest extends TestCase {

    public void testParseLegacy() throws URISyntaxException, FileNotFoundException {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFolder("/sdk/tools");
        mockFop.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                        "Archive.Os=WINDOWS\n" +
                        "Pkg.Revision=22.3.4\n" +
                        "Platform.MinPlatformToolsRev=18\n" +
                        "Pkg.LicenseRef=android-sdk-license\n" +
                        "Archive.Arch=ANY\n" +
                        "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mockFop.recordExistingFile("/sdk/tools/" + SdkConstants.androidCmdName(), "placeholder");
        mockFop.recordExistingFile("/sdk/tools/" + SdkConstants.FN_EMULATOR, "placeholder");

        File root = new File("/sdk");
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepoManager mgr = AndroidSdkHandler.getInstance().getSdkManager(progress);
        progress.assertNoErrorsOrWarnings();
        mgr.setLocalPath(root);

        LocalRepoLoader sdk = new LocalRepoLoader(root, mgr,
                new LegacyLocalRepoLoader(root, mockFop, mgr), mockFop);
        Map<String, LocalPackage> packages = sdk.getPackages(progress);
        progress.assertNoErrorsOrWarnings();
        assertEquals(1, packages.size());
        LocalPackage local = packages.get("tools");
        assertTrue(local.getTypeDetails() instanceof DetailsTypes.ToolDetailsType);
        assertEquals("Terms and Conditions", local.getLicense().getValue());
        assertEquals(new Revision(22, 3, 4), local.getVersion());
    }

    public void testRewriteLegacy() throws URISyntaxException, FileNotFoundException {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFolder("/sdk/tools");
        mockFop.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                        "Archive.Os=WINDOWS\n" +
                        "Pkg.Revision=22.3\n" +
                        "Platform.MinPlatformToolsRev=18\n" +
                        "Pkg.LicenseRef=android-sdk-license\n" +
                        "Archive.Arch=ANY\n" +
                        "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mockFop.recordExistingFile("/sdk/tools/" + SdkConstants.androidCmdName(), "placeholder");
        mockFop.recordExistingFile("/sdk/tools/" + SdkConstants.FN_EMULATOR, "placeholder");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        File root = new File("/sdk");
        RepoManager mgr = new AndroidSdkHandler(mockFop).getSdkManager(progress);

        mgr.registerSchemaModule(AndroidSdkHandler.getInstance().getAddonModule(progress));
        mgr.registerSchemaModule(AndroidSdkHandler.getInstance().getRepositoryModule(progress));
        mgr.registerSchemaModule(AndroidSdkHandler.getInstance().getSysImgModule(progress));
        progress.assertNoErrorsOrWarnings();

        LocalRepoLoader sdk = new LocalRepoLoader(root, mgr,
                new LegacyLocalRepoLoader(root, mockFop, mgr), mockFop);
        // Cause the packages to be loaded. This will write out package.xml for the legacy package.
        sdk.getPackages(progress);
        progress.assertNoErrorsOrWarnings();

        Collection<SchemaModule> extensions = ImmutableList
                .of(AndroidSdkHandler.getInstance().getRepositoryModule(progress));

        // Now read the new package
        Repository repo = (Repository) SchemaModuleUtil.unmarshal(
                mockFop.newFileInputStream(new File("/sdk/tools/package.xml")),
                extensions,
                mgr.getResourceResolver(progress), progress);
        progress.assertNoErrorsOrWarnings();
        LocalPackage local = repo.getLocalPackage();
        local.setInstalledPath(mgr.getLocalPath());
        assertTrue(local.getTypeDetails() instanceof DetailsTypes.ToolDetailsType);
        assertEquals("Terms and Conditions", local.getLicense().getValue());
        int[] revision = local.getVersion().toIntArray(false);
        assertEquals(3, revision.length);
        assertEquals(22, revision[0]);
        assertEquals(3, revision[1]);
        assertEquals(0, revision[2]);
        assertTrue(local.getTypeDetails() instanceof DetailsTypes.ToolDetailsType);
    }

}
