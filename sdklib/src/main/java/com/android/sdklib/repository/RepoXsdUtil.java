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

package com.android.sdklib.repository;

import com.google.common.collect.Lists;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;

/**
 * Utilities related to the respository XSDs.
 */
public class RepoXsdUtil {

    /**
     * Returns a stream to the requested XML Schema.
     *
     * @param rootElement The root of the filename of the XML schema. This is by convention the same
     *                    as the root element declared by the schema.
     * @param version     The XML schema revision number, an integer >= 1.
     * @return An {@link InputStream} object for the local XSD file or null if there is no schema
     * for the requested version.
     */
    public static InputStream getXsdStream(String rootElement, int version) {
        String filename = String.format("%1$s-%2$02d.xsd", rootElement, version);      //$NON-NLS-1$

        InputStream stream = null;
        try {
            stream = RepoXsdUtil.class.getResourceAsStream(filename);
        } catch (Exception e) {
            // Some implementations seem to return null on failure,
            // others throw an exception. We want to return null.
        }
        if (stream == null) {
            // Try the alternate schemas that are not published yet.
            // This allows us to internally test with new schemas before the
            // public repository uses it.
            filename = String.format("-%1$s-%2$02d.xsd", rootElement, version);      //$NON-NLS-1$
            try {
                stream = RepoXsdUtil.class.getResourceAsStream(filename);
            } catch (Exception e) {
                // Some implementations seem to return null on failure,
                // others throw an exception. We want to return null.
            }
        }

        return stream;
    }
}
