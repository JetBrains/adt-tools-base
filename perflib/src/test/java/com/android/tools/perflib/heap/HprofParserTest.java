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

package com.android.tools.perflib.heap;

import com.google.common.io.Closeables;

import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.Collection;

public class HprofParserTest extends TestCase {

    State mState;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        String file = getClass().getResource("/dialer.android-hprof").getFile();

        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        DataInputStream dis = new DataInputStream(bis);
        try {
            mState = (new HprofParser(dis)).parse();
        } finally {
            Closeables.close(dis, false);
        }
    }

    public void testHierarchy() {
        ClassObj application = mState.findClass("android.app.Application");
        assertNotNull(application);

        ClassObj contextWrapper = application.getSuperClassObj();
        assertNotNull(contextWrapper);
        assertEquals("android.content.ContextWrapper", contextWrapper.getClassName());
        contextWrapper.getSubclasses().contains(application);

        ClassObj context = contextWrapper.getSuperClassObj();
        assertNotNull(context);
        assertEquals("android.content.Context", context.getClassName());
        context.getSubclasses().contains(contextWrapper);

        ClassObj object = context.getSuperClassObj();
        assertNotNull(object);
        assertEquals("java.lang.Object", object.getClassName());
        object.getSubclasses().contains(context);

        ClassObj none = object.getSuperClassObj();
        assertNull(none);
    }

    /**
     * Tests the creation of an Enum class which covers static values, fields of type references,
     * strings and primitive values.
     */
    public void testObjectConstruction() {
        ClassObj clazz = mState.findClass("java.lang.Thread$State");
        assertNotNull(clazz);

        Object object = clazz.getStaticField(Type.OBJECT, "$VALUES").getValue();
        assertTrue(object instanceof ArrayInstance);
        ArrayInstance array = (ArrayInstance) object;
        Value[] values = array.getValues();
        assertEquals(6, values.length);

        Collection<Instance> instances = clazz.getInstances();
        for (Value value : values) {
            assertTrue(value.getValue() instanceof Instance);
            assertTrue(instances.contains(value.getValue()));
        }

        Object enumValue = clazz.getStaticField(Type.OBJECT, "NEW").getValue();
        assertTrue(enumValue instanceof ClassInstance);
        ClassInstance instance = (ClassInstance) enumValue;
        assertSame(clazz, instance.getClassObj());

        Object name = instance.getField(Type.OBJECT, "name").getValue();
        assertTrue(name instanceof ClassInstance);
        ClassInstance string = (ClassInstance) name;
        assertEquals("java.lang.String", string.getClassObj().getClassName());
        Object value = string.getField(Type.OBJECT, "value").getValue();
        assertTrue(value instanceof ArrayInstance);
        Value[] data = ((ArrayInstance) value).getValues();
        assertEquals(3, data.length);
        assertEquals('N', data[0].getValue());
        assertEquals('E', data[1].getValue());
        assertEquals('W', data[2].getValue());

        Object ordinal = instance.getField(Type.INT, "ordinal").getValue();
        assertEquals(0, ordinal);
    }
}
