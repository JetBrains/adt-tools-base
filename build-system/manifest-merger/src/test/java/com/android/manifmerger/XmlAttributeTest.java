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

import static org.mockito.Mockito.when;

import com.android.SdkConstants;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Attr;

/**
 * Tests for {@link com.android.manifmerger.XmlAttribute} class
 */
public class XmlAttributeTest extends TestCase {

    @Mock XmlDocument mXmlDocument;
    @Mock XmlElement mXmlElement;
    @Mock Attr mAttr;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        when(mAttr.getNamespaceURI()).thenReturn(SdkConstants.ANDROID_URI);
        when(mAttr.getPrefix()).thenReturn("android");
        when(mAttr.getLocalName()).thenReturn("name");

        when(mXmlElement.getType()).thenReturn(ManifestModel.NodeTypes.ACTIVITY);
        when(mXmlElement.getDocument()).thenReturn(mXmlDocument);
        when(mXmlDocument.getPackageName()).thenReturn("com.foo.bar");
    }

    public void testPackageSubstitution_noDot() {

        when(mAttr.getValue()).thenReturn("ActivityOne");
        // this will reset the package.
        assertNotNull(new XmlAttribute(mXmlElement, mAttr));
        Mockito.verify(mAttr).setValue("com.foo.bar.ActivityOne");
    }

    public void testPackageSubstitution_withDot() {

        when(mAttr.getValue()).thenReturn(".ActivityOne");
        // this will reset the package.
        assertNotNull(new XmlAttribute(mXmlElement, mAttr));
        Mockito.verify(mAttr).setValue("com.foo.bar.ActivityOne");
    }

    public void testNoPackageSubstitution() {

        when(mAttr.getValue()).thenReturn("com.foo.foo2.ActivityOne");
        // this will NOT reset the package.
        assertNotNull(new XmlAttribute(mXmlElement, mAttr));
        Mockito.verify(mAttr).getNamespaceURI();
        Mockito.verify(mAttr).getLocalName();
        Mockito.verify(mAttr).getValue();
        Mockito.verifyNoMoreInteractions(mAttr);
    }
}
