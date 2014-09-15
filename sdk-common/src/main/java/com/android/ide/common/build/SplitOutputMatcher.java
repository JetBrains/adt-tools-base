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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.SplitOutput;
import com.android.resources.Density;

import java.util.List;

/**
 * Helper class to help with installation of multi-output variants.
 */
public class SplitOutputMatcher {

    /**
     * Returns which output to use based on given densities and abis.
     * @param outputs the outputs to choose from
     * @param density the density
     * @param abis a list of ABIs in descending priority order. Devices that support more than one
     *             abi will prefer splits using the first abi than later ones.
     * @return the output to use or null if none are compatible.
     */
    @Nullable
    public static SplitOutput computeBestOutput(
            @NonNull List<? extends SplitOutput> outputs,
            int density,
            @NonNull List<String> abis) {
        Density densityEnum = Density.getEnum(density);

        String densityValue;
        if (densityEnum == null) {
            densityValue = null;
        } else {
            densityValue = densityEnum.getResourceValue();
        }

        // a full match, both in density and abi
        SplitOutput fullMatch = null;
        // the abi level at which it's a match, in order to find the best ABI for it.
        int fullMatchAbiLevel = Integer.MAX_VALUE;

        // a full universal match, ie that output has no filter.
        SplitOutput universalMatch = null;

        // an ABI match, with universal Density support.
        SplitOutput universalDensityMatch = null;
        // its ABI level for best ABI support.
        int densityMatchAbiLevel = Integer.MAX_VALUE;

        // a density match with universal ABI support.
        SplitOutput universalAbiMatch = null;

        // find a matching output.
        for (SplitOutput output : outputs) {
            String densityFilter = output.getDensityFilter();
            String abiFilter = output.getAbiFilter();

            // record whether this is a universal density or a specific density. This
            // is important to figure out the full type of match this output may be, which
            // can only be done after we look at both density and abi.
            boolean isUniversalDensityMatch = false;

            if (densityFilter != null) {
                // if the density does not match, then we can abort looking at this split
                // completely, otherwise, we'll go on to look at abi.
                if (!densityFilter.equals(densityValue)) {
                    continue;
                }
            } else {
                // no filter on the output means universal match.
                isUniversalDensityMatch = true;
            }

            // at this point it's a density match, we need to figure out if the abi match as well.
            if (abiFilter != null) {
                // search for a matching abi
                int levelMatch = abis.indexOf(abiFilter);
                if (levelMatch == -1) {
                    levelMatch = Integer.MAX_VALUE;
                }

                // check if the density match was a full match or not.
                if (isUniversalDensityMatch) {
                    // check if this match is better than a previous match.
                    if (levelMatch < densityMatchAbiLevel) {
                        densityMatchAbiLevel = levelMatch;
                        universalDensityMatch = output;
                    }
                } else {
                    if (levelMatch < fullMatchAbiLevel) {
                        fullMatchAbiLevel = levelMatch;
                        fullMatch = output;
                    }
                }
            } else {
                // universal abi match, since the density was already checked for. Just need
                // to check what kind of overall match it is.
                if (isUniversalDensityMatch) {
                    universalMatch = output;
                } else {
                    universalAbiMatch = output;
                }
            }
        }

        // full match is better.
        if (fullMatch != null) {
            return fullMatch;
        }

        // then we prefer a universal density over abi, mostly for the case where
        // devices convert native code.
        // We might want to change this depending on whether there are actual splits or not
        // in either dimension.
        if (universalDensityMatch != null) {
            return universalDensityMatch;
        }

        if (universalAbiMatch != null) {
            return universalAbiMatch;
        }

        // last, universal match, or null if none found.
        return universalMatch;
    }
}
