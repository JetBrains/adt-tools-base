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

package com.android.repository.impl.local;

import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.GenericFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.RevisionType;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Tests for {@link LocalRepoLoaderImpl}.
 */
public class LocalRepoTest extends TestCase {

    // Test that we can parse a basic package.
    public void testParseGeneric() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFolder("/repo/random");
        mockFop.recordExistingFile("/repo/random/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <license type=\"text\" id=\"license1\">\n"
                        + "        This is the license\n"
                        + "        for this platform.\n"
                        + "    </license>\n"
                        + "\n"
                        + "    <localPackage path=\"random\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "        <uses-license ref=\"license1\"/>\n"
                        + "        <dependencies>\n"
                        + "            <dependency path=\"tools\">\n"
                        + "                <min-revision>\n"
                        + "                    <major>2</major>\n"
                        + "                    <micro>1</micro>\n"
                        + "                </min-revision>\n"
                        + "            </dependency>\n"
                        + "        </dependencies>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>"
        );

        RepoManager manager = RepoManager.create(mockFop);
        LocalRepoLoader localLoader = new LocalRepoLoaderImpl(new File("/repo"), manager, null,
                mockFop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        progress.assertNoErrorsOrWarnings();
        assertEquals(new Revision(3), p.getVersion());
        assertEquals("This is the license for this platform.", p.getLicense().getValue());
        assertTrue(p.getTypeDetails() instanceof TypeDetails.GenericType);
        assertEquals("The first Android platform ever", p.getDisplayName());
        // TODO: validate package in more detail
    }

    // Test writing a package out to xml
    public void testMarshalGeneric() throws Exception {
        RepoManager manager = new RepoManagerImpl(new MockFileOp());

        CommonFactory factory = (CommonFactory)RepoManager.getCommonModule().createLatestFactory();
        GenericFactory genericFactory = (GenericFactory) RepoManager.getGenericModule()
                .createLatestFactory();
        Repository repo = factory.createRepositoryType();
        LocalPackageImpl p = factory.createLocalPackage();
        License license = factory.createLicenseType("some license text", "license1");
        p.setLicense(license);
        p.setPath("dummy;path");
        p.setVersion(new Revision(1, 2));
        p.setDisplayName("package name");
        p.setTypeDetails((TypeDetails) genericFactory.createGenericDetailsType());
        Dependency dep = factory.createDependencyType();
        dep.setPath("depId1");
        RevisionType r = factory.createRevisionType(new Revision(1, 2, 3));
        dep.setMinRevision(r);
        p.addDependency(dep);
        dep = factory.createDependencyType();
        dep.setPath("depId2");
        p.addDependency(dep);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        repo.setLocalPackage(p);
        repo.getLicense().add(license);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        SchemaModuleUtil.marshal(
                ((CommonFactory) RepoManager.getCommonModule().createLatestFactory())
                        .generateRepository(repo),
                ImmutableSet.of(manager.getGenericModule()), output,
                manager.getResourceResolver(progress), progress);
        progress.assertNoErrorsOrWarnings();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setNamespaceAware(true);
        dbf.setSchema(SchemaModuleUtil.getSchema(ImmutableSet.of(RepoManager.getGenericModule()),
                SchemaModuleUtil.createResourceResolver(ImmutableSet.of(RepoManager.getCommonModule()),
                        progress),
                progress));
        progress.assertNoErrorsOrWarnings();
        DocumentBuilder db = dbf.newDocumentBuilder();

        db.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });

        // Can't just check the output against expected directly, since e.g. attribute node order
        // can change.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<ns3:repository "
                + "xmlns:ns2=\"http://schemas.android.com/repository/android/generic/01\" "
                + "xmlns:ns3=\"http://schemas.android.com/repository/android/common/01\">"
                + "<license type=\"text\" id=\"license1\">some license text</license>"
                + "<localPackage path=\"dummy;path\">"
                + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:type=\"ns2:genericDetailsType\"/>"
                + "<revision><major>1</major><minor>2</minor></revision>"
                + "<display-name>package name</display-name>"
                + "<uses-license ref=\"license1\"/>"
                + "<dependencies>"
                + "<dependency path=\"depId1\">"
                + "<min-revision><major>1</major><minor>2</minor><micro>3</micro></min-revision>"
                + "</dependency>"
                + "<dependency path=\"depId2\"/></dependencies></localPackage></ns3:repository>";
        Document doc = db.parse(new ByteArrayInputStream(output.toByteArray()));
        Document doc2 = db.parse(new ByteArrayInputStream(expected.getBytes()));
        assertTrue(doc.isEqualNode(doc2));
    }

    // Test that a package in an inconsistent location gives a warning.
    public void testWrongPath() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/repo/bogus/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"random\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>"
        );

        RepoManager manager = RepoManager.create(mockFop);
        LocalRepoLoader localLoader = new LocalRepoLoaderImpl(new File("/repo"), manager, null,
                mockFop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        assertEquals(new Revision(3), p.getVersion());
        assertTrue(!progress.getWarnings().isEmpty());
    }

    // Test that a package in an inconsistent is overridden by one in the right place
    public void testDuplicate() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/repo/bogus/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"random\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>1</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>"
        );
        mockFop.recordExistingFile("/repo/random/package.xml",
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "\n"
                        + "    <localPackage path=\"random\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>"
        );

        RepoManager manager = RepoManager.create(mockFop);
        LocalRepoLoader localLoader = new LocalRepoLoaderImpl(new File("/repo"), manager, null,
                mockFop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        LocalPackage p = localLoader.getPackages(progress).get("random");
        assertEquals(new Revision(3), p.getVersion());
        assertTrue(!progress.getWarnings().isEmpty());
    }

    // todo: test strictness
}
