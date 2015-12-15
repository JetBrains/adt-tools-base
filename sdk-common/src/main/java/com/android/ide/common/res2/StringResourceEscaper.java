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
package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

final class StringResourceEscaper {

    static final String STRING_ELEMENT_NAME = "string";

    private static final Pattern DECIMAL_REFERENCE = Pattern.compile("&#(\\p{Digit}+);");

    private static final String DECIMAL_PLACEHOLDER = "___D";

    private static final Pattern HEXADECIMAL_REFERENCE = Pattern.compile("&#x(\\p{XDigit}+);");

    private static final String HEXADECIMAL_PLACEHOLDER = "___X";

    private static final Pattern ESCAPED_DECIMAL_REFERENCE
            = Pattern.compile(DECIMAL_PLACEHOLDER + "(\\p{Digit}+);");

    private static final Pattern ESCAPED_HEXADECIMAL_REFERENCE
            = Pattern.compile(HEXADECIMAL_PLACEHOLDER + "(\\p{XDigit}+);");

    private StringResourceEscaper() {
    }

    @NonNull
    static String escapeCharacterData(@NonNull String xml) {
        if (xml.isEmpty()) {
            return "";
        }

        // This is a hack. We want to preserve character references but the SAX parser always
        // resolves them.
        xml = DECIMAL_REFERENCE.matcher(xml).replaceAll(DECIMAL_PLACEHOLDER + "$1;");
        xml = HEXADECIMAL_REFERENCE.matcher(xml).replaceAll(HEXADECIMAL_PLACEHOLDER + "$1;");

        StringBuilder builder = new StringBuilder(xml.length() * 3 / 2);

        if (startsOrEndsWithSpace(xml)) {
            builder.append('"');
        } else if (startsWithQuestionMarkOrAtSign(xml)) {
            builder.append('\\');
        }

        try {
            parse(xml, builder);
        } catch (SAXException exception) {
            throw new IllegalArgumentException(xml, exception);
        }

        if (startsOrEndsWithSpace(xml)) {
            builder.append('"');
        }

        xml = builder.toString();

        xml = ESCAPED_DECIMAL_REFERENCE.matcher(xml).replaceAll("&#$1;");
        xml = ESCAPED_HEXADECIMAL_REFERENCE.matcher(xml).replaceAll("&#x$1;");

        return xml;
    }

    private static void parse(@NonNull String string, @NonNull StringBuilder builder)
            throws SAXException {
        XMLReader reader;

        try {
            Escaper escaper = buildEscaper(!startsOrEndsWithSpace(string), false);
            ContentHandler handler = new StringResourceEscaperContentHandler(builder, escaper);

            reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            reader.setContentHandler(handler);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        } catch (ParserConfigurationException exception) {
            throw new RuntimeException(exception);
        } catch (SAXException exception) {
            throw new RuntimeException(exception);
        }

        try {
            reader.parse(new InputSource(new StringReader(containInStringElement(string))));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @NonNull
    private static String containInStringElement(@NonNull String string) {
        return "<" + STRING_ELEMENT_NAME + ">" + string + "</" + STRING_ELEMENT_NAME + ">";
    }

    @NonNull
    static String escape(@NonNull String string, boolean escapeMarkupDelimiters) {
        if (string.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(string.length() * 3 / 2);

        if (startsOrEndsWithSpace(string)) {
            builder.append('"');
        } else if (startsWithQuestionMarkOrAtSign(string)) {
            builder.append('\\');
        }

        Escaper escaper = buildEscaper(!startsOrEndsWithSpace(string), escapeMarkupDelimiters);
        builder.append(escaper.escape(string));

        if (startsOrEndsWithSpace(string)) {
            builder.append('"');
        }

        return builder.toString();
    }

    @NonNull
    private static Escaper buildEscaper(boolean escapeApostrophes, boolean escapeMarkupDelimiters) {
        Escapers.Builder builder = Escapers.builder()
                .addEscape('"', "\\\"")
                .addEscape('\\', "\\\\")
                .addEscape('\n', "\\n")
                .addEscape('\t', "\\t");

        if (escapeApostrophes) {
            builder.addEscape('\'', "\\'");
        }

        if (escapeMarkupDelimiters) {
            builder
                    .addEscape('&', "&amp;")
                    .addEscape('<', "&lt;");
        }

        return builder.build();
    }

    private static boolean startsWithQuestionMarkOrAtSign(@NonNull String string) {
        assert !string.isEmpty();
        return string.charAt(0) == '?' || string.charAt(0) == '@';
    }

    private static boolean startsOrEndsWithSpace(@NonNull String string) {
        assert !string.isEmpty();
        return string.charAt(0) == ' ' || string.charAt(string.length() - 1) == ' ';
    }
}
