/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository.sources;

import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.MockMonitor;
import com.android.sdklib.internal.repository.packages.Package;
import com.android.sdklib.internal.repository.packages.SystemImagePackage;
import com.android.sdklib.repository.SdkSysImgConstants;

import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.TestCase;

/**
 * Tests for {@link SdkSysImgSource}.
 */
public class SdkSysImgSourceTest extends TestCase {

    /**
     * An internal helper class to give us visibility to the protected members we want
     * to test.
     */
    private static class MockSdkSysImgSource extends SdkSysImgSource {
        public MockSdkSysImgSource() {
            super("fake-url", null /*uiName*/);
        }

        public Document _findAlternateToolsXml(InputStream xml) {
            return super.findAlternateToolsXml(xml);
        }

        public boolean _parsePackages(Document doc, String nsUri, ITaskMonitor monitor) {
            return super.parsePackages(doc, nsUri, monitor);
        }

        public int _getXmlSchemaVersion(InputStream xml) {
            return super.getXmlSchemaVersion(xml);
        }

        public String _validateXml(InputStream xml, String url, int version,
                                   String[] outError, Boolean[] validatorFound) {
            return super.validateXml(xml, url, version, outError, validatorFound);
        }

        public Document _getDocument(InputStream xml, ITaskMonitor monitor) {
            return super.getDocument(xml, monitor);
        }

    }

    private MockSdkSysImgSource mSource;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSource = new MockSdkSysImgSource();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mSource = null;
    }

    /**
     * Validate that findAlternateToolsXml doesn't work for this source even
     * when trying to load a valid xml. That's because finding alternate tools
     * is not supported by this kind of source.
     */
    public void testFindAlternateToolsXml_1() throws Exception {
        InputStream xmlStream =
            getTestResource("/com/android/sdklib/testdata/sys_img_sample_1.xml");

        Document result = mSource._findAlternateToolsXml(xmlStream);
        assertNull(result);
    }

    /**
     * Validate we can load a valid schema version 1
     */
    public void testLoadSysImgXml_1() throws Exception {
        InputStream xmlStream =
            getTestResource("/com/android/sdklib/testdata/sys_img_sample_1.xml");

        // guess the version from the XML document
        int version = mSource._getXmlSchemaVersion(xmlStream);
        assertEquals(1, version);

        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        String url = "not-a-valid-url://" + SdkSysImgConstants.URL_DEFAULT_FILENAME;

        String uri = mSource._validateXml(xmlStream, url, version, validationError, validatorFound);
        assertEquals(Boolean.TRUE, validatorFound[0]);
        assertEquals(null, validationError[0]);
        assertEquals(SdkSysImgConstants.getSchemaUri(1), uri);

        // Validation was successful, load the document
        MockMonitor monitor = new MockMonitor();
        Document doc = mSource._getDocument(xmlStream, monitor);
        assertNotNull(doc);

        // Get the packages
        assertTrue(mSource._parsePackages(doc, uri, monitor));

        assertEquals(
                "Found Intel x86 Atom System Image, Android API 2, revision 1\n" +
                "Found ARM EABI v7a System Image, Android API 2, revision 2\n" +
                "Found ARM EABI System Image, Android API 42, revision 12\n" +
                "Found MIPS System Image, Android API 42, revision 12\n",
                monitor.getCapturedVerboseLog());
        assertEquals("", monitor.getCapturedLog());
        assertEquals("", monitor.getCapturedErrorLog());

        // check the packages we found...
        // Note the order doesn't necessary match the one from the
        // assertEquald(getCapturedVerboseLog) because packages are sorted using the
        // Packages' sorting order, e.g. all platforms are sorted by descending API level, etc.

        Package[] pkgs = mSource.getPackages();

        assertEquals(4, pkgs.length);
        for (Package p : pkgs) {
            // We expected to find packages with each at least one archive.
            assertTrue(p.getArchives().length >= 1);
            // And only system images are supported by this source
            assertTrue(p instanceof SystemImagePackage);
        }

        // Check the system-image packages
        ArrayList<String> sysImgVersionAbi = new ArrayList<String>();
        for (Package p : pkgs) {
            if (p instanceof SystemImagePackage) {
                SystemImagePackage sip = (SystemImagePackage) p;
                String v = sip.getAndroidVersion().getApiString();
                String a = sip.getAbi();
                sysImgVersionAbi.add(String.format("%1$s %2$s", v, a)); //$NON-NLS-1$
            }
        }
        assertEquals(
                "[42 armeabi, " +
                 "42 mips, " +
                 "2 armeabi-v7a, " +
                 "2 x86]",
                Arrays.toString(sysImgVersionAbi.toArray()));

    }


    //-----

    /**
     * Returns an SdkLib file resource as a {@link ByteArrayInputStream},
     * which has the advantage that we can use {@link InputStream#reset()} on it
     * at any time to read it multiple times.
     * <p/>
     * The default for getResourceAsStream() is to return a {@link FileInputStream} that
     * does not support reset(), yet we need it in the tested code.
     *
     * @throws IOException if some I/O read fails
     */
    private ByteArrayInputStream getTestResource(String filename) throws IOException {
        InputStream xmlStream = this.getClass().getResourceAsStream(filename);

        try {
            byte[] data = new byte[8192];
            int offset = 0;
            int n;

            while ((n = xmlStream.read(data, offset, data.length - offset)) != -1) {
                offset += n;

                if (offset == data.length) {
                    byte[] newData = new byte[offset + 8192];
                    System.arraycopy(data, 0, newData, 0, offset);
                    data = newData;
                }
            }

            return new ByteArrayInputStream(data, 0, offset);
        } finally {
            if (xmlStream != null) {
                xmlStream.close();
            }
        }
    }
}
