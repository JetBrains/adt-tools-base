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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.ProductFlavor;
import com.android.utils.StringHelper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * A combination of product flavors for a variant, each belonging to a different flavor dimension.
 */
public class ProductFlavorCombo {
    private String name;

    @NonNull
    private final List<ProductFlavor> flavorList;

    /**
     * Create a ProductFlavorCombo.
     * @param flavors Lists of ProductFlavor.
     */
    public ProductFlavorCombo(@NonNull ProductFlavor... flavors) {
        flavorList = ImmutableList.copyOf(flavors);
    }

    public ProductFlavorCombo(@NonNull Iterable<ProductFlavor> flavors) {
        flavorList = ImmutableList.copyOf(flavors);
    }

    @NonNull
    public String getName() {
        if (name == null) {
            boolean first = true;
            StringBuilder sb = new StringBuilder();
            for (ProductFlavor flavor : flavorList) {
                if (first) {
                    sb.append(flavor.getName());
                    first = false;
                } else {
                    sb.append(StringHelper.capitalize(flavor.getName()));
                }
            }
            name = sb.toString();
        }
        return name;
    }

    @NonNull
    public List<ProductFlavor> getFlavorList() {
        return flavorList;
    }

    /**
     * Creates a list containing all combinations of ProductFlavors of the given dimensions.
     * @param flavorDimensions The dimensions each product flavor can belong to.
     * @param productFlavors An iterable of all ProductFlavors in the project..
     * @return A list of ProductFlavorCombo representing all combinations of ProductFlavors.
     */
    @NonNull
    public static List<ProductFlavorCombo> createCombinations(
            @Nullable List<String> flavorDimensions,
            @NonNull Iterable<? extends ProductFlavor> productFlavors) {

        List <ProductFlavorCombo> result = Lists.newArrayList();
        if (flavorDimensions == null || flavorDimensions.isEmpty()) {
            for (ProductFlavor flavor : productFlavors) {
                result.add(new ProductFlavorCombo(ImmutableList.of(flavor)));
            }
        } else {
            // need to group the flavor per dimension.
            // First a map of dimension -> list(ProductFlavor)
            ArrayListMultimap<String, ProductFlavor> map = ArrayListMultimap.create();
            for (ProductFlavor flavor : productFlavors) {
                String flavorDimension = flavor.getDimension();

                if (flavorDimension == null) {
                    throw new RuntimeException(String.format(
                            "Flavor '%1$s' has no flavor dimension.", flavor.getName()));
                }
                if (!flavorDimensions.contains(flavorDimension)) {
                    throw new RuntimeException(String.format(
                            "Flavor '%1$s' has unknown dimension '%2$s'.",
                            flavor.getName(), flavor.getDimension()));
                }

                map.put(flavorDimension, flavor);
            }

            createProductFlavorCombinations(result,
                    new ProductFlavor[flavorDimensions.size()],
                    0, flavorDimensions, map);
        }
        return result;
    }

    /**
     * Remove all null reference from an array and create an ImmutableList it.
     */
    private static ImmutableList<ProductFlavor> filterNullFromArray(ProductFlavor[] flavors) {
        ImmutableList.Builder<ProductFlavor> builder = ImmutableList.builder();
        for (ProductFlavor flavor : flavors) {
            if (flavor != null) {
                builder.add(flavor);
            }
        }
        return builder.build();
    }

    private static void createProductFlavorCombinations(
            List<ProductFlavorCombo> flavorGroups,
            ProductFlavor[] group,
            int index,
            List<String> flavorDimensionList,
            ListMultimap<String, ProductFlavor> map) {
        if (index == flavorDimensionList.size()) {
            flavorGroups.add(new ProductFlavorCombo(filterNullFromArray(group)));
            return;
        }

        // fill the array at the current index.
        // get the dimension name that matches the index we are filling.
        String dimension = flavorDimensionList.get(index);

        // from our map, get all the possible flavors in that dimension.
        List<ProductFlavor> flavorList = map.get(dimension);

        // loop on all the flavors to add them to the current index and recursively fill the next
        // indices.
        if (flavorList.isEmpty()) {
            throw new RuntimeException(String.format(
                    "No flavor is associated with flavor dimension '%1$s'.", dimension));
        } else {
            for (ProductFlavor flavor : flavorList) {
                group[index] = flavor;
                createProductFlavorCombinations(
                        flavorGroups, group, index + 1, flavorDimensionList, map);
            }
        }
    }
}
