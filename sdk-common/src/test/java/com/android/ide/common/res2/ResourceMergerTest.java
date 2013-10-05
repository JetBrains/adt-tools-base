/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.FD_RES_DRAWABLE;
import static com.android.SdkConstants.FD_RES_LAYOUT;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.testutils.TestUtils;
import com.android.utils.SdkUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceMergerTest extends BaseTestCase {

    public void testMergeByCount() throws Exception {
        ResourceMerger merger = getResourceMerger();

        assertEquals(27, merger.size());
    }

    public void testMergedResourcesByName() throws Exception {
        ResourceMerger merger = getResourceMerger();

        verifyResourceExists(merger,
                "drawable/icon",
                "drawable-ldpi/icon",
                "drawable/icon2",
                "drawable/patch",
                "raw/foo",
                "layout/main",
                "layout/layout_ref",
                "layout/alias_replaced_by_file",
                "layout/file_replaced_by_alias",
                "drawable/color_drawable",
                "drawable/drawable_ref",
                "color/color",
                "string/basic_string",
                "string/xliff_string",
                "string/styled_string",
                "style/style",
                "array/string_array",
                "attr/dimen_attr",
                "attr/string_attr",
                "attr/enum_attr",
                "attr/flag_attr",
                "attr/blah",
                "attr/blah2",
                "declare-styleable/declare_styleable",
                "dimen/dimen",
                "id/item_id",
                "integer/integer"
        );
    }

    private String getPlatformPath(String path) {
        return path.replaceAll("/", Matcher.quoteReplacement(File.separator));
    }

    public void testReplacedLayout() throws Exception {
        ResourceMerger merger = getResourceMerger();
        ListMultimap<String, ResourceItem> mergedMap = merger.getDataMap();

        List<ResourceItem> values = mergedMap.get("layout/main");

        // the overlay means there's 2 versions of this resource.
        assertEquals(2, values.size());
        ResourceItem mainLayout = values.get(1);

        ResourceFile sourceFile = mainLayout.getSource();
        assertTrue(sourceFile.getFile().getAbsolutePath()
            .endsWith(getPlatformPath("overlay/layout/main.xml")));
    }

    public void testReplacedAlias() throws Exception {
        ResourceMerger merger = getResourceMerger();
        ListMultimap<String, ResourceItem> mergedMap = merger.getDataMap();

        List<ResourceItem> values = mergedMap.get("layout/alias_replaced_by_file");

        // the overlay means there's 2 versions of this resource.
        assertEquals(2, values.size());
        ResourceItem layout = values.get(1);

        // since it's replaced by a file, there's no node.
        assertNull(layout.getValue());
    }

    public void testReplacedFile() throws Exception {
        ResourceMerger merger = getResourceMerger();
        ListMultimap<String, ResourceItem> mergedMap = merger.getDataMap();

        List<ResourceItem> values = mergedMap.get("layout/file_replaced_by_alias");

        // the overlay means there's 2 versions of this resource.
        assertEquals(2, values.size());
        ResourceItem layout = values.get(1);

        // since it's replaced by an alias, there's a node
        assertNotNull(layout.getValue());
    }

    public void testMergeWrite() throws Exception {
        ResourceMerger merger = getResourceMerger();
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused");
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        // compare the two maps, but not using the full map as the set loaded from the output
        // won't contains all versions of each ResourceItem item.
        compareResourceMaps(merger, writtenSet, false /*full compare*/);
        checkLogger(logger);
    }

    public void testNotMergedAttr() throws Exception {
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused");
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        List<ResourceItem> items = writtenSet.getDataMap().get("attr/blah");
        assertEquals(1, items.size());
        assertTrue(items.get(0).getIgnoredFromDiskMerge());

        checkLogger(logger);
    }

    public void testMergedAttr() throws Exception {
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused");
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        List<ResourceItem> items = writtenSet.getDataMap().get("attr/blah2");
        assertEquals(1, items.size());
        assertFalse(items.get(0).getIgnoredFromDiskMerge());

        checkLogger(logger);
    }

    public void testNotMergedAttrFromMerge() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = Files.createTempDir();
        merger.writeBlobTo(folder,
                new MergedResourceWriter(Files.createTempDir(), null /*aaptRunner*/));

        ResourceMerger loadedMerger = new ResourceMerger();
        loadedMerger.loadFromBlob(folder, true /*incrementalState*/);

        // check that attr/blah is ignoredFromDiskMerge.
        List<ResourceItem> items = loadedMerger.getDataSets().get(0).getDataMap().get("attr/blah");
        assertEquals(1, items.size());
        assertTrue(items.get(0).getIgnoredFromDiskMerge());
    }

    public void testWrittenDeclareStyleable() throws Exception {
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused");
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        List<ResourceItem> items = writtenSet.getDataMap().get("declare-styleable/declare_styleable");
        assertEquals(1, items.size());

        Node styleableNode = items.get(0).getValue();
        assertNotNull(styleableNode);

        // inspect the node
        NodeList nodes = styleableNode.getChildNodes();

        boolean foundBlah = false;
        boolean foundAndroidColorForegroundInverse = false;
        boolean foundBlah2 = false;

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = node.getLocalName();
            if (ResourceType.ATTR.getName().equals(nodeName)) {
                Attr attribute = (Attr) node.getAttributes().getNamedItemNS(null, ATTR_NAME);

                if (attribute != null) {
                    String name = attribute.getValue();
                    if ("blah".equals(name)) {
                        foundBlah = true;
                    } else if ("android:colorForegroundInverse".equals(name)) {
                        foundAndroidColorForegroundInverse = true;
                    } else if ("blah2".equals(name)) {
                        foundBlah2 = true;
                    }
                }

            }
        }

        assertTrue(foundBlah);
        assertTrue(foundAndroidColorForegroundInverse);
        assertTrue(foundBlah2);

        checkLogger(logger);
    }

    public void testMergeBlob() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = Files.createTempDir();
        merger.writeBlobTo(folder,
                new MergedResourceWriter(Files.createTempDir(), null /*aaptRunner*/));

        ResourceMerger loadedMerger = new ResourceMerger();
        loadedMerger.loadFromBlob(folder, true /*incrementalState*/);

        compareResourceMaps(merger, loadedMerger, true /*full compare*/);
    }

    /**
     * Tests the path replacement in the merger.xml file loaded from testData/
     * @throws Exception
     */
    public void testLoadingTestPathReplacement() throws Exception {
        File root = TestUtils.getRoot("resources", "baseMerge");
        File fakeRoot = getMergedBlobFolder(root);

        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        for (ResourceSet set : sets) {
            List<File> sourceFiles = set.getSourceFiles();

            // there should only be one
            assertEquals(1, sourceFiles.size());

            File sourceFile = sourceFiles.get(0);
            assertTrue(String.format("File %s is located in %s", sourceFile, root),
                    sourceFile.getAbsolutePath().startsWith(root.getAbsolutePath()));
        }
    }

    public void testUpdateWithBasicFiles() throws Exception {
        File root = getIncMergeRoot("basicFiles");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger =  new RecordingLogger();

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainDrawable = new File(mainBase, "drawable");
        File mainDrawableLdpi = new File(mainBase, "drawable-ldpi");

        // touched/removed files:
        File mainDrawableTouched = new File(mainDrawable, "touched.png");
        mainSet.updateWith(mainBase, mainDrawableTouched, FileStatus.CHANGED, logger);
        checkLogger(logger);

        File mainDrawableRemoved = new File(mainDrawable, "removed.png");
        mainSet.updateWith(mainBase, mainDrawableRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        File mainDrawableLdpiRemoved = new File(mainDrawableLdpi, "removed.png");
        mainSet.updateWith(mainBase, mainDrawableLdpiRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayDrawable = new File(overlayBase, "drawable");
        File overlayDrawableHdpi = new File(overlayBase, "drawable-hdpi");

        // new/removed files:
        File overlayDrawableNewOverlay = new File(overlayDrawable, "new_overlay.png");
        overlaySet.updateWith(overlayBase, overlayDrawableNewOverlay, FileStatus.NEW, logger);
        checkLogger(logger);

        File overlayDrawableRemovedOverlay = new File(overlayDrawable, "removed_overlay.png");
        overlaySet.updateWith(overlayBase, overlayDrawableRemovedOverlay, FileStatus.REMOVED,
                logger);
        checkLogger(logger);

        File overlayDrawableHdpiNewAlternate = new File(overlayDrawableHdpi, "new_alternate.png");
        overlaySet.updateWith(overlayBase, overlayDrawableHdpiNewAlternate, FileStatus.NEW, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the content.
        ListMultimap<String, ResourceItem> mergedMap = resourceMerger.getDataMap();

        // check unchanged file is WRITTEN
        List<ResourceItem> drawableUntouched = mergedMap.get("drawable/untouched");
        assertEquals(1, drawableUntouched.size());
        assertTrue(drawableUntouched.get(0).isWritten());
        assertFalse(drawableUntouched.get(0).isTouched());
        assertFalse(drawableUntouched.get(0).isRemoved());

        // check replaced file is TOUCHED
        List<ResourceItem> drawableTouched = mergedMap.get("drawable/touched");
        assertEquals(1, drawableTouched.size());
        assertTrue(drawableTouched.get(0).isWritten());
        assertTrue(drawableTouched.get(0).isTouched());
        assertFalse(drawableTouched.get(0).isRemoved());

        // check removed file is REMOVED
        List<ResourceItem> drawableRemoved = mergedMap.get("drawable/removed");
        assertEquals(1, drawableRemoved.size());
        assertTrue(drawableRemoved.get(0).isWritten());
        assertTrue(drawableRemoved.get(0).isRemoved());

        // check new overlay: two objects, last one is TOUCHED
        List<ResourceItem> drawableNewOverlay = mergedMap.get("drawable/new_overlay");
        assertEquals(2, drawableNewOverlay.size());
        ResourceItem newOverlay = drawableNewOverlay.get(1);
        assertEquals(overlayDrawableNewOverlay, newOverlay.getSource().getFile());
        assertFalse(newOverlay.isWritten());
        assertTrue(newOverlay.isTouched());

        // check new alternate: one objects, last one is TOUCHED
        List<ResourceItem> drawableHdpiNewAlternate = mergedMap.get("drawable-hdpi/new_alternate");
        assertEquals(1, drawableHdpiNewAlternate.size());
        ResourceItem newAlternate = drawableHdpiNewAlternate.get(0);
        assertEquals(overlayDrawableHdpiNewAlternate, newAlternate.getSource().getFile());
        assertFalse(newAlternate.isWritten());
        assertTrue(newAlternate.isTouched());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        MergedResourceWriter writer = new MergedResourceWriter(resFolder, null /*aaptRunner*/);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // Check the content.
        checkImageColor(new File(resFolder, "drawable" + File.separator + "touched.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable" + File.separator + "untouched.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable" + File.separator + "new_overlay.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable" + File.separator + "removed_overlay.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable-hdpi" + File.separator + "new_alternate.png"),
                (int) 0xFF00FF00);
        assertFalse(new File(resFolder, "drawable-ldpi" + File.separator + "removed.png").isFile());
    }

    public void testUpdateWithBasicValues() throws Exception {
        File root = getIncMergeRoot("basicValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, "values");
        File mainValuesEn = new File(mainBase, "values-en");

        // touched file:
        File mainValuesTouched = new File(mainValues, "values.xml");
        mainSet.updateWith(mainBase, mainValuesTouched, FileStatus.CHANGED, logger);
        checkLogger(logger);


        // removed files
        File mainValuesEnRemoved = new File(mainValuesEn, "values.xml");
        mainSet.updateWith(mainBase, mainValuesEnRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);


        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayValues = new File(overlayBase, "values");
        File overlayValuesFr = new File(overlayBase, "values-fr");

        // new files:
        File overlayValuesNew = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesNew, FileStatus.NEW, logger);
        checkLogger(logger);

        File overlayValuesFrNew = new File(overlayValuesFr, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesFrNew, FileStatus.NEW, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the content.
        ListMultimap<String, ResourceItem> mergedMap = resourceMerger.getDataMap();

        // check unchanged string is WRITTEN
        List<ResourceItem> valuesUntouched = mergedMap.get("string/untouched");
        assertEquals(1, valuesUntouched.size());
        assertTrue(valuesUntouched.get(0).isWritten());
        assertFalse(valuesUntouched.get(0).isTouched());
        assertFalse(valuesUntouched.get(0).isRemoved());

        // check replaced file is TOUCHED
        List<ResourceItem> valuesTouched = mergedMap.get("string/touched");
        assertEquals(1, valuesTouched.size());
        assertTrue(valuesTouched.get(0).isWritten());
        assertTrue(valuesTouched.get(0).isTouched());
        assertFalse(valuesTouched.get(0).isRemoved());

        // check removed file is REMOVED
        List<ResourceItem> valuesRemoved = mergedMap.get("string/removed");
        assertEquals(1, valuesRemoved.size());
        assertTrue(valuesRemoved.get(0).isWritten());
        assertTrue(valuesRemoved.get(0).isRemoved());

        valuesRemoved = mergedMap.get("string-en/removed");
        assertEquals(1, valuesRemoved.size());
        assertTrue(valuesRemoved.get(0).isWritten());
        assertTrue(valuesRemoved.get(0).isRemoved());

        // check new overlay: two objects, last one is TOUCHED
        List<ResourceItem> valuesNewOverlay = mergedMap.get("string/new_overlay");
        assertEquals(2, valuesNewOverlay.size());
        ResourceItem newOverlay = valuesNewOverlay.get(1);
        assertFalse(newOverlay.isWritten());
        assertTrue(newOverlay.isTouched());

        // check new alternate: one objects, last one is TOUCHED
        List<ResourceItem> valuesFrNewAlternate = mergedMap.get("string-fr/new_alternate");
        assertEquals(1, valuesFrNewAlternate.size());
        ResourceItem newAlternate = valuesFrNewAlternate.get(0);
        assertFalse(newAlternate.isWritten());
        assertTrue(newAlternate.isTouched());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        MergedResourceWriter writer = new MergedResourceWriter(resFolder, null /*aaptRunner*/);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // Check the content.
        // values/values.xml
        Map<String, String> map = quickStringOnlyValueFileParser(
                new File(resFolder, "values" + File.separator + "values.xml"));
        assertEquals("untouched", map.get("untouched"));
        assertEquals("touched", map.get("touched"));
        assertEquals("new_overlay", map.get("new_overlay"));

        // values/values-fr.xml
        map = quickStringOnlyValueFileParser(
                new File(resFolder, "values-fr" + File.separator + "values.xml"));
        assertEquals("new_alternate", map.get("new_alternate"));

        // deleted values-en/values.xml
        assertFalse(new File(resFolder, "values-en" + File.separator + "values.xml").isFile());
    }

    public void testUpdateWithBasicValues2() throws Exception {
        File root = getIncMergeRoot("basicValues2");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // first set is the main one, no change here

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayValues = new File(overlayBase, "values");

        // new files:
        File overlayValuesNew = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesNew, FileStatus.REMOVED, logger);
        checkLogger(logger);


        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the content.
        ListMultimap<String, ResourceItem> mergedMap = resourceMerger.getDataMap();

        // check unchanged string is WRITTEN
        List<ResourceItem> valuesUntouched = mergedMap.get("string/untouched");
        assertEquals(1, valuesUntouched.size());
        assertTrue(valuesUntouched.get(0).isWritten());
        assertFalse(valuesUntouched.get(0).isTouched());
        assertFalse(valuesUntouched.get(0).isRemoved());

        // check removed_overlay is present twice.
        List<ResourceItem> valuesRemovedOverlay = mergedMap.get("string/removed_overlay");
        assertEquals(2, valuesRemovedOverlay.size());
        // first is untouched
        assertFalse(valuesRemovedOverlay.get(0).isWritten());
        assertFalse(valuesRemovedOverlay.get(0).isTouched());
        assertFalse(valuesRemovedOverlay.get(0).isRemoved());
        // other is removed
        assertTrue(valuesRemovedOverlay.get(1).isWritten());
        assertFalse(valuesRemovedOverlay.get(1).isTouched());
        assertTrue(valuesRemovedOverlay.get(1).isRemoved());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        MergedResourceWriter writer = new MergedResourceWriter(resFolder, null /*aaptRunner*/);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // Check the content.
        // values/values.xml
        Map<String, String> map = quickStringOnlyValueFileParser(
                new File(resFolder, "values" + File.separator + "values.xml"));
        assertEquals("untouched", map.get("untouched"));
        assertEquals("untouched", map.get("removed_overlay"));
    }

    public void testUpdateWithFilesVsValues() throws Exception {
        File root = getIncMergeRoot("filesVsValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(1, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, ResourceFolderType.VALUES.getName());
        File mainLayout = new File(mainBase, ResourceFolderType.LAYOUT.getName());

        // touched file:
        File mainValuesTouched = new File(mainValues, "values.xml");
        mainSet.updateWith(mainBase, mainValuesTouched, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // new file:
        File mainLayoutNew = new File(mainLayout, "alias_replaced_by_file.xml");
        mainSet.updateWith(mainBase, mainLayoutNew, FileStatus.NEW, logger);
        checkLogger(logger);

        // removed file
        File mainLayoutRemoved = new File(mainLayout, "file_replaced_by_alias.xml");
        mainSet.updateWith(mainBase, mainLayoutRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the content.
        ListMultimap<String, ResourceItem> mergedMap = resourceMerger.getDataMap();

        // check layout/main is unchanged
        List<ResourceItem> layoutMain = mergedMap.get("layout/main");
        assertEquals(1, layoutMain.size());
        assertTrue(layoutMain.get(0).isWritten());
        assertFalse(layoutMain.get(0).isTouched());
        assertFalse(layoutMain.get(0).isRemoved());

        // check file_replaced_by_alias has 2 version, 2nd is TOUCHED, and contains a Node
        List<ResourceItem> layoutReplacedByAlias = mergedMap.get("layout/file_replaced_by_alias");
        assertEquals(2, layoutReplacedByAlias.size());
        // 1st one is removed version, as it already existed in the item multimap
        ResourceItem replacedByAlias = layoutReplacedByAlias.get(0);
        assertTrue(replacedByAlias.isWritten());
        assertFalse(replacedByAlias.isTouched());
        assertTrue(replacedByAlias.isRemoved());
        assertNull(replacedByAlias.getValue());
        assertEquals("file_replaced_by_alias.xml", replacedByAlias.getSource().getFile().getName());
        // 2nd version is the new one
        replacedByAlias = layoutReplacedByAlias.get(1);
        assertFalse(replacedByAlias.isWritten());
        assertTrue(replacedByAlias.isTouched());
        assertFalse(replacedByAlias.isRemoved());
        assertNotNull(replacedByAlias.getValue());
        assertEquals("values.xml", replacedByAlias.getSource().getFile().getName());

        // check alias_replaced_by_file has 2 version, 2nd is TOUCHED, and contains a Node
        List<ResourceItem> layoutReplacedByFile = mergedMap.get("layout/alias_replaced_by_file");
        // 1st one is removed version, as it already existed in the item multimap
        assertEquals(2, layoutReplacedByFile.size());
        ResourceItem replacedByFile = layoutReplacedByFile.get(0);
        assertTrue(replacedByFile.isWritten());
        assertFalse(replacedByFile.isTouched());
        assertTrue(replacedByFile.isRemoved());
        assertNotNull(replacedByFile.getValue());
        assertEquals("values.xml", replacedByFile.getSource().getFile().getName());
        // 2nd version is the new one
        replacedByFile = layoutReplacedByFile.get(1);
        assertFalse(replacedByFile.isWritten());
        assertTrue(replacedByFile.isTouched());
        assertFalse(replacedByFile.isRemoved());
        assertNull(replacedByFile.getValue());
        assertEquals("alias_replaced_by_file.xml", replacedByFile.getSource().getFile().getName());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        MergedResourceWriter writer = new MergedResourceWriter(resFolder, null /*aaptRunner*/);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // deleted layout/file_replaced_by_alias.xml
        assertFalse(new File(resFolder, "layout" + File.separator + "file_replaced_by_alias.xml")
                .isFile());
        // new file layout/alias_replaced_by_file.xml
        assertTrue(new File(resFolder, "layout" + File.separator + "alias_replaced_by_file.xml")
                .isFile());

        // quick load of the values file
        File valuesFile = new File(resFolder, "values" + File.separator + "values.xml");
        assertTrue(valuesFile.isFile());
        String content = Files.toString(valuesFile, Charsets.UTF_8);
        assertTrue(content.contains("name=\"file_replaced_by_alias\""));
        assertFalse(content.contains("name=\"alias_replaced_by_file\""));
    }

    public void testCheckValidUpdate() throws Exception {
        // first merger
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    ("/main/res1"), ("/main/res2") },
                new String[] { "overlay", ("/overlay/res1"), ("/overlay/res2") },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][] {
                new String[] { "main",    ("/main/res2"), ("/main/res1") },
                new String[] { "overlay", ("/overlay/res1"), ("/overlay/res2") },
        });

        assertTrue(merger1.checkValidUpdate(merger2.getDataSets()));

        // write merger1 on disk to test writing empty ResourceSets.
        File folder = Files.createTempDir();
        merger1.writeBlobTo(folder,
                new MergedResourceWriter(Files.createTempDir(), null /*aaptRunner*/));

        // reload it
        ResourceMerger loadedMerger = new ResourceMerger();
        loadedMerger.loadFromBlob(folder, true /*incrementalState*/);

        String expected = merger1.toString();
        String actual = loadedMerger.toString();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            expected = expected.replaceAll(Pattern.quote(File.separator), "/").
                                replaceAll("[A-Z]:/", "/");
            actual = actual.replaceAll(Pattern.quote(File.separator), "/").
                            replaceAll("[A-Z]:/", "/");
            assertEquals("Actual: " + actual + "\nExpected: " + expected, expected, actual);
        } else {
            assertTrue("Actual: " + actual + "\nExpected: " + expected,
                       loadedMerger.checkValidUpdate(merger1.getDataSets()));
        }
    }

    public void testUpdateWithRemovedOverlay() throws Exception {
        // Test with removed overlay
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getDataSets()));
    }

    public void testUpdateWithReplacedOverlays() throws Exception {
        // Test with different overlays
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
                new String[] { "overlay2", "/overlay2/res1", "/overlay2/res2" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getDataSets()));
    }

    public void testUpdateWithReorderedOverlays() throws Exception {
        // Test with different overlays
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay1", "/overlay1/res1", "/overlay1/res2" },
                new String[] { "overlay2", "/overlay2/res1", "/overlay2/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
                new String[] { "overlay2", "/overlay2/res1", "/overlay2/res2" },
                new String[] { "overlay1", "/overlay1/res1", "/overlay1/res2" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getDataSets()));
    }

    public void testUpdateWithRemovedSourceFile() throws Exception {
        // Test with different source files
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][]{
                new String[]{"main", "/main/res1"},
        });

        assertFalse(merger1.checkValidUpdate(merger2.getDataSets()));
    }

    public void testChangedIgnoredFile() throws Exception {
        ResourceSet res = ResourceSetTest.getBaseResourceSet();

        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.addDataSet(res);

        File root = TestUtils.getRoot("resources", "baseSet");
        File changedCVSFoo = new File(root, "CVS/foo.txt");
        FileValidity<ResourceSet> fileValidity = resourceMerger.findDataSetContaining(
                changedCVSFoo);

        assertEquals(FileValidity.FileStatus.IGNORED_FILE, fileValidity.status);
    }

    public void testIncDataForRemovedFile() throws Exception {
        File root = TestUtils.getCanonicalRoot("resources", "removedFile");
        File fakeBlobRoot = getMergedBlobFolder(root);

        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/);
        checkSourceFolders(resourceMerger);

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(1, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File resBase = new File(root, "res");
        File resDrawable = new File(resBase, ResourceFolderType.DRAWABLE.getName());

        // removed file
        File resIconRemoved = new File(resDrawable, "removed.png");
        mainSet.updateWith(resBase, resIconRemoved, FileStatus.REMOVED, logger);
        checkLogger(logger);

        // validate for duplicates
        resourceMerger.validateDataSets();

        // check the content.
        ListMultimap<String, ResourceItem> mergedMap = resourceMerger.getDataMap();

        // check layout/main is unchanged
        List<ResourceItem> removedIcon = mergedMap.get("drawable/removed");
        assertEquals(1, removedIcon.size());
        assertTrue(removedIcon.get(0).isRemoved());
        assertTrue(removedIcon.get(0).isWritten());
        assertFalse(removedIcon.get(0).isTouched());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File outFolder = getFolderCopy(new File(root, "out"));

        // write the content of the resource merger.
        MergedResourceWriter writer = new MergedResourceWriter(outFolder, null /*aaptRunner*/);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        File outDrawableFolder = new File(outFolder, ResourceFolderType.DRAWABLE.getName());

        // check the files are correct
        assertFalse(new File(outDrawableFolder, "removed.png").isFile());
        assertTrue(new File(outDrawableFolder, "icon.png").isFile());

        // now write the blob
        File outBlobFolder = Files.createTempDir();
        resourceMerger.writeBlobTo(outBlobFolder, writer);

        // check the removed icon is not present.
        ResourceMerger resourceMerger2 = new ResourceMerger();
        resourceMerger2.loadFromBlob(outBlobFolder, true /*incrementalState*/);

        mergedMap = resourceMerger2.getDataMap();
        removedIcon = mergedMap.get("drawable/removed");
        assertTrue(removedIcon.isEmpty());
    }

    /**
     * Creates a fake merge with given sets.
     *
     * the data is an array of sets.
     *
     * Each set is [ setName, folder1, folder2, ...]
     *
     * @param data
     * @return
     */
    private static ResourceMerger createMerger(String[][] data) {
        ResourceMerger merger = new ResourceMerger();
        for (String[] setData : data) {
            ResourceSet set = new ResourceSet(setData[0]);
            merger.addDataSet(set);
            for (int i = 1, n = setData.length; i < n; i++) {
                set.addSource(new File(setData[i]));
            }
        }

        return merger;
    }

    private static ResourceMerger getResourceMerger()
            throws DuplicateDataException, IOException {
        File root = TestUtils.getRoot("resources", "baseMerge");

        ResourceSet res = ResourceSetTest.getBaseResourceSet();

        RecordingLogger logger = new RecordingLogger();

        ResourceSet overlay = new ResourceSet("overlay");
        overlay.addSource(new File(root, "overlay"));
        overlay.loadFromFiles(logger);

        checkLogger(logger);

        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.addDataSet(res);
        resourceMerger.addDataSet(overlay);

        return resourceMerger;
    }

    private static File getWrittenResources() throws DuplicateDataException, IOException,
            MergeConsumer.ConsumerException {
        ResourceMerger resourceMerger = getResourceMerger();

        File folder = Files.createTempDir();

        MergedResourceWriter writer = new MergedResourceWriter(folder, null /*aaptRunner*/);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        return folder;
    }

    private File getIncMergeRoot(String name) throws IOException {
        File root = TestUtils.getCanonicalRoot("resources", "incMergeData");
        return new File(root, name);
    }

    private static File getFolderCopy(File folder) throws IOException {
        File dest = Files.createTempDir();
        copyFolder(folder, dest);
        return dest;
    }

    private static void copyFolder(File from, File to) throws IOException {
        if (from.isFile()) {
            Files.copy(from, to);
        } else if (from.isDirectory()) {
            if (!to.exists()) {
                to.mkdirs();
            }

            File[] children = from.listFiles();
            if (children != null) {
                for (File f : children) {
                    copyFolder(f, new File(to, f.getName()));
                }
            }
        }
    }

    private static Map<String, String> quickStringOnlyValueFileParser(File file)
            throws IOException {
        Map<String, String> result = Maps.newHashMap();

        Document document = ValueResourceParser2.parseDocument(file);

        // get the root node
        Node rootNode = document.getDocumentElement();
        if (rootNode == null) {
            return Collections.emptyMap();
        }

        NodeList nodes = rootNode.getChildNodes();

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (node.getNodeName().equals(SdkConstants.TAG_EAT_COMMENT)) {
                continue;
            }

            ResourceType type = ValueResourceParser2.getType(node);
            if (type != ResourceType.STRING) {
                throw new IllegalArgumentException("Only String resources supported.");
            }
            String name = ValueResourceParser2.getName(node);

            String value = null;

            NodeList nodeList = node.getChildNodes();
            nodeLoop: for (int ii = 0, nn = nodes.getLength(); ii < nn; ii++) {
                Node subNode = nodeList.item(ii);

                switch (subNode.getNodeType()) {
                    case Node.COMMENT_NODE:
                        break;
                    case Node.TEXT_NODE:
                        value = subNode.getNodeValue().trim(); // TODO: remove trim.
                        break nodeLoop;
                    case Node.ELEMENT_NODE:
                        break;
                }
            }

            result.put(name, value != null ? value : "");
        }

        return result;
    }

    public void testAppendedSourceComment() throws Exception {
        ResourceMerger merger = getResourceMerger();
        RecordingLogger logger =  new RecordingLogger();
        File folder = getWrittenResources();
        ResourceSet writtenSet = new ResourceSet("unused");
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);
        compareResourceMaps(merger, writtenSet, false /*full compare*/);
        checkLogger(logger);

        File layout = new File(folder, FD_RES_LAYOUT + File.separator + "main.xml");
        assertTrue(layout.exists());
        String layoutXml = Files.toString(layout, Charsets.UTF_8);
        assertTrue(layoutXml.contains("main.xml")); // in <!-- From: /full/path/to/main.xml -->
        int index = layoutXml.indexOf("From: ");
        assertTrue(index != -1);
        String path = layoutXml.substring(index + 6, layoutXml.indexOf(' ', index + 6));
        File file = SdkUtils.urlToFile(path);
        assertTrue(path, file.exists());
        assertFalse(Arrays.equals(Files.toByteArray(file), Files.toByteArray(layout)));

        // Also make sure .png files were NOT modified
        File root = TestUtils.getRoot("resources", "baseMerge");
        assertNotNull(root);
        File original = new File(root,
                "overlay/drawable-ldpi/icon.png".replace('/', File.separatorChar));
        File copied = new File(folder, FD_RES_DRAWABLE + File.separator + "icon.png");
        assertTrue(Arrays.equals(Files.toByteArray(original), Files.toByteArray(copied)));
    }
}
