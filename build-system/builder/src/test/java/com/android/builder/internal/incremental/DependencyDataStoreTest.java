/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.internal.incremental;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class DependencyDataStoreTest extends TestCase {

    public void testStoreSingleData() throws IOException {
        // create a DependencyData object.
        DependencyData data = new DependencyData();
        data.setMainFile("/main/file");
        data.addSecondaryFile("/secondary/file");
        data.addOutputFile("/output/file");

        // create a store and add the data.
        DependencyDataStore store = new DependencyDataStore();
        store.addData(data);

        // and store it to disk
        File file = File.createTempFile("DependencyDataStoreTest", "");
        file.deleteOnExit();
        store.saveTo(file);

        // now load it
        store = new DependencyDataStore();
        store.loadFrom(file);

        Collection<DependencyData> newDataList = store.getData();
        assertNotNull(newDataList);
        assertEquals(1, newDataList.size());

        DependencyData newData = newDataList.iterator().next();
        assertNotNull(newData);

        // compare the values
        assertEquals(data.getMainFile(), newData.getMainFile());
        assertEquals(data.getSecondaryFiles(), newData.getSecondaryFiles());
        assertEquals(data.getOutputFiles(), newData.getOutputFiles());
    }

    public void testStoreSingleDataWithMultiFiles() throws IOException {
        // create a DependencyData object.
        DependencyData data = new DependencyData();
        data.setMainFile("/main/file");
        data.addSecondaryFile("/secondary/file");
        data.addSecondaryFile("/secondary/file2");
        data.addOutputFile("/output/file");
        data.addOutputFile("/output/file2");

        // create a store and add the data.
        DependencyDataStore store = new DependencyDataStore();
        store.addData(data);

        // and store it to disk
        File file = File.createTempFile("DependencyDataStoreTest", "");
        file.deleteOnExit();
        store.saveTo(file);

        // now load it
        store = new DependencyDataStore();
        store.loadFrom(file);

        Collection<DependencyData> newDataList = store.getData();
        assertNotNull(newDataList);
        assertEquals(1, newDataList.size());

        DependencyData newData = newDataList.iterator().next();
        assertNotNull(newData);

        // compare the values
        assertEquals(data.getMainFile(), newData.getMainFile());
        assertEquals(data.getSecondaryFiles(), newData.getSecondaryFiles());
        assertEquals(data.getOutputFiles(), newData.getOutputFiles());
    }


    public void testStoreMultiData() throws IOException {
        // create a DependencyData object.
        DependencyData data = new DependencyData();
        data.setMainFile("/1/main/file");
        data.addSecondaryFile("/1/secondary/file");
        data.addOutputFile("/1/output/file");

        DependencyData data2 = new DependencyData();
        data2.setMainFile("/2/main/file");
        data2.addSecondaryFile("/2/secondary/file");
        data2.addOutputFile("/2/output/file");

        // create a store and add the data.
        DependencyDataStore store = new DependencyDataStore();
        store.addData(data);
        store.addData(data2);

        // and store it to disk
        File file = File.createTempFile("DependencyDataStoreTest", "");
        file.deleteOnExit();
        store.saveTo(file);

        // now load it
        store = new DependencyDataStore();
        store.loadFrom(file);

        // get the collection to check on the size.
        Collection<DependencyData> newDataList = store.getData();
        assertEquals(2, newDataList.size());

        DependencyData firstData = store.getByMainFile("/1/main/file");
        assertNotNull(firstData);

        // compare the values
        assertEquals(data.getMainFile(), firstData.getMainFile());
        assertEquals(data.getSecondaryFiles(), firstData.getSecondaryFiles());
        assertEquals(data.getOutputFiles(), firstData.getOutputFiles());

        DependencyData secondData = store.getByMainFile("/2/main/file");
        assertNotNull(secondData);

        // compare the values
        assertEquals(data2.getMainFile(), secondData.getMainFile());
        assertEquals(data2.getSecondaryFiles(), secondData.getSecondaryFiles());
        assertEquals(data2.getOutputFiles(), secondData.getOutputFiles());
    }

    public void testStoreNoOutputData() throws IOException {
        // create a DependencyData object.
        DependencyData data = new DependencyData();
        data.setMainFile("/1/main/file");
        data.addSecondaryFile("/1/secondary/file");

        DependencyData data2 = new DependencyData();
        data2.setMainFile("/2/main/file");
        data2.addSecondaryFile("/2/secondary/file");
        data2.addOutputFile("/2/output/file");

        // create a store and add the data.
        DependencyDataStore store = new DependencyDataStore();
        store.addData(data);
        store.addData(data2);

        // and store it to disk
        File file = File.createTempFile("DependencyDataStoreTest", "");
        file.deleteOnExit();
        store.saveTo(file);

        // now load it
        store = new DependencyDataStore();
        store.loadFrom(file);

        // get the collection to check on the size.
        Collection<DependencyData> newDataList = store.getData();
        assertEquals(2, newDataList.size());

        DependencyData firstData = store.getByMainFile("/1/main/file");
        assertNotNull(firstData);

        // compare the values
        assertEquals(data.getMainFile(), firstData.getMainFile());
        assertEquals(data.getSecondaryFiles(), firstData.getSecondaryFiles());
        assertEquals(0, firstData.getOutputFiles().size());

        DependencyData secondData = store.getByMainFile("/2/main/file");
        assertNotNull(secondData);

        // compare the values
        assertEquals(data2.getMainFile(), secondData.getMainFile());
        assertEquals(data2.getSecondaryFiles(), secondData.getSecondaryFiles());
        assertEquals(data2.getOutputFiles(), secondData.getOutputFiles());
    }

    public void testStoreHeaderData() throws IOException {
        // create a DependencyData object.
        DependencyData data = new DependencyData();
        data.setMainFile("/1/main/file");

        DependencyData data2 = new DependencyData();
        data2.setMainFile("/2/main/file");

        // create a store and add the data.
        DependencyDataStore store = new DependencyDataStore();
        store.addData(data);
        store.addData(data2);

        // and store it to disk
        File file = File.createTempFile("DependencyDataStoreTest", "");
        file.deleteOnExit();
        store.saveTo(file);

        // now load it
        store = new DependencyDataStore();
        store.loadFrom(file);

        // get the collection to check on the size.
        Collection<DependencyData> newDataList = store.getData();
        assertEquals(2, newDataList.size());

        DependencyData firstData = store.getByMainFile("/1/main/file");
        assertNotNull(firstData);

        // compare the values
        assertEquals(data.getMainFile(), firstData.getMainFile());
        assertEquals(0, firstData.getSecondaryFiles().size());
        assertEquals(0, firstData.getOutputFiles().size());

        DependencyData secondData = store.getByMainFile("/2/main/file");
        assertNotNull(secondData);

        // compare the values
        assertEquals(data2.getMainFile(), secondData.getMainFile());
        assertEquals(0, secondData.getSecondaryFiles().size());
        assertEquals(0, secondData.getOutputFiles().size());
    }
}
