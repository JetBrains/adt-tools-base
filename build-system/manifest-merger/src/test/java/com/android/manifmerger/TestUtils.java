/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Utilities for testing ManifestMerge classes.
 */
public class TestUtils {

    static class TestSourceLocation implements XmlLoader.SourceLocation {

        private final String mLocation;

        TestSourceLocation(Class sourceClass, String location) {
            this.mLocation = sourceClass.getSimpleName() + "#" + location;
        }

        @Override
        public String print(boolean shortFormat) {
            return mLocation;
        }

        @Override
        public Node toXml(Document document) {
            Element location = document.createElement("source");
            location.setAttribute("value", mLocation);
            return location;
        }
    }

    static XmlDocument xmlDocumentFromString(XmlLoader.SourceLocation location, String input)
            throws IOException, SAXException, ParserConfigurationException {
        return XmlLoader.load(location, input);
    }

}
