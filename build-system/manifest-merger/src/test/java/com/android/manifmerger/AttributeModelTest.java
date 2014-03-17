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

import static org.mockito.Mockito.verify;

import com.android.sdklib.mock.MockLog;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link com.android.manifmerger.AttributeModel} class
 */
public class AttributeModelTest extends TestCase {

    @Mock
    AttributeModel.Validator mValidator;

    @Mock
    XmlAttribute mXmlAttribute;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testGetters() {
        AttributeModel attributeModel = new AttributeModel(
                "someName", true /* packageDependent */, "default_value", mValidator);

        assertEquals("someName", attributeModel.getName());
        assertTrue(attributeModel.isPackageDependent());
        assertEquals("default_value", attributeModel.getDefaultValue());

        attributeModel = new AttributeModel("someName", false /* packageDependent */,
                null /* defaultValue */, null /* validator */);

        assertEquals("someName", attributeModel.getName());
        assertFalse(attributeModel.isPackageDependent());
        assertEquals(null, attributeModel.getDefaultValue());

        Mockito.verifyZeroInteractions(mValidator);
    }

    public void testValidator() {

        AttributeModel.BooleanValidator booleanValidator = new AttributeModel.BooleanValidator();
        MockLog mockLog = new MockLog();
        MergingReport.Builder mergingReport = new MergingReport.Builder(mockLog);
        assertTrue(booleanValidator.validates(mergingReport, mXmlAttribute, "false"));
        assertTrue(booleanValidator.validates(mergingReport, mXmlAttribute, "true"));
        assertTrue(booleanValidator.validates(mergingReport, mXmlAttribute, "FALSE"));
        assertTrue(booleanValidator.validates(mergingReport, mXmlAttribute, "TRUE"));
        assertTrue(booleanValidator.validates(mergingReport, mXmlAttribute, "False"));
        assertTrue(booleanValidator.validates(mergingReport, mXmlAttribute, "True"));

        assertFalse(booleanValidator.validates(mergingReport, mXmlAttribute, "foo"));
        verify(mXmlAttribute).printPosition();
    }
}
