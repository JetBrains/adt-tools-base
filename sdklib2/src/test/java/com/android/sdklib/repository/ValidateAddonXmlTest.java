/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdklib.repository;

import com.android.annotations.Nullable;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.InputStream;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import junit.framework.TestCase;

/**
 * Tests local validation of an SDK Addon sample XMLs using an XML Schema validator.
 */
public class ValidateAddonXmlTest extends TestCase {

    private static String OPEN_TAG_ADDON =
        "<r:sdk-addon xmlns:r=\"http://schemas.android.com/sdk/android/addon/" +
        Integer.toString(SdkAddonConstants.NS_LATEST_VERSION) +
        "\">";
    private static String CLOSE_TAG_ADDON = "</r:sdk-addon>";

    // --- Helpers ------------

    /**
     * Helper method that returns a validator for our Addon XSD
     *
     * @param version The version number, in range {@code 1..NS_LATEST_VERSION}
     * @param handler A {@link CaptureErrorHandler}. If null the default will be used,
     *   which will most likely print errors to stderr.
     */
    private Validator getAddonValidator(int version, @Nullable CaptureErrorHandler handler)
            throws SAXException {
        Validator validator = null;
        InputStream xsdStream = SdkAddonConstants.getXsdStream(version);
        if (xsdStream != null) {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(xsdStream));
            validator = schema.newValidator();
            if (handler != null) {
                validator.setErrorHandler(handler);
            }
        }

        return validator;
    }

    /** An helper that validates a string against an expected regexp. */
    private void assertRegex(String expectedRegexp, String actualString) {
        assertNotNull(actualString);
        assertTrue(
                String.format("Regexp Assertion Failed:\nExpected: %s\nActual: %s\n",
                        expectedRegexp, actualString),
                actualString.matches(expectedRegexp));
    }

    // --- Tests ------------

    /** Validates that NS_LATEST_VERSION points to the max available XSD schema. */
    public void testAddonLatestVersionNumber() throws Exception {
        CaptureErrorHandler handler = new CaptureErrorHandler();

        // There should be a schema matching NS_LATEST_VERSION
        assertNotNull(getAddonValidator(SdkAddonConstants.NS_LATEST_VERSION, handler));

        // There should NOT be a schema with NS_LATEST_VERSION+1
        assertNull(
                String.format(
                        "There's an ADDON XSD at version %d but SdkAddonConstants.NS_LATEST_VERSION is still set to %d.",
                        SdkAddonConstants.NS_LATEST_VERSION + 1,
                        SdkAddonConstants.NS_LATEST_VERSION),
                getAddonValidator(SdkAddonConstants.NS_LATEST_VERSION + 1, handler));
    }

    /** Validate a valid sample using namespace version 1 using an InputStream */
    public void testValidateLocalAddonFile1() throws Exception {
        InputStream xmlStream = this.getClass().getResourceAsStream(
                    "/com/android/sdklib/testdata/addon_sample_1.xml");
        Source source = new StreamSource(xmlStream);

        CaptureErrorHandler handler = new CaptureErrorHandler();
        Validator validator = getAddonValidator(1, handler);
        validator.validate(source);
        handler.verify();
    }

    /** Validate a valid sample using namespace version 2 using an InputStream */
    public void testValidateLocalAddonFile2() throws Exception {
        InputStream xmlStream = this.getClass().getResourceAsStream(
                    "/com/android/sdklib/testdata/addon_sample_2.xml");
        Source source = new StreamSource(xmlStream);

        CaptureErrorHandler handler = new CaptureErrorHandler();
        Validator validator = getAddonValidator(2, handler);
        validator.validate(source);
        handler.verify();
    }

    /** Validate a valid sample using namespace version 3 using an InputStream */
    public void testValidateLocalAddonFile3() throws Exception {
        InputStream xmlStream = this.getClass().getResourceAsStream(
                    "/com/android/sdklib/testdata/addon_sample_3.xml");
        Source source = new StreamSource(xmlStream);

        CaptureErrorHandler handler = new CaptureErrorHandler();
        Validator validator = getAddonValidator(3, handler);
        validator.validate(source);
        handler.verify();
    }

    /** Validate a valid sample using namespace version 4 using an InputStream */
    public void testValidateLocalAddonFile4() throws Exception {
        InputStream xmlStream = this.getClass().getResourceAsStream(
                    "/com/android/sdklib/testdata/addon_sample_4.xml");
        Source source = new StreamSource(xmlStream);

        CaptureErrorHandler handler = new CaptureErrorHandler();
        Validator validator = getAddonValidator(4, handler);
        validator.validate(source);
        handler.verify();
    }

    // IMPORTANT: each time you add a test here, you should add a corresponding
    // test in SdkAddonSourceTest to validate the XML content is parsed correctly.

    // ----

    /**
     * An addon does not support a codename.
     * There used to be a typo in the repository.XSD versions 1-2 & the addon XSD versions 1-2
     * where addons had an optional element 'codename'. This was a typo and it's been fixed.
     */
    public void testAddonCodename() throws Exception {
        // we define a license named "lic1" and then reference "lic2" instead
        String document = "<?xml version=\"1.0\"?>" +
            OPEN_TAG_ADDON +
            "<r:license id=\"lic1\"> some license </r:license> " +
            "<r:add-on> <r:uses-license ref=\"lic1\" /> <r:revision>1</r:revision> " +
            "<r:name-id>AddonName</r:name-id> <r:name-display>The Addon Name</r:name-display> " +
            "<r:vendor-id>AddonVendor</r:vendor-id> <r:vendor-display>The Addon Vendor</r:vendor-display> " +
            "<r:api-level>42</r:api-level> " +
            "<r:codename>Addons do not support codenames</r:codenames> " +
            "<r:libs><r:lib><r:name>com.example.LibName</r:name></r:lib></r:libs> " +
            "<r:archives> <r:archive os=\"any\"> <r:size>1</r:size> <r:checksum>2822ae37115ebf13412bbef91339ee0d9454525e</r:checksum> " +
            "<r:url>url</r:url> </r:archive> </r:archives> </r:add-on>" +
            CLOSE_TAG_ADDON;

        Source source = new StreamSource(new StringReader(document));

        // don't capture the validator errors, we want it to fail and catch the exception
        Validator validator = getAddonValidator(SdkAddonConstants.NS_LATEST_VERSION, null);
        try {
            validator.validate(source);
        } catch (SAXParseException e) {
            // We expect a parse error referring to this grammar rule
            assertRegex("cvc-complex-type.2.4.a: Invalid content was found starting with element 'r:codename'.*",
                    e.getMessage());
            return;
        }
        // If we get here, the validator has not failed as we expected it to.
        fail();
    }

    /** A document with a slash in an extra path. */
    public void testExtraPathWithSlash() throws Exception {
        String document = "<?xml version=\"1.0\"?>" +
            OPEN_TAG_ADDON +
            "<r:extra> <r:revision>1</r:revision> <r:path>path/cannot\\contain\\segments</r:path> " +
            "<r:archives> <r:archive os=\"any\"> <r:size>1</r:size> <r:checksum>2822ae37115ebf13412bbef91339ee0d9454525e</r:checksum> " +
            "<r:url>url</r:url> </r:archive> </r:archives> </r:extra>" +
            CLOSE_TAG_ADDON;

        Source source = new StreamSource(new StringReader(document));

        // don't capture the validator errors, we want it to fail and catch the exception
        Validator validator = getAddonValidator(SdkAddonConstants.NS_LATEST_VERSION, null);
        try {
            validator.validate(source);
        } catch (SAXParseException e) {
            // We expect a parse error referring to this grammar rule
            assertRegex("cvc-pattern-valid: Value 'path/cannot\\\\contain\\\\segments' is not facet-valid with respect to pattern.*",
                    e.getMessage());
            return;
        }
        // If we get here, the validator has not failed as we expected it to.
        fail();
    }
}
