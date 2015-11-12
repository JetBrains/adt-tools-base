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

package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SchemaModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/**
 * Utilities for working with {@link SchemaModule}s, including marshalling and unmarshalling with
 * JAXB.
 */
public class SchemaModuleUtil {

    /**
     * Create an {@link LSResourceResolver} that will used the supplied {@link SchemaModule}s to
     * find an XSD from its namespace. This must be used when marshalling/unmarshalling if any
     * {@link SchemaModule}s contain XSDs which import others without specifying a complete
     * {@code schemaLocation}.
     */
    @NonNull
    public static LSResourceResolver createResourceResolver(
            @NonNull final Collection<SchemaModule> modules) {
        return new LSResourceResolver() {
            @Override
            public LSInput resolveResource(String type, String namespaceURI, String publicId,
                    String systemId, String baseURI) {
                SchemaModule.SchemaModuleVersion version = null;
                for (SchemaModule ext : modules) {
                    version = ext.getNamespaceVersionMap().get(namespaceURI);
                    if (version != null) {
                        return new DOMInputImpl(version.getNamespace(), null, null,
                                version.getXsd(), null);
                    }
                }
                return null;
            }
        };
    }

    /**
     * Creates a {@link JAXBContext} from the XSDs in the given {@link SchemaModule}s.
     */
    @NonNull
    private static JAXBContext getContext(@NonNull Collection<SchemaModule> possibleModules) {
        List<String> packages = Lists.newArrayList();
        for (SchemaModule module : possibleModules) {
            for (SchemaModule.SchemaModuleVersion version : module
                    .getNamespaceVersionMap().values()) {
                packages.add(version.getObjectFactory().getPackage().getName());
            }
        }
        JAXBContext jc = null;
        try {
            jc = JAXBContext.newInstance(Joiner.on(":").join(packages));
        } catch (JAXBException e1) {
            assert false : "Failed to create context!";
        }
        return jc;
    }

    /**
     * Creates a {@link Schema} from a collection of {@link SchemaModule}s, with a given
     * {@link LSResourceResolver} (probably obtained from
     * {@link #createResourceResolver(Collection)}. Any warnings or errors are logged to the
     * given {@link ProgressIndicator}.
     */
    @VisibleForTesting
    @NonNull
    public static Schema getSchema(
            final Collection<SchemaModule> possibleModules,
            @Nullable final LSResourceResolver resourceResolver, final ProgressIndicator progress) {
        Schema schema = null;
        SchemaFactory sf =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        if (resourceResolver != null) {
            sf.setResourceResolver(resourceResolver);
        }
        sf.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                progress.logWarning("Warning while creating schema:", exception);
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                progress.logWarning("Error creating schema:", exception);

            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                progress.logWarning("Fatal error creating schema:", exception);
            }
        });

        List<StreamSource> sources = Lists.newArrayList();
        for (SchemaModule module : possibleModules) {
            for (SchemaModule.SchemaModuleVersion version : module
                    .getNamespaceVersionMap()
                    .values()) {
                sources.add(new StreamSource(version.getXsd()));
            }
        }

        try {
            schema = sf.newSchema(sources.toArray(new StreamSource[sources.size()]));
        } catch (SAXException e) {
            assert false : "Invalid schema found!";
        }
        return schema;
    }

    /**
     * Use JAXB to create POJOs from the given XML.
     * @param xml The XML to read. The stream will be closed after being read.
     * @param possibleModules The {@link SchemaModule}s that are available to parse the XML.
     * @param resourceResolver Resolver for any imported XSDs.
     * @param progress For loggin.
     * @return The unmarshalled object.
     *
     * TODO: maybe templatize and return a nicer type.
     */
    @Nullable
    public static Object unmarshal(@NonNull InputStream xml,
            @NonNull Collection<SchemaModule> possibleModules,
            @Nullable LSResourceResolver resourceResolver, @NonNull ProgressIndicator progress) {
        JAXBContext context = getContext(possibleModules);
        Schema schema = getSchema(possibleModules, resourceResolver, progress);
        Unmarshaller u;
        try {
            u = context.createUnmarshaller();
            u.setSchema(schema);
            u.setEventHandler(createValidationEventHandler(progress));
            return ((JAXBElement) u.unmarshal(new StreamSource(xml))).getValue();
        } catch (JAXBException e) {
            progress.logWarning(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Transform the given {@link JAXBElement} into xml, using JAXB and the schemas provided by the
     * given {@link SchemaModule}s.
     */
    public static void marshal(@NonNull JAXBElement element,
            @NonNull Collection<SchemaModule> possibleModules,
            @NonNull OutputStream out, @Nullable LSResourceResolver resourceResolver,
            @NonNull ProgressIndicator progress) {
        JAXBContext context = getContext(possibleModules);
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setEventHandler(createValidationEventHandler(progress));
            Schema schema = getSchema(possibleModules, resourceResolver, progress);
            marshaller.setSchema(schema);
            marshaller.marshal(element, out);
            out.close();
        } catch (JAXBException e) {
            progress.logWarning(e.getMessage(), e);
        } catch (IOException e) {
            progress.logWarning(e.getMessage(), e);
        }
    }

    /**
     * Creates a {@link ValidationEventHandler} that delegates logging to the given
     * {@link ProgressIndicator}.
     */
    @NonNull
    private static ValidationEventHandler createValidationEventHandler(
            @NonNull final ProgressIndicator progress) {
        return new ValidationEventHandler() {
            @Override
            public boolean handleEvent(ValidationEvent event) {
                //noinspection ThrowableResultOfMethodCallIgnored
                if (event.getLinkedException() != null) {
                    progress.logWarning(event.getMessage(), event.getLinkedException());
                } else {
                    progress.logWarning(event.getMessage());
                }
                return false;
            }
        };
    }
}
