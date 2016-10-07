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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.resources.ResourceType;
import com.android.testutils.TestUtils;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Test the IdGeneratingResourceParser.
 */
public class IdGeneratingResourceParserTest extends BaseTestCase {

    @Test
    public void testParseLayoutDocument() throws Exception {
        File root = TestUtils.getRoot("resources", "idGenerating");
        File layout = new File(root, "layout");
        File layoutFile = new File(layout, "layout_for_id_scan.xml");

        IdGeneratingResourceParser parser = new IdGeneratingResourceParser(layoutFile, "layout_for_id_scan", ResourceType.LAYOUT);
        ResourceItem fileItem = parser.getFileResourceItem();
        assertEquals(fileItem.getName(), "layout_for_id_scan");
        assertEquals(fileItem.getType(), ResourceType.LAYOUT);

        List<ResourceItem> idItems = parser.getIdResourceItems();
        assertResourceItemsNames(idItems,
                                 "header", "image", "styledView", "imageView", "btn_title_refresh", "title_refresh_progress",
                                 "imageView2", "imageButton", "noteArea", "text2", "nonExistent");
    }

    @Test
    public void testParseMenuDocument() throws Exception {
        File root = TestUtils.getRoot("resources", "idGenerating");
        File menu = new File(root, "menu");
        File menuFile = new File(menu, "menu.xml");

        IdGeneratingResourceParser parser = new IdGeneratingResourceParser(menuFile, "menu", ResourceType.MENU);

        ResourceItem fileItem = parser.getFileResourceItem();
        assertEquals(fileItem.getName(), "menu");
        assertEquals(fileItem.getType(), ResourceType.MENU);

        List<ResourceItem> idItems = parser.getIdResourceItems();
        assertResourceItemsNames(idItems, "item1", "group", "group_item1", "group_item2", "submenu", "submenu_item2");
    }

    @Test
    public void testParseDataBindingDocument() throws Exception {
        File root = TestUtils.getRoot("resources", "idGenerating");
        File layout = new File(root, "layout");
        File layoutFile = new File(layout, "layout_with_databinding.xml");

        try {
            new IdGeneratingResourceParser(layoutFile, "layout_with_databinding", ResourceType.LAYOUT);
            assertTrue("Should have thrown exception", true);
        }
        catch (MergingException e) {
            assertEquals("Error: Does not handle data-binding files", e.getMessage());
        }
    }

    private static void assertResourceItemsNames(Collection<ResourceItem> idItems, String... expected) {
        Collection<String> idNames = Collections2.transform(idItems, new Function<ResourceItem, String>() {
            @Override
            public String apply(ResourceItem input) {
                assertEquals(input.getType(), ResourceType.ID);
                return input.getName();
            }
        });
        assertSameElements(idNames, Arrays.asList(expected));
    }

    private static <T> void assertSameElements(Collection<? extends T> collection, Collection<T> expected) {
        assertNotNull(collection);
        assertNotNull(expected);
        assertEquals(new TreeSet<T>(expected), new TreeSet<T>(collection));
    }
}
