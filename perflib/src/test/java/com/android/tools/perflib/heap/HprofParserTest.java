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

import com.android.tools.perflib.captures.MemoryMappedFileBuffer;

import junit.framework.TestCase;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class HprofParserTest extends TestCase {

    Snapshot mSnapshot;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        File file = new File(getClass().getResource("/dialer.android-hprof").getFile());
        mSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
    }

    public void testHierarchy() {
        ClassObj application = mSnapshot.findClass("android.app.Application");
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

    public void testClassLoaders() {
        ClassObj application = mSnapshot.findClass("android.app.Application");
        assertNull(application.getClassLoader());

        ClassObj dialer = mSnapshot.findClass("com.android.dialer.DialerApplication");
        Instance classLoader = dialer.getClassLoader();
        assertNotNull(classLoader);
        assertEquals("dalvik.system.PathClassLoader", classLoader.getClassObj().getClassName());
    }

    public void testPrimitiveArrays() {
        ClassObj byteArray = mSnapshot.findClass("byte[]");
        assertEquals(1406, byteArray.getInstancesList().size());
        assertEquals(0, byteArray.getInstanceSize());
        assertEquals(681489, byteArray.getShallowSize());

        ArrayInstance byteArrayInstance = (ArrayInstance) mSnapshot.findInstance(0xB0D60401);
        assertEquals(byteArray, byteArrayInstance.getClassObj());
        assertEquals(43224, byteArrayInstance.getSize());
        assertEquals(43224, byteArrayInstance.getCompositeSize());

        ClassObj intArrayArray = mSnapshot.findClass("int[][]");
        assertEquals(37, intArrayArray.getInstancesList().size());

        ArrayInstance intArrayInstance = (ArrayInstance) mSnapshot.findInstance(0xB0F69F58);
        assertEquals(intArrayArray, intArrayInstance.getClassObj());
        assertEquals(40, intArrayInstance.getSize());
        assertEquals(52, intArrayInstance.getCompositeSize());

        ClassObj stringArray = mSnapshot.findClass("java.lang.String[]");
        assertEquals(1396, stringArray.getInstancesList().size());
    }

    /**
     * Tests the creation of an Enum class which covers static values, fields of type references,
     * strings and primitive values.
     */
    public void testObjectConstruction() {
        ClassObj clazz = mSnapshot.findClass("java.lang.Thread$State");
        assertNotNull(clazz);

        Object object = clazz.getStaticField(Type.OBJECT, "$VALUES");
        assertTrue(object instanceof ArrayInstance);
        ArrayInstance array = (ArrayInstance) object;
        Object[] values = array.getValues();
        assertEquals(6, values.length);

        Collection<Instance> instances = clazz.getInstancesList();
        for (Object value : values) {
            assertTrue(value instanceof Instance);
            assertTrue(instances.contains(value));
        }

        Object enumValue = clazz.getStaticField(Type.OBJECT, "NEW");
        assertTrue(enumValue instanceof ClassInstance);
        ClassInstance instance = (ClassInstance) enumValue;
        assertSame(clazz, instance.getClassObj());

        List<ClassInstance.FieldValue> fields = instance.getFields("name");
        assertEquals(1, fields.size());
        assertEquals(Type.OBJECT, fields.get(0).getField().getType());
        Object name = fields.get(0).getValue();

        assertTrue(name instanceof ClassInstance);
        ClassInstance string = (ClassInstance) name;
        assertEquals("java.lang.String", string.getClassObj().getClassName());
        fields = string.getFields("value");
        assertEquals(1, fields.size());
        assertEquals(Type.OBJECT, fields.get(0).getField().getType());
        Object value = fields.get(0).getValue();
        assertTrue(value instanceof ArrayInstance);
        Object[] data = ((ArrayInstance) value).getValues();
        assertEquals(3, data.length);
        assertEquals('N', data[0]);
        assertEquals('E', data[1]);
        assertEquals('W', data[2]);

        fields = instance.getFields("ordinal");
        assertEquals(1, fields.size());
        assertEquals(Type.INT, fields.get(0).getField().getType());
        assertEquals(0, fields.get(0).getValue());
    }

    /**
     * Tests getValues to make sure it's not adding duplicate entries to the back references.
     */
    public void testDuplicateEntries() {
        mSnapshot = new SnapshotBuilder(2).addReferences(1, 2).addRoot(1).build();
        mSnapshot.computeDominators();

        assertEquals(2, mSnapshot.getReachableInstances().size());
        ClassInstance parent = (ClassInstance)mSnapshot.findInstance(1);
        List<ClassInstance.FieldValue> firstGet = parent.getValues();
        List<ClassInstance.FieldValue> secondGet = parent.getValues();
        assertEquals(1, firstGet.size());
        assertEquals(firstGet.size(), secondGet.size());
        Instance child = mSnapshot.findInstance(2);
        assertEquals(1, child.getHardReferences().size());
    }

    public void testResolveReferences() {
        mSnapshot = new SnapshotBuilder(1).addRoot(1).build();
        ClassObj subSoftReferenceClass = new ClassObj(98, null, "SubSoftReference", 0);
        subSoftReferenceClass.setSuperClassId(SnapshotBuilder.SOFT_REFERENCE_ID);
        ClassObj subSubSoftReferenceClass = new ClassObj(97, null, "SubSubSoftReference", 0);
        subSubSoftReferenceClass.setSuperClassId(98);

        mSnapshot.findClass(SnapshotBuilder.SOFT_REFERENCE_ID).addSubclass(subSoftReferenceClass);
        subSoftReferenceClass.addSubclass(subSubSoftReferenceClass);

        mSnapshot.addClass(98, subSoftReferenceClass);
        mSnapshot.addClass(97, subSubSoftReferenceClass);

        mSnapshot.resolveReferences();

        assertTrue(subSoftReferenceClass.getIsSoftReference());
        assertTrue(subSubSoftReferenceClass.getIsSoftReference());
    }
}
