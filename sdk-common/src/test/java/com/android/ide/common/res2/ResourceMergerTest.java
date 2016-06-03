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
import static com.android.SdkConstants.TAG_ATTR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public class ResourceMergerTest extends BaseTestCase {

    @Mock
    ResourcePreprocessor mPreprocessor;

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMergeWithNormalizationByCount() throws Exception {
        ResourceMerger merger = getResourceMerger();

        assertEquals(36, merger.size());
    }

    @Test
    public void testMergedResourcesWithNormalizationByName() throws Exception {
        ResourceMerger merger = getResourceMerger();

        verifyResourceExists(merger,
                "drawable/icon",
                "drawable-ldpi-v4/icon",
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
                "string/xliff_with_carriage_return",
                "string/styled_string",
                "string/two",
                "string/many",
                "style/style",
                "array/string_array",
                "array/integer_array",
                "array/my_colors",
                "attr/dimen_attr",
                "attr/string_attr",
                "attr/enum_attr",
                "attr/flag_attr",
                "attr/blah",
                "attr/blah2",
                "attr/flagAttr",
                "declare-styleable/declare_styleable",
                "dimen/dimen",
                "dimen-sw600dp-v13/offset",
                "id/item_id",
                "integer/integer",
                "plurals/plurals",
                "plurals/plurals_with_bad_quantity"
        );
    }

    private static String getPlatformPath(String path) {
        return path.replace('/', File.separatorChar);
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testMergeWrite() throws Exception {
        ResourceMerger merger = getResourceMerger();
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused", null);
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        // compare the two maps, but not using the full map as the set loaded from the output
        // won't contains all versions of each ResourceItem item.
        compareResourceMaps(merger, writtenSet, false /*full compare*/);
        checkLogger(logger);
    }

    @Test
    public void testXliffString() throws Exception {
        ResourceMerger merger = getResourceMerger();

        // check the result of the load
        List<ResourceItem> values = merger.getDataMap().get("string/xliff_string");

        assertEquals(1, values.size());
        ResourceItem string = values.get(0);

        // Even though the content is
        //     <xliff:g id="firstName">%1$s</xliff:g> <xliff:g id="lastName">%2$s</xliff:g>
        // The valueText is going to skip the <g> node so we skip them from the comparison.
        // What matters here is that the whitespaces are kept.
        assertEquals("Loaded String in merger",
                "%1$s %2$s",
                string.getValueText());

        File folder = getWrittenResources();

        RecordingLogger logger =  new RecordingLogger();
        ResourceSet writtenSet = new ResourceSet("unused", null);
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        values = writtenSet.getDataMap().get("string/xliff_string");

        assertEquals(1, values.size());
        string = values.get(0);

        // Even though the content is
        //     <xliff:g id="firstName">%1$s</xliff:g> <xliff:g id="lastName">%2$s</xliff:g>
        // The valueText is going to skip the <g> node so we skip them from the comparison.
        // What matters here is that the whitespaces are kept.
        assertEquals("Rewritten String through merger",
                "%1$s %2$s",
                string.getValueText());
    }

    @Test
    public void testXliffStringWithCarriageReturn() throws Exception {
        ResourceMerger merger = getResourceMerger();

        // check the result of the load
        List<ResourceItem> values = merger.getDataMap().get("string/xliff_with_carriage_return");

        assertEquals(1, values.size());
        ResourceItem string = values.get(0);

        // Even though the content has xliff nodes
        // The valueText is going to skip the <g> node so we skip them from the comparison.
        // What matters here is that the whitespaces are kept.
        String value = string.getValueText();
        assertEquals("Loaded String in merger",
                "This is should be followed by whitespace:\n        %1$s",
                value);

        File folder = getWrittenResources();

        RecordingLogger logger =  new RecordingLogger();
        ResourceSet writtenSet = new ResourceSet("unused", null);
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        values = writtenSet.getDataMap().get("string/xliff_with_carriage_return");

        assertEquals(1, values.size());
        string = values.get(0);

        // Even though the content has xliff nodes
        // The valueText is going to skip the <g> node so we skip them from the comparison.
        // What matters here is that the whitespaces are kept.
        String newValue = string.getValueText();
        assertEquals("Rewritten String through merger",
                value,
                newValue);
    }

    @Test
    public void testNotMergedAttr() throws Exception {
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused", null);
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        List<ResourceItem> items = writtenSet.getDataMap().get("attr/blah");
        assertEquals(1, items.size());
        assertTrue(items.get(0).getIgnoredFromDiskMerge());

        checkLogger(logger);
    }

    @Test
    public void testMergedAttr() throws Exception {
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused", null);
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles(logger);

        List<ResourceItem> items = writtenSet.getDataMap().get("attr/blah2");
        assertEquals(1, items.size());
        assertFalse(items.get(0).getIgnoredFromDiskMerge());

        checkLogger(logger);
    }

    @Test
    public void testNotMergedAttrFromMerge() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = TestUtils.createTempDirDeletedOnExit();
        merger.writeBlobTo(folder, getConsumer(), false);

        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        // check that attr/blah is ignoredFromDiskMerge.
        List<ResourceItem> items = loadedMerger.getDataSets().get(0).getDataMap().get("attr/blah");
        assertEquals(1, items.size());
        assertTrue(items.get(0).getIgnoredFromDiskMerge());
    }

    @Test
    public void testNotMergedSingleFileItem() throws Exception {
        ResourceMerger merger = getResourceMerger();

        List<ResourceItem> items = merger.getDataSets().get(0).getDataMap().get("drawable/patch");
        assertEquals(1, items.size());
        assertFalse(items.get(0).getIgnoredFromDiskMerge());

        File folder = TestUtils.createTempDirDeletedOnExit();
        merger.writeBlobTo(folder, getConsumer(), false);

        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        // drawable/patch should survive blob writing / loading
        List<ResourceItem> loadedItems = loadedMerger.getDataSets().get(0).getDataMap().get("drawable/patch");
        assertEquals(1, loadedItems.size());

        // Now mark the item ignored and try write + load again
        loadedItems.get(0).setIgnoredFromDiskMerge(true);
        folder = TestUtils.createTempDirDeletedOnExit();
        loadedMerger.writeBlobTo(folder, getConsumer(), false);

        ResourceMerger loadedMerger2 = new ResourceMerger(0);
        assertTrue(loadedMerger2.loadFromBlob(folder, true /*incrementalState*/));

        List<ResourceItem> loadedItems2 = loadedMerger2.getDataSets().get(0).getDataMap().get("drawable/patch");
        assertEquals(0, loadedItems2.size());
    }

    @Test
    public void testWrittenDeclareStyleable() throws Exception {
        RecordingLogger logger =  new RecordingLogger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused", null);
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

    @Test
    public void testMergeBlob() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = TestUtils.createTempDirDeletedOnExit();
        merger.writeBlobTo(folder, getConsumer(), false);

        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        compareResourceMaps(merger, loadedMerger, true /*full compare*/);

        // Also check that some of the node values are preserved.
        List<ResourceItem> fromOrigValue = merger.getDataMap().get("string/xliff_with_carriage_return");
        assertEquals(1, fromOrigValue.size());
        ResourceItem fromOrigString = fromOrigValue.get(0);
        assertEquals("Original String in merger",
                     "This is should be followed by whitespace:\n        %1$s",
                     fromOrigString.getValueText());

        List<ResourceItem> fromLoadedValues = loadedMerger.getDataMap().get("string/xliff_with_carriage_return");
        assertEquals(1, fromLoadedValues.size());
        ResourceItem fromLoadedString = fromLoadedValues.get(0);
        assertEquals("Loaded String in merger",
                     "This is should be followed by whitespace:\n        %1$s",
                     fromLoadedString.getValueText());
    }

    @Test
    public void testMergeIdGeneratingResources() throws Exception {
        // Test loading and writing Id resources.  Test loading a single file instead of all.
        File root = TestUtils.getRoot("resources", "idGenerating");
        ResourceSet resourceSet = new ResourceSet("idResources", null);
        resourceSet.addSource(root);
        ResourceMerger merger = new ResourceMerger(0);
        merger.addDataSet(resourceSet);

        RecordingLogger logger = new RecordingLogger();

        File layoutDir = new File(root, "layout");
        File layoutFile = new File(layoutDir, "layout_for_id_scan.xml");
        resourceSet.setShouldParseResourceIds(true);
        final ResourceFile parsedFile = resourceSet.loadFile(root, layoutFile, logger);
        assertNotNull(parsedFile);
        assertTrue(parsedFile.getFile().equals(layoutFile));
        assertEquals("", parsedFile.getQualifiers());
        assertEquals(12, parsedFile.getItems().size());
        Collection<ResourceItem> layoutItems = Collections2.filter(parsedFile.getItems(), new Predicate<ResourceItem>() {
            @Override
            public boolean apply(ResourceItem input) {
                return input.getType() == ResourceType.LAYOUT &&
                       input.getName().equals("layout_for_id_scan") &&
                       input.getSource() != null &&
                       input.getSource().equals(parsedFile);
            }
        });
        assertEquals(1, layoutItems.size());
        // Also check that the layout item's ResourceValue makes sense.
        ResourceItem layoutItem = Iterables.getFirst(layoutItems, null);
        assertNotNull(layoutItem);
        ResourceValue layoutValue = layoutItem.getResourceValue(false);
        assertNotNull(layoutValue);
        assertEquals(layoutFile.getAbsolutePath(), layoutValue.getValue());
        Collection<ResourceItem> idItems = Collections2.filter(parsedFile.getItems(), new Predicate<ResourceItem>() {
            @Override
            public boolean apply(ResourceItem input) {
                return input.getType() == ResourceType.ID &&
                       input.getSource() != null &&
                       input.getSource().equals(parsedFile);
            }
        });
        assertEquals(11, idItems.size());

        File folder = TestUtils.createTempDirDeletedOnExit();
        folder.deleteOnExit();
        merger.writeBlobTo(folder, getConsumer(), false);

        // reload it
        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        compareResourceMaps(merger, loadedMerger, true /*full compare*/);
        // Also check that the layout item's ResourceValue makes sense after reload.
        List<ResourceItem> loadedLayoutItems = loadedMerger.getDataMap().get("layout/layout_for_id_scan");
        assertEquals(1, loadedLayoutItems.size());
        layoutItem = Iterables.getFirst(loadedLayoutItems, null);
        assertNotNull(layoutItem);
        layoutValue = layoutItem.getResourceValue(false);
        assertNotNull(layoutValue);
        assertEquals(layoutFile.getAbsolutePath(), layoutValue.getValue());

        // Check that the ID item's ResourceValue is nothing of consequence.
        List<ResourceItem> loadedIdItems = loadedMerger.getDataMap().get("id/title_refresh_progress");
        assertEquals(1, loadedIdItems.size());
        ResourceItem idItem = Iterables.getFirst(loadedIdItems, null);
        assertNotNull(idItem);
        ResourceValue idValue = idItem.getResourceValue(false);
        assertNotNull(idValue);
        assertTrue(Strings.isNullOrEmpty(idValue.getValue()));

        checkLogger(logger);
    }

    @Test
    public void testScanForIdDrawableXmlMixedWithBinary() throws Exception {
        // Test that in a drawable directory with both XML and PNG, we can pick up IDs
        // without being confused about the PNG.
        File srcDrawables = FileUtils.join(TestUtils.getRoot("resources", "idGenerating"), "drawable-v21");
        File copiedRoot = TestUtils.createTempDirDeletedOnExit();
        copiedRoot.deleteOnExit();
        File copiedDrawables = FileUtils.join(copiedRoot, "drawable-v21");
        copyFolder(srcDrawables, copiedDrawables);

        File srcPng = FileUtils.join(TestUtils.getRoot("resources", "baseSet"), "drawable", "icon.png");
        FileUtils.copyFileToDirectory(srcPng, copiedDrawables);
        assertTrue(FileUtils.join(copiedDrawables, "drawable_for_id_scan.xml").exists());

        ResourceSet resourceSet = new ResourceSet("idResources", null);
        resourceSet.addSource(copiedRoot);
        resourceSet.setShouldParseResourceIds(true);

        RecordingLogger logger = new RecordingLogger();
        resourceSet.loadFromFiles(logger);
        List<ResourceItem> iconRes = resourceSet.getDataMap().get("drawable-v21/icon");
        assertEquals(1, iconRes.size());
        List<ResourceItem> drawableRes = resourceSet.getDataMap().get("drawable-v21/drawable_for_id_scan");
        assertEquals(1, drawableRes.size());
        List<ResourceItem> focusedId= resourceSet.getDataMap().get("id-v21/focused");
        assertEquals(1, focusedId.size());

        checkLogger(logger);
    }

    @Test
    public void testDontNormalizeQualifiers() throws Exception {
        File root = TestUtils.getRoot("resources", "idGenerating");
        File copiedRoot = getFolderCopy(root);
        copiedRoot.deleteOnExit();
        // Add some qualifiers to directory before loading.
        File layoutDirWithQualifiers = new File(copiedRoot, "layout-xlarge-land");
        FileUtils.renameTo(new File(copiedRoot, "layout"), layoutDirWithQualifiers);
        File layoutFile = new File(layoutDirWithQualifiers, "layout_for_id_scan.xml");

        ResourceSet resourceSet = new ResourceSet("idResources", null);
        resourceSet.addSource(copiedRoot);
        RecordingLogger logger = new RecordingLogger();

        // Try first with normalization, which tacks on a -v4 qualifier.
        ResourceFile parsedFile = resourceSet.loadFile(copiedRoot, layoutFile, logger);
        assertNotNull(parsedFile);
        assertEquals("xlarge-land-v4", parsedFile.getQualifiers());

        // Now try without normalization.
        resourceSet.setDontNormalizeQualifiers(true);
        parsedFile = resourceSet.loadFile(copiedRoot, layoutFile, logger);
        assertNotNull(parsedFile);
        assertEquals("xlarge-land", parsedFile.getQualifiers());

        checkLogger(logger);
    }

    @Test
    public void testWriteAndReadBlobWithTimestamps() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = TestUtils.createTempDirDeletedOnExit();
        folder.deleteOnExit();
        merger.writeBlobToWithTimestamps(folder, getConsumer());

        // new merger to read the blob
        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        compareResourceMaps(merger, loadedMerger, true /*full compare*/);
    }

    @Test
    public void testWriteEditAndReadBlobWithTimestamps() throws Exception {
        Pair<ResourceMerger, File> pair = getResourceMergerBackedByTempFiles();
        ResourceMerger merger = pair.getFirst();
        File tempDir = pair.getSecond();

        // Check existence of a values resource.
        String stringKey = "string/basic_string";
        assertNotNull(merger.getDataMap().get(stringKey));
        assertFalse(merger.getDataMap().get(stringKey).isEmpty());
        ResourceFile resourceFile = merger.getDataMap().get(stringKey).get(0).getSource();
        assertNotNull(resourceFile);
        assertTrue(resourceFile.getFile().getName().equals("values.xml"));

        // Write blobs with and without timestamps.
        File folderWithTimestamps = TestUtils.createTempDirDeletedOnExit();
        merger.writeBlobToWithTimestamps(folderWithTimestamps, getConsumer());

        File folderWithoutTimestamps = TestUtils.createTempDirDeletedOnExit();
        merger.writeBlobTo(folderWithoutTimestamps, getConsumer(), false);

        // Simply touch the values file with a sufficiently later timestamp.
        File subFile = new File(tempDir, FileUtils.join("values", "values.xml"));
        long oldLastModified = subFile.lastModified();
        long twoSeconds = 2000;
        if (!subFile.setLastModified(oldLastModified + twoSeconds)) {
            // Not supported on this platform
            return;
        }

        // Load with and without timestamps to see values omitted due to modification (or not omitted).
        ResourceMerger loadedWithTimestamps = new ResourceMerger(0);
        assertTrue(loadedWithTimestamps.loadFromBlob(folderWithTimestamps, true /*incrementalState*/));

        // omitted
        assertNotNull(loadedWithTimestamps.getDataMap().get(stringKey));
        assertTrue(loadedWithTimestamps.getDataMap().get(stringKey).isEmpty());

        ResourceMerger loadedWithoutTimestamps = new ResourceMerger(0);
        assertTrue(loadedWithoutTimestamps.loadFromBlob(folderWithoutTimestamps, true /*incrementalState*/));

        // not omitted
        assertNotNull(loadedWithoutTimestamps.getDataMap().get(stringKey));
        assertFalse(loadedWithoutTimestamps.getDataMap().get(stringKey).isEmpty());
        resourceFile = loadedWithoutTimestamps.getDataMap().get(stringKey).get(0).getSource();
        assertNotNull(resourceFile);
        assertTrue(resourceFile.getFile().getName().equals("values.xml"));
    }

    @Test
    public void testWriteDeleteAndReadBlobWithTimestamps() throws Exception {
        Pair<ResourceMerger, File> pair = getResourceMergerBackedByTempFiles();
        ResourceMerger merger = pair.getFirst();
        File tempDir = pair.getSecond();

        File folder = TestUtils.createTempDirDeletedOnExit();
        merger.writeBlobToWithTimestamps(folder, getConsumer());

        String stringKey = "string/basic_string";
        assertNotNull(merger.getDataMap().get(stringKey));
        assertFalse(merger.getDataMap().get(stringKey).isEmpty());
        ResourceFile resourceFile = merger.getDataMap().get(stringKey).get(0).getSource();
        assertNotNull(resourceFile);
        assertTrue(resourceFile.getFile().getName().equals("values.xml"));

        File subFile = new File(tempDir, FileUtils.join("values", "values.xml"));
        assertTrue(subFile.delete());

        // new merger to read the blob
        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        assertNotNull(loadedMerger.getDataMap().get(stringKey));
        assertTrue(loadedMerger.getDataMap().get(stringKey).isEmpty());
    }

    /**
     * Tests the path replacement in the merger.xml file loaded from testData/
     * @throws Exception
     */
    @Test
    public void testLoadingTestPathReplacement() throws Exception {
        File root = TestUtils.getRoot("resources", "baseMerge");
        File fakeRoot = getMergedBlobFolder(root);

        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/));
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

    @Test
    public void testUpdateWithBasicFiles() throws Exception {
        File root = getIncMergeRoot("basicFiles");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/));
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
        List<ResourceItem> drawableHdpiNewAlternate = mergedMap.get("drawable-hdpi-v4/new_alternate");
        assertEquals(1, drawableHdpiNewAlternate.size());
        ResourceItem newAlternate = drawableHdpiNewAlternate.get(0);
        assertEquals(overlayDrawableHdpiNewAlternate, newAlternate.getSource().getFile());
        assertFalse(newAlternate.isWritten());
        assertTrue(newAlternate.isTouched());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));
        resFolder.deleteOnExit();

        File mergeLogFolder = TestUtils.createTempDirDeletedOnExit();
        mergeLogFolder.deleteOnExit();

        // write the content of the resource merger.
        MergedResourceWriter writer = MergedResourceWriter.createWriterWithoutPngCruncher(
                resFolder,
                null /*publicFile*/,
                mergeLogFolder,
                mPreprocessor,
                mTemporaryFolder.getRoot());
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
        checkImageColor(new File(resFolder, "drawable-hdpi-v4" + File.separator + "new_alternate.png"),
                (int) 0xFF00FF00);
        assertFalse(new File(resFolder, "drawable-ldpi-v4" + File.separator + "removed.png").isFile());

        // Blame log sanity check
        MergingLog mergingLog = new MergingLog(mergeLogFolder);

        SourceFile original = mergingLog.find(
                new SourceFile(new File(resFolder, "drawable" + File.separator + "touched.png")));
        assertTrue(original.getSourceFile().getAbsolutePath().endsWith(
                "basicFiles/main/drawable/touched.png".replace('/', File.separatorChar)));
    }

    @Test
    public void testUpdateWithBasicValues() throws Exception {
        File root = getIncMergeRoot("basicValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/));
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


        File mergeLogFolder = TestUtils.createTempDirDeletedOnExit();
        mergeLogFolder.deleteOnExit();

        // write the content of the resource merger.
        MergedResourceWriter writer = MergedResourceWriter.createWriterWithoutPngCruncher(
                resFolder,
                null /*publicFile*/,
                mergeLogFolder,
                mPreprocessor,
                mTemporaryFolder.getRoot());
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // Check the content.
        // values/values.xml
        Map<String, String> map = quickStringOnlyValueFileParser(
                new File(resFolder, "values" + File.separator + "values.xml"));
        assertEquals("untouched", map.get("untouched"));
        assertEquals("touched", map.get("touched"));
        assertEquals("new_overlay", map.get("new_overlay"));

        // values-fr/values-fr.xml
        map = quickStringOnlyValueFileParser(
                new File(resFolder, "values-fr" + File.separator + "values-fr.xml"));
        assertEquals("new_alternate", map.get("new_alternate"));

        // deleted values-en/values-en.xml
        assertFalse(new File(resFolder, "values-en" + File.separator + "values-en.xml").isFile());

        // Blame log sanity check.
        MergingLog mergingLog = new MergingLog(mergeLogFolder);

        SourceFile destFile =
                mergingLog.destinationFor(
                        new SourceFile(overlayValuesNew));

        SourceFilePosition original =
                mergingLog.find(
                        new SourceFilePosition(destFile, new SourcePosition(2,5,-1)));

        assertEquals(new SourcePosition(2, 4, 55, 2, 51, 102), original.getPosition());
        assertTrue(original.getFile().getSourceFile().getAbsolutePath().endsWith(
                "basicValues/overlay/values/values.xml".replace('/', File.separatorChar)));

    }

    @Test
    public void testUpdateWithBasicValues2() throws Exception {
        File root = getIncMergeRoot("basicValues2");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/));
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
        MergedResourceWriter writer = getConsumer(resFolder);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // Check the content.
        // values/values.xml
        Map<String, String> map = quickStringOnlyValueFileParser(
                new File(resFolder, "values" + File.separator + "values.xml"));
        assertEquals("untouched", map.get("untouched"));
        assertEquals("untouched", map.get("removed_overlay"));
    }

    @Test
    public void testUpdateWithFilesVsValues() throws Exception {
        File root = getIncMergeRoot("filesVsValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeRoot, true /*incrementalState*/));
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
        MergedResourceWriter writer = getConsumer(resFolder);
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

    @Test
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
        File folder = TestUtils.createTempDirDeletedOnExit();
        merger1.writeBlobTo(folder, getConsumer(), false);

        // reload it
        ResourceMerger loadedMerger = new ResourceMerger(0);
        assertTrue(loadedMerger.loadFromBlob(folder, true /*incrementalState*/));

        String expected = merger1.toString();
        String actual = loadedMerger.toString();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            expected = expected.replace(File.separatorChar, '/').
                                replaceAll("[A-Z]:/", "/");
            actual = actual.replace(File.separatorChar, '/').
                            replaceAll("[A-Z]:/", "/");
            assertEquals("Actual: " + actual + "\nExpected: " + expected, expected, actual);
        } else {
            assertTrue("Actual: " + actual + "\nExpected: " + expected,
                       loadedMerger.checkValidUpdate(merger1.getDataSets()));
        }
    }

    @Test
    public void testUpdateWithRemovedOverlay() throws Exception {
        // Test with removed overlay
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][]{
                new String[]{"main", "/main/res2", "/main/res1"},
        });

        assertFalse(merger1.checkValidUpdate(merger2.getDataSets()));
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testChangedIgnoredFile() throws Exception {
        ResourceSet res = ResourceSetTest.getBaseResourceSet();

        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.addDataSet(res);

        File root = TestUtils.getRoot("resources", "baseSet");
        File changedCVSFoo = new File(root, "CVS/foo.txt");
        FileValidity<ResourceSet> fileValidity = resourceMerger.findDataSetContaining(
                changedCVSFoo);

        assertEquals(FileValidity.FileStatus.IGNORED_FILE, fileValidity.status);
    }

    @Test
    public void testIncDataForRemovedFile() throws Exception {
        File root = TestUtils.getCanonicalRoot("resources", "removedFile");
        File fakeBlobRoot = getMergedBlobFolder(root);

        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/));
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
        MergedResourceWriter writer = getConsumer(outFolder);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        File outDrawableFolder = new File(outFolder, ResourceFolderType.DRAWABLE.getName());

        // check the files are correct
        assertFalse(new File(outDrawableFolder, "removed.png").isFile());
        assertTrue(new File(outDrawableFolder, "icon.png").isFile());

        // now write the blob
        File outBlobFolder = TestUtils.createTempDirDeletedOnExit();
        resourceMerger.writeBlobTo(outBlobFolder, writer, false);

        // check the removed icon is not present.
        ResourceMerger resourceMerger2 = new ResourceMerger(0);
        assertTrue(resourceMerger2.loadFromBlob(outBlobFolder, true /*incrementalState*/));

        mergedMap = resourceMerger2.getDataMap();
        removedIcon = mergedMap.get("drawable/removed");
        assertTrue(removedIcon.isEmpty());
    }

    @Test
    public void testMergedDeclareStyleable() throws Exception {
        File root = TestUtils.getRoot("resources", "declareStyleable");

        // load both base and overlay set
        File baseRoot = new File(root, "base");
        ResourceSet baseSet = new ResourceSet("main", null);
        baseSet.addSource(baseRoot);
        RecordingLogger logger = new RecordingLogger();
        baseSet.loadFromFiles(logger);
        checkLogger(logger);

        File overlayRoot = new File(root, "overlay");
        ResourceSet overlaySet = new ResourceSet("overlay", null);
        overlaySet.addSource(overlayRoot);
        logger = new RecordingLogger();
        overlaySet.loadFromFiles(logger);
        checkLogger(logger);

        // create a merger
        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.addDataSet(baseSet);
        resourceMerger.addDataSet(overlaySet);

        // write the merge result.
        File folder = TestUtils.createTempDirDeletedOnExit();
        folder.deleteOnExit();

        MergedResourceWriter writer = getConsumer(folder);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // load the result as a set.
        ResourceSet mergedSet = new ResourceSet("merged", null);
        mergedSet.addSource(folder);
        logger = new RecordingLogger();
        mergedSet.loadFromFiles(logger);
        checkLogger(logger);

        ListMultimap<String, ResourceItem> map = mergedSet.getDataMap();
        assertEquals(4, map.size());

        List<ResourceItem> items = map.get("declare-styleable/foo");
        assertNotNull(items);
        assertEquals(1, items.size());

        ResourceItem item = items.get(0);
        assertNotNull(item);

        // now we need to look at the item's value (which is the XML).
        // We're looking for 3 attributes.
        List<String> expectedAttrs = Lists.newArrayList("bar", "bar1", "boo");
        Node rootNode = item.getValue();
        assertNotNull(rootNode);
        NodeList sourceNodes = rootNode.getChildNodes();
        for (int i = 0, n = sourceNodes.getLength(); i < n; i++) {
            Node sourceNode = sourceNodes.item(i);

            if (sourceNode.getNodeType() != Node.ELEMENT_NODE ||
                    !TAG_ATTR.equals(sourceNode.getLocalName())) {
                continue;
            }

            Attr attr = (Attr) sourceNode.getAttributes().getNamedItem(ATTR_NAME);
            if (attr == null) {
                continue;
            }

            String attrName = attr.getValue();

            assertTrue("Check expected " + attrName, expectedAttrs.contains(attrName));
            expectedAttrs.remove(attrName);
        }

        assertTrue("Check emptiness of " + expectedAttrs.toString(), expectedAttrs.isEmpty());
    }

    @Test
    public void testUnchangedMergedItem() throws Exception {
        // locate the merger file that contains exactly the result of the source folders.
        File root = TestUtils.getRoot("resources", "declareStyleable");
        File fakeBlobRoot = getMergedBlobFolder(root, new File(root, "unchanged_merger.xml"));

        // load a resource merger based on it.
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/));
        checkSourceFolders(resourceMerger);

        // create a fake consumer
        FakeMergeConsumer consumer = new FakeMergeConsumer();

        // do the merge
        resourceMerger.mergeData(consumer, false /*doCleanUp*/);

        // test result of merger.
        assertTrue(consumer.touchedItems.isEmpty());
        assertTrue(consumer.removedItems.isEmpty());
    }

    @Test
    public void testRemovedMergedItem() throws Exception {
        // locate the merger file that contains exactly the result of the source folders.
        File root = TestUtils.getCanonicalRoot("resources", "declareStyleable");
        File fakeBlobRoot = getMergedBlobFolder(root, new File(root, "removed_merger.xml"));

        // load a resource merger based on it.
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/));
        checkSourceFolders(resourceMerger);

        // we know have to tell the merger that the values files have been touched
        // to trigger the removal detection based on the original merger blob.

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File mainRoot = new File(root, "base");
        File mainValues = new File(mainRoot, ResourceFolderType.VALUES.getName());

        // trigger changed file event
        File touchedValueFile = new File(mainValues, "values.xml");
        mainSet.updateWith(mainRoot, touchedValueFile, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // same with overlay set.
        ResourceSet overlaySet = sets.get(1);
        File overlayRoot = new File(root, "overlay");
        File overlayValues = new File(overlayRoot, ResourceFolderType.VALUES.getName());

        // trigger changed file event
        touchedValueFile = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayRoot, touchedValueFile, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // create a fake consumer
        FakeMergeConsumer consumer = new FakeMergeConsumer();

        // do the merge
        resourceMerger.mergeData(consumer, false /*doCleanUp*/);

        // test result of merger.
        assertTrue(consumer.touchedItems.isEmpty());
        assertEquals(1, consumer.removedItems.size());
    }

    @Test
    public void testTouchedMergedItem() throws Exception {
        // locate the merger file that contains exactly the result of the source folders.
        File root = TestUtils.getCanonicalRoot("resources", "declareStyleable");
        File fakeBlobRoot = getMergedBlobFolder(root, new File(root, "touched_merger.xml"));

        // load a resource merger based on it.
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/));
        checkSourceFolders(resourceMerger);

        // we know have to tell the merger that the values files have been touched
        // to trigger the removal detection based on the original merger blob.

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File mainRoot = new File(root, "base");
        File mainValues = new File(mainRoot, ResourceFolderType.VALUES.getName());

        // trigger changed file event
        File touchedValueFile = new File(mainValues, "values.xml");
        mainSet.updateWith(mainRoot, touchedValueFile, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // create a fake consumer
        FakeMergeConsumer consumer = new FakeMergeConsumer();

        // do the merge
        resourceMerger.mergeData(consumer, false /*doCleanUp*/);

        // test result of merger.
        assertEquals(1, consumer.touchedItems.size());
        assertTrue(consumer.removedItems.isEmpty());
    }

    @Test
    public void testTouchedNoDiffMergedItem() throws Exception {
        // locate the merger file that contains exactly the result of the source folders.
        File root = TestUtils.getCanonicalRoot("resources", "declareStyleable");
        File fakeBlobRoot = getMergedBlobFolder(root, new File(root, "touched_nodiff_merger.xml"));

        // load a resource merger based on it.
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/));
        checkSourceFolders(resourceMerger);

        // we know have to tell the merger that the values files have been touched
        // to trigger the removal detection based on the original merger blob.

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the overlay set
        ResourceSet overlaySet = sets.get(1);
        File overlayRoot = new File(root, "overlay");
        File overlayValues = new File(overlayRoot, ResourceFolderType.VALUES.getName());

        // trigger changed file event
        File touchedValueFile = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayRoot, touchedValueFile, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // create a fake consumer
        FakeMergeConsumer consumer = new FakeMergeConsumer();

        // do the merge
        resourceMerger.mergeData(consumer, false /*doCleanUp*/);

        // test result of merger.
        assertTrue(consumer.touchedItems.isEmpty());
        assertTrue(consumer.removedItems.isEmpty());
    }

    @Test
    public void testRemovedOtherWithNoNoDiffTouchMergedItem() throws Exception {
        // test that when a non-merged resources is changed/removed, the result of the merge still
        // contain the merged items even if they were touched but had no change.

        // locate the merger file that contains exactly the result of the source folders.
        File root = TestUtils.getCanonicalRoot("resources", "declareStyleable");
        File fakeBlobRoot = getMergedBlobFolder(root, new File(root, "removed_other_merger.xml"));

        // load a resource merger based on it.
        ResourceMerger resourceMerger = new ResourceMerger(0);
        assertTrue(resourceMerger.loadFromBlob(fakeBlobRoot, true /*incrementalState*/));
        checkSourceFolders(resourceMerger);

        // we know have to tell the merger that the values files have been touched
        // to trigger the removal detection based on the original merger blob.

        List<ResourceSet> sets = resourceMerger.getDataSets();
        assertEquals(2, sets.size());

        RecordingLogger logger = new RecordingLogger();

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File mainRoot = new File(root, "base");
        File mainValues = new File(mainRoot, ResourceFolderType.VALUES.getName());

        // trigger changed file event
        File touchedValueFile = new File(mainValues, "values.xml");
        mainSet.updateWith(mainRoot, touchedValueFile, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // same for overlay
        ResourceSet overlaySet = sets.get(1);
        File overlayRoot = new File(root, "overlay");
        File overlayValues = new File(overlayRoot, ResourceFolderType.VALUES.getName());

        // trigger changed file event
        touchedValueFile = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayRoot, touchedValueFile, FileStatus.CHANGED, logger);
        checkLogger(logger);

        // create a fake consumer
        FakeMergeConsumer consumer = new FakeMergeConsumer();

        // do the merge
        resourceMerger.mergeData(consumer, false /*doCleanUp*/);

        // test result of merger.
        // only 3 items added since attr/bar isn't added (declared inline)
        assertEquals(3, consumer.addedItems.size());
        // no touched items
        assertTrue(consumer.touchedItems.isEmpty());
        // one removed string item
        assertEquals(1, consumer.removedItems.size());
    }

    @Test
    public void testStringWhiteSpaces() throws Exception {
        File root = TestUtils.getRoot("resources", "stringWhiteSpaces");

        // load res folder
        ResourceSet baseSet = new ResourceSet("main", null);
        baseSet.addSource(root);
        RecordingLogger logger = new RecordingLogger();
        baseSet.loadFromFiles(logger);
        checkLogger(logger);

        // create a merger
        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.addDataSet(baseSet);

        // write the merge result.
        File folder = TestUtils.createTempDirDeletedOnExit();
        folder.deleteOnExit();

        MergedResourceWriter writer = getConsumer(folder);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        // load the result as a set.
        ResourceSet mergedSet = new ResourceSet("merged", null);
        mergedSet.addSource(folder);
        logger = new RecordingLogger();
        mergedSet.loadFromFiles(logger);
        checkLogger(logger);

        ListMultimap<String, ResourceItem> originalItems = baseSet.getDataMap();
        ListMultimap<String, ResourceItem> mergedItems = mergedSet.getDataMap();

        for (Map.Entry<String, Collection<ResourceItem>> entry : originalItems.asMap().entrySet()) {
            Collection<ResourceItem> originalItemList = entry.getValue();
            Collection<ResourceItem> mergedItemList = mergedItems.asMap().get(entry.getKey());

            // the collection should only have a single items
            assertEquals(1, originalItemList.size());
            assertEquals(1, mergedItemList.size());

            ResourceItem originalItem = originalItemList.iterator().next();
            ResourceItem mergedItem = mergedItemList.iterator().next();

            assertTrue(originalItem.compareValueWith(mergedItem));
        }
    }

    /**
     * Creates a fake merge with given sets.
     *
     * the data is an array of sets.
     *
     * Each set is [ setName, folder1, folder2, ...]
     *
     * @param data the data sets
     * @return the merger
     */
    private static ResourceMerger createMerger(String[][] data) {
        ResourceMerger merger = new ResourceMerger(0);
        for (String[] setData : data) {
            ResourceSet set = new ResourceSet(setData[0], null);
            merger.addDataSet(set);
            for (int i = 1, n = setData.length; i < n; i++) {
                set.addSource(new File(setData[i]));
            }
        }

        return merger;
    }

    private static ResourceMerger getResourceMerger()
            throws MergingException, IOException {
        File root = TestUtils.getRoot("resources", "baseMerge");

        ResourceSet res = ResourceSetTest.getBaseResourceSet();

        RecordingLogger logger = new RecordingLogger();

        ResourceSet overlay = new ResourceSet("overlay", null);
        overlay.addSource(new File(root, "overlay"));
        overlay.loadFromFiles(logger);

        checkLogger(logger);

        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.addDataSet(res);
        resourceMerger.addDataSet(overlay);

        return resourceMerger;
    }

    private static Pair<ResourceMerger, File> getResourceMergerBackedByTempFiles()
      throws MergingException, IOException {
        File srcOverlay = TestUtils.getRoot("resources", "baseMerge", "overlay");

        RecordingLogger logger = new RecordingLogger();
        File testFolder = getFolderCopy(srcOverlay);
        testFolder.deleteOnExit();

        ResourceSet overlay = new ResourceSet("overlay", null);
        overlay.addSource(testFolder);
        overlay.loadFromFiles(logger);

        checkLogger(logger);

        ResourceMerger resourceMerger = new ResourceMerger(0);
        resourceMerger.addDataSet(overlay);

        return Pair.of(resourceMerger, testFolder);
    }

    private File getWrittenResources() throws MergingException, IOException {
        ResourceMerger resourceMerger = getResourceMerger();

        File folder = TestUtils.createTempDirDeletedOnExit();

        MergedResourceWriter writer = getConsumer(folder);
        resourceMerger.mergeData(writer, false /*doCleanUp*/);

        return folder;
    }

    private static File getIncMergeRoot(String name) throws IOException {
        File root = TestUtils.getCanonicalRoot("resources", "incMergeData");
        return new File(root, name);
    }

    private static File getFolderCopy(File folder) throws IOException {
        File dest = TestUtils.createTempDirDeletedOnExit();
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
            throws IOException, MergingException {
        Map<String, String> result = Maps.newHashMap();

        Document document = ValueResourceParser2.parseDocument(file, true);

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

            ResourceType type = ValueResourceParser2.getType(node, file);
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

    @Test
    public void testWritePermission() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = TestUtils.createTempDirDeletedOnExit();
        boolean writable = folder.setWritable(false);
        if (!writable) {
            // Not supported on this platform
            return;
        }
        try {
            merger.writeBlobTo(folder, getConsumer(), false);
        } catch (MergingException e) {
            File file = new File(folder, "merger.xml");
            assertEquals(file.getPath() + ": Error: (Permission denied)",
                    e.getMessage());
            return;
        }
        fail("Exception not thrown as expected");
    }

    @Test
    public void testInvalidFileNames() throws Exception {
        File root = TestUtils.getRoot("resources", "brokenSet5");
        ResourceSet resourceSet = new ResourceSet("brokenSet5", null);
        resourceSet.addSource(root);
        RecordingLogger logger =  new RecordingLogger();

        try {
            resourceSet.loadFromFiles(logger);
        } catch (MergingException e) {
            File file = new File(root, "layout" + File.separator + "ActivityMain.xml");
            file = file.getAbsoluteFile();
            assertEquals(
                    file.getPath() +
                            ": Error: 'A' is not a valid file-based resource name character: "
                            + "File-based resource names must contain only lowercase a-z, 0-9,"
                            + " or underscore",
                    e.getMessage());
            return;
        }
        fail("Expected error");
    }

    @Test
    public void testStricterInvalidFileNames() throws Exception {
        File root = TestUtils.getRoot("resources", "brokenSetDrawableFileName");
        ResourceSet resourceSet = new ResourceSet("brokenSetDrawableFileName", null);
        resourceSet.addSource(root);
        RecordingLogger logger =  new RecordingLogger();

        try {
            resourceSet.loadFromFiles(logger);
        } catch (MergingException e) {
            File file = new File(root, "drawable" + File.separator + "1icon.png");
            file = file.getAbsoluteFile();
            assertEquals(
                    file.getPath() +
                            ": Error: The resource name must start with a letter",
                    e.getMessage());
            return;
        }
        fail("Expected error");
    }

    @Test
    public void testXmlParseError1() throws Exception {
        File root = TestUtils.getRoot("resources", "brokenSet6");
        try {
            ResourceSet resourceSet = new ResourceSet("brokenSet6", null);
            resourceSet.addSource(root);
            RecordingLogger logger =  new RecordingLogger();
            resourceSet.loadFromFiles(logger);

            ResourceMerger resourceMerger = new ResourceMerger(0);
            resourceMerger.addDataSet(resourceSet);


            MergedResourceWriter writer = getConsumer();
            resourceMerger.mergeData(writer, false /*doCleanUp*/);
        } catch (MergingException e) {
            File file = new File(root, "values" + File.separator + "dimens.xml");
            file = file.getAbsoluteFile();
            assertEquals(file.getPath() + ":4:6: Error: The content of elements must consist "
                    + "of well-formed character data or markup.",
                    e.getMessage());
            return;
        }
        fail("Expected error");
    }

    @Test
    public void testXmlParseError7() throws Exception {
        File root = TestUtils.getRoot("resources", "brokenSet7");
        try {
            ResourceSet resourceSet = new ResourceSet("brokenSet7", null);
            resourceSet.addSource(root);
            RecordingLogger logger =  new RecordingLogger();
            resourceSet.loadFromFiles(logger);

            ResourceMerger resourceMerger = new ResourceMerger(0);
            resourceMerger.addDataSet(resourceSet);


            MergedResourceWriter writer = getConsumer();
            resourceMerger.mergeData(writer, false /*doCleanUp*/);
        } catch (MergingException e) {
            File file = new File(root, "values" + File.separator + "dimens.xml");
            file = file.getAbsoluteFile();
            assertTrue(e.getMessage().startsWith(file.getPath() + ":2:17"));
            return;
        }
        fail("Expected error");
    }

    @Test
    public void testSdkFiltering() throws Exception {
        ResourceSet resourceSet = new ResourceSet("filterableSet", null);
        resourceSet.addSource(TestUtils.getRoot("resources", "filterableSet"));
        resourceSet.loadFromFiles(new RecordingLogger());

        ResourceMerger resourceMerger = new ResourceMerger(21);
        resourceMerger.addDataSet(resourceSet);

        MergedResourceWriter consumer = getConsumer();
        resourceMerger.mergeData(consumer, false);

        File wroteRoot = consumer.getRootFolder();
        assertTrue(wroteRoot.isDirectory());
        assertEquals(1, wroteRoot.listFiles().length);

        File v21 = new File(wroteRoot, "raw-v21");
        assertTrue(v21.isDirectory());
        assertEquals(1, v21.listFiles().length);

        File foo = new File(v21, "foo.txt");
        assertTrue(foo.isFile());

        String fooContents = Files.toString(foo, Charset.defaultCharset());
        assertEquals("21st foo", fooContents);
    }


    // create a fake consumer
    private static class FakeMergeConsumer implements MergeConsumer<ResourceItem> {
        final List<ResourceItem> addedItems = Lists.newArrayList();
        final List<ResourceItem> touchedItems = Lists.newArrayList();
        final List<ResourceItem> removedItems = Lists.newArrayList();

        @Override
        public void start(@NonNull DocumentBuilderFactory factory)
                throws ConsumerException {
            // do nothing
        }

        @Override
        public void end() throws ConsumerException {
            // do nothing
        }

        @Override
        public void addItem(@NonNull ResourceItem item) throws ConsumerException {
            // the default res merge writer calls this, so we should too.
            // this is to test that the merged item are properly created
            @SuppressWarnings("UnusedDeclaration")
            ResourceFile.FileType type = item.getSourceType();

            if (item.isTouched()) {
                touchedItems.add(item);
            }

            addedItems.add(item);
        }

        @Override
        public void removeItem(@NonNull ResourceItem removedItem,
                @Nullable ResourceItem replacedBy)
                throws ConsumerException {
            removedItems.add(removedItem);
        }

        @Override
        public boolean ignoreItemInMerge(ResourceItem item) {
            return item.getIgnoredFromDiskMerge();
        }
    }

    @NonNull
    private MergedResourceWriter getConsumer() {
        return getConsumer(TestUtils.createTempDirDeletedOnExit());
    }

    @NonNull
    private MergedResourceWriter getConsumer(File tempDir) {
        return MergedResourceWriter.createWriterWithoutPngCruncher(
                tempDir,
                null /*publicFile*/,
                null /*blameLogFolder*/,
                mPreprocessor,
                mTemporaryFolder.getRoot());
    }
}
