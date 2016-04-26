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
package com.android.repository.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.testframework.MockFileOp;

import org.junit.Test;

import java.io.File;

/**
 * Tests for {@link License}
 */
public class LicenseTest {

    @Test
    public void testAccept() {
        MockFileOp fop = new MockFileOp();
        CommonFactory factory = (CommonFactory) RepoManager.getCommonModule().createLatestFactory();
        License l = factory.createLicenseType("my license", "lic1");
        License l2 = factory.createLicenseType("my license 2", "lic2");
        File root = new File("/sdk");
        assertFalse(l.checkAccepted(root, fop));
        assertFalse(l2.checkAccepted(root, fop));
        l.setAccepted(root, fop);
        assertTrue(l.checkAccepted(root, fop));
        assertFalse(l2.checkAccepted(root, fop));
    }

    @Test
    public void testMultiSameIdAccept() {
        MockFileOp fop = new MockFileOp();
        CommonFactory factory = (CommonFactory) RepoManager.getCommonModule().createLatestFactory();
        License l = factory.createLicenseType("my license", "lic1");
        License l2 = factory.createLicenseType("my license 2", "lic1");
        File root = new File("/sdk");
        assertFalse(l.checkAccepted(root, fop));
        assertFalse(l2.checkAccepted(root, fop));
        l.setAccepted(root, fop);
        assertTrue(l.checkAccepted(root, fop));
        assertFalse(l2.checkAccepted(root, fop));
        l2.setAccepted(root, fop);
        assertTrue(l.checkAccepted(root, fop));
        assertTrue(l2.checkAccepted(root, fop));
    }

    /**
     * Since we tell users the files control the license acceptance, make sure they work.
     */
    @Test
    public void testLicenseFile() {
        MockFileOp fop = new MockFileOp();
        CommonFactory factory = (CommonFactory) RepoManager.getCommonModule().createLatestFactory();
        License lic1 = factory.createLicenseType("my license", "lic1");
        License lic1a = factory.createLicenseType("my license rev 2", "lic1");
        License lic2 = factory.createLicenseType("my license 2", "lic2");
        File root = new File("/sdk");
        lic1.setAccepted(root, fop);
        lic1a.setAccepted(root, fop);
        lic2.setAccepted(root, fop);
        File licenseDir = new File(root, License.LICENSE_DIR);
        File[] licenseFiles = fop.listFiles(licenseDir);
        assertEquals(2, licenseFiles.length);
        File lic1File = new File(licenseDir, "lic1");
        byte[] lic1FileContent = fop.getContent(lic1File);
        fop.delete(lic1File);
        assertFalse(lic1.checkAccepted(root, fop));
        assertFalse(lic1a.checkAccepted(root, fop));
        assertTrue(lic2.checkAccepted(root, fop));

        fop = new MockFileOp();
        assertFalse(lic1.checkAccepted(root, fop));
        fop.recordExistingFile(lic1File.getPath(), lic1FileContent);
        assertTrue(lic1.checkAccepted(root, fop));
        assertTrue(lic1a.checkAccepted(root, fop));
    }
}
