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

package com.android.dvlib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class DeviceSchema {

    public static final String NS_DEVICES_XSD = "http://schemas.android.com/sdk/devices/1";

    /**
     * The "devices" element is the root element of this schema.
     *
     * It must contain one or more "device" elements that each define the
     * hardware, software, and states for a given device.
     */
    public static final String NODE_DEVICES = "devices";

    /**
     * A "device" element contains a "hardware" element, a "software" element
     * for each API version it supports, and a "state" element for each possible
     * state the device could be in.
     */
    public static final String NODE_DEVICE = "device";

    /**
     * The "hardware" element contains all of the hardware information for a
     * given device.
     */
    public static final String NODE_HARDWARE = "hardware";

    /**
     * The "software" element contains all of the software information for an
     * API version of the given device.
     */
    public static final String NODE_SOFTWARE = "software";

    /**
     * The "state" element contains all of the parameters for a given state of
     * the device. It's also capable of redefining hardware configurations if
     * they change based on state.
     */
    public static final String NODE_STATE = "state";

    public static final String NODE_KEYBOARD = "keyboard";

    public static final String NODE_TOUCH = "touch";

    public static final String NODE_GL_EXTENSIONS = "gl-extensions";

    public static final String NODE_GL_VERSION = "gl-version";

    public static final String NODE_NETWORKING = "networking";

    public static final String NODE_REMOVABLE_STORAGE = "removable-storage";

    public static final String NODE_FLASH = "flash";

    public static final String NODE_LIVE_WALLPAPER_SUPPORT = "live-wallpaper-support";

    public static final String NODE_STATUS_BAR = "status-bar";

    public static final String NODE_BUTTONS = "buttons";

    public static final String NODE_CAMERA = "camera";

    public static final String NODE_LOCATION = "location";

    public static final String NODE_GPU = "gpu";

    public static final String NODE_DOCK = "dock";

    public static final String NODE_YDPI = "ydpi";

    public static final String NODE_POWER_TYPE= "power-type";

    public static final String NODE_Y_DIMENSION = "y-dimension";

    public static final String NODE_SCREEN_RATIO = "screen-ratio";

    public static final String NODE_NAV_STATE = "nav-state";

    public static final String NODE_MIC = "mic";

    public static final String NODE_RAM = "ram";

    public static final String NODE_XDPI = "xdpi";

    public static final String NODE_DIMENSIONS = "dimensions";

    public static final String NODE_ABI = "abi";

    public static final String NODE_MECHANISM = "mechanism";

    public static final String NODE_MULTITOUCH = "multitouch";

    public static final String NODE_NAV = "nav";

    public static final String NODE_PIXEL_DENSITY = "pixel-density";

    public static final String NODE_SCREEN_ORIENTATION = "screen-orientation";

    public static final String NODE_AUTOFOCUS = "autofocus";

    public static final String NODE_SCREEN_SIZE = "screen-size";

    public static final String NODE_DESCRIPTION = "description";

    public static final String NODE_BLUETOOTH_PROFILES = "bluetooth-profiles";

    public static final String NODE_SCREEN = "screen";

    public static final String NODE_SENSORS = "sensors";

    public static final String NODE_DIAGONAL_LENGTH = "diagonal-length";

    public static final String NODE_SCREEN_TYPE = "screen-type";

    public static final String NODE_KEYBOARD_STATE = "keyboard-state";

    public static final String NODE_X_DIMENSION = "x-dimension";

    public static final String NODE_CPU = "cpu";

    public static final String NODE_INTERNAL_STORAGE = "internal-storage";

    public static final String NODE_META = "meta";

    public static final String NODE_ICONS = "icons";

    public static final String NODE_SIXTY_FOUR = "sixty-four";

    public static final String NODE_SIXTEEN = "sixteen";

    public static final String NODE_FRAME = "frame";

    public static final String NODE_PATH = "path";

    public static final String NODE_PORTRAIT_X_OFFSET = "portrait-x-offset";

    public static final String NODE_PORTRAIT_Y_OFFSET = "portrait-y-offset";

    public static final String NODE_LANDSCAPE_X_OFFSET = "landscape-x-offset";

    public static final String NODE_LANDSCAPE_Y_OFFSET = "landscape-y-offset";

    public static final String NODE_NAME = "name";

    public static final String NODE_API_LEVEL = "api-level";

    public static final String NODE_MANUFACTURER = "manufacturer";

    public static final String ATTR_DEFAULT = "default";

    public static final String ATTR_UNIT = "unit";

    public static final String ATTR_NAME = "name";

    /**
     * Validates the input stream.
     *
     * @param deviceXml
     *            The XML InputStream to validate.
     * @param out
     *            The OutputStream for error messages.
     * @param parent
     *            The parent directory of the input stream.
     * @return Whether the given input constitutes a valid devices file.
     */
    public static boolean validate(InputStream deviceXml, OutputStream out, File parent) {
        Schema s;
        SAXParserFactory factory = SAXParserFactory.newInstance();
        PrintWriter writer = new PrintWriter(out);
        try {
            s = DeviceSchema.getSchema();
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            factory.setSchema(s);
            ValidationHandler validator = new ValidationHandler(parent, writer);
            SAXParser parser = factory.newSAXParser();
            parser.parse(deviceXml, validator);
            return validator.isValidDevicesFile();
        } catch (SAXException e) {
            writer.println(e.getMessage());
            return false;
        } catch (ParserConfigurationException e) {
            writer.println("Error creating SAX parser:");
            writer.println(e.getMessage());
            return false;
        } catch (IOException e) {
            writer.println("Error reading file stream:");
            writer.println(e.getMessage());
            return false;
        } finally {
            writer.flush();
        }
    }

    /**
     * Helper to get an input stream of the device config XML schema.
     */
    public static InputStream getXsdStream() {
        return DeviceSchema.class.getResourceAsStream("devices.xsd"); //$NON-NLS-1$
    }

    /** Helper method that returns a {@link Validator} for our XSD */
    public static Schema getSchema() throws SAXException {
        InputStream xsdStream = getXsdStream();
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(new StreamSource(xsdStream));
        return schema;
    }

    /**
     * A DefaultHandler that parses only to validate the XML is actually a valid
     * devices config, since validation can't be entirely encoded in the devices
     * schema.
     */
    private static class ValidationHandler extends DefaultHandler {
        private boolean mValidDevicesFile = true;
        private boolean mDefaultSeen = false;
        private String mDeviceName;
        private final File mDirectory;
        private final PrintWriter mWriter;
        private final StringBuilder mStringAccumulator = new StringBuilder();

        public ValidationHandler(File directory, PrintWriter writer) {
            mDirectory = directory; // Possibly null
            mWriter = writer;
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (NODE_DEVICE.equals(localName)) {
                // Reset for a new device
                mDefaultSeen = false;
            } else if (NODE_STATE.equals(localName)) {
                // Check if the state is set to be a default state
                String val = attributes.getValue(ATTR_DEFAULT);
                if (val != null && ("1".equals(val) || Boolean.parseBoolean(val))) {
                    /*
                     * If it is and we already have a default state for this
                     * device, then the device configuration is invalid.
                     * Otherwise, set that we've seen a default state for this
                     * device and continue
                     */

                    if (mDefaultSeen) {
                        validationError("More than one default state for device " + mDeviceName);
                    } else {
                        mDefaultSeen = true;
                    }
                }
            }
            mStringAccumulator.setLength(0);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            mStringAccumulator.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            // If this is the end of a device node, make sure we have at least
            // one default state
            if (NODE_DEVICE.equals(localName) && !mDefaultSeen) {
                validationError("No default state for device " + mDeviceName);
            } else if (NODE_NAME.equals(localName)) {
                mDeviceName = mStringAccumulator.toString().trim();
            } else if (NODE_PATH.equals(localName) || NODE_SIXTY_FOUR.equals(localName)
                    || NODE_SIXTEEN.equals(localName)) {
                if (mDirectory == null) {
                    // There is no given parent directory, so this is not a
                    // valid devices file
                    validationError("No parent directory given, but relative paths exist.");
                    return;
                }
                // This is going to break on any files that end with a space,
                // but that should be an incredibly rare corner case.
                String relativePath = mStringAccumulator.toString().trim();
                File f = new File(mDirectory, relativePath);
                if (f == null || !f.isFile()) {
                    validationError(relativePath + " is not a valid path.");
                    return;
                }
                String fileName = f.getName();
                int extensionStart = fileName.lastIndexOf(".");
                if (extensionStart == -1 || !fileName.substring(extensionStart + 1).equals("png")) {
                    validationError(relativePath + " is not a valid file type.");
                }
            }
        }

        @Override
        public void error(SAXParseException e) {
            validationError(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) {
            validationError(e.getMessage());
        }

        public boolean isValidDevicesFile() {
            return mValidDevicesFile;
        }

        private void validationError(String reason) {
            mWriter.println("Error: " + reason);
            mValidDevicesFile = false;
        }

    }
}
