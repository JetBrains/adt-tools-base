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

package com.android.build.gradle.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.build.gradle.internal.dsl.GroupableProductFlavor;
import com.google.common.collect.ImmutableList;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class ProductFlavorComboTest {
    private Instantiator instantiator = new DirectInstantiator();
    private Project project = ProjectBuilder.builder().build();
    private Logger logger = Logging.getLogger(this.getClass());

    private static final String DIMENSION1 = "dimension1";
    private static final String DIMENSION2 = "dimension2";

    private GroupableProductFlavor f1;
    private GroupableProductFlavor f2;
    private GroupableProductFlavor f1d1;
    private GroupableProductFlavor f2d1;
    private GroupableProductFlavor f1d2;
    private GroupableProductFlavor f2d2;

    @Before
    public void setup() {
        f1 = new GroupableProductFlavor("flavor1", project, instantiator, logger);
        f2 = new GroupableProductFlavor("flavor2", project, instantiator, logger);

        f1d1 = new GroupableProductFlavor("flavor1", project, instantiator, logger);
        f1d1.setFlavorDimension(DIMENSION1);
        f2d1 = new GroupableProductFlavor("flavor2", project, instantiator, logger);
        f2d1.setFlavorDimension(DIMENSION1);

        f1d2 = new GroupableProductFlavor("flavor1", project, instantiator, logger);
        f1d2.setFlavorDimension(DIMENSION2);
        f2d2 = new GroupableProductFlavor("flavor2", project, instantiator, logger);
        f2d2.setFlavorDimension(DIMENSION2);
    }

    @Test
    public void getNameEmpty() {
        assertEquals("", new ProductFlavorCombo().getName());
    }

    @Test
    public void getNameSingleFlavor() {
        assertEquals("flavor1", new ProductFlavorCombo(f1).getName());
    }

    @Test
    public void getNameMultiFlavor() {
        assertEquals("flavor1Flavor2", new ProductFlavorCombo(f1, f2).getName());
    }

    @Test
    public void createGroupListEmpty() throws Exception {
        assertEqualGroupList(
                ImmutableList.<ProductFlavorCombo>of(),
                ProductFlavorCombo.createCombinations(
                        Collections.<String>emptyList(),
                        Collections.<GroupableProductFlavor>emptyList()));
    }

    @Test
    public void createGroupListNullDimension() throws Exception {
        assertEqualGroupList(
                ImmutableList.of(
                        new ProductFlavorCombo(f1)
                ),
                ProductFlavorCombo.createCombinations(
                        null,
                        ImmutableList.of(f1)));
    }

    @Test
    public void createGroupListEmptyDimension() throws Exception {
        assertEqualGroupList(
                ImmutableList.of(
                        new ProductFlavorCombo(f1),
                        new ProductFlavorCombo(f2)
                ),
                ProductFlavorCombo.createCombinations(
                        ImmutableList.<String>of(),
                        ImmutableList.of(f1, f2)));
    }

    @Test
    public void createGroupListSingleDimension() throws Exception {
        assertEqualGroupList(
                ImmutableList.of(new ProductFlavorCombo(f1d1)),
                ProductFlavorCombo.createCombinations(
                        ImmutableList.of(DIMENSION1),
                        ImmutableList.of(f1d1)));
    }

    @Test
    public void createGroupListMultiDimensions() throws Exception {
        assertEqualGroupList(
                ImmutableList.of(
                        new ProductFlavorCombo(f1d1, f1d2),
                        new ProductFlavorCombo(f1d1, f2d2),
                        new ProductFlavorCombo(f2d1, f1d2),
                        new ProductFlavorCombo(f2d1, f2d2)),
                ProductFlavorCombo.createCombinations(
                        ImmutableList.of(DIMENSION1, DIMENSION2),
                        ImmutableList.of(f1d1, f1d2, f2d1, f2d2)));
    }

    @Test
    public void createGroupListMissingDimension() throws Exception {
        try {
            ProductFlavorCombo.createCombinations(
                    ImmutableList.of(DIMENSION1),
                    ImmutableList.of(f1));
            fail("Expected to throw");
        } catch (RuntimeException ignore) {
        }
    }

    @Test
    public void createGroupListInvalidDimension() throws Exception {
        try {
            ProductFlavorCombo.createCombinations(
                    ImmutableList.of(DIMENSION1),
                    ImmutableList.of(f1d2));
            fail("Expected to throw");
        } catch (RuntimeException ignore) {
        }
    }

    @Test
    public void createGroupListDimensionWithoutFlavor() throws Exception {
        try {
            ProductFlavorCombo.createCombinations(
                    ImmutableList.of(DIMENSION1, DIMENSION2),
                    ImmutableList.of(f1d1));
            fail("Expected to throw");
        } catch (RuntimeException ignore) {
        }
    }

    private static void assertEqualGroupList(List<ProductFlavorCombo> expected, List<ProductFlavorCombo> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(
                    "Unexpected value for ProductFlavorCombo " + i,
                    expected.get(i).getFlavorList().toArray(),
                    actual.get(i).getFlavorList().toArray());
        }
    }
}