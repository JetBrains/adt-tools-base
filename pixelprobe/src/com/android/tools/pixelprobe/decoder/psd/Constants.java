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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.pixelprobe.BlendMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Various constants found in PSD files.
 */
public final class Constants {
    /**
     * List of all the blending modes supported by Photoshop. They are all identified
     * by a 4 character key. The BlendMode enum uses Photoshop's naming conventions
     * (linear dodge is for instance commonly known as "add").
     */
    private static final Map<String, BlendMode> blendModes = new HashMap<>();
    static {
        blendModes.put("pass", BlendMode.PASS_THROUGH);
        blendModes.put("norm", BlendMode.NORMAL);
        blendModes.put("diss", BlendMode.DISSOLVE);
        blendModes.put("dark", BlendMode.DARKEN);
        blendModes.put("mul ", BlendMode.MULTIPLY);
        blendModes.put("idiv", BlendMode.COLOR_BURN);
        blendModes.put("lbrn", BlendMode.LINEAR_BURN);
        blendModes.put("dkCl", BlendMode.DARKER_COLOR);
        blendModes.put("lite", BlendMode.LIGHTEN);
        blendModes.put("scrn", BlendMode.SCREEN);
        blendModes.put("div ", BlendMode.COLOR_DODGE);
        blendModes.put("lddg", BlendMode.LINEAR_DODGE);
        blendModes.put("lgCl", BlendMode.LIGHTER_COLOR);
        blendModes.put("over", BlendMode.OVERLAY);
        blendModes.put("sLit", BlendMode.SOFT_LIGHT);
        blendModes.put("hLit", BlendMode.HARD_LIGHT);
        blendModes.put("vLit", BlendMode.VIVID_LIGHT);
        blendModes.put("lLit", BlendMode.LINEAR_LIGHT);
        blendModes.put("pLit", BlendMode.PIN_LIGHT);
        blendModes.put("hMix", BlendMode.HARD_MIX);
        blendModes.put("diff", BlendMode.DIFFERENCE);
        blendModes.put("smud", BlendMode.EXCLUSION);
        blendModes.put("fsub", BlendMode.SUBTRACT);
        blendModes.put("fdiv", BlendMode.DIVIDE);
        blendModes.put("hue ", BlendMode.HUE);
        blendModes.put("sat ", BlendMode.SATURATION);
        blendModes.put("colr", BlendMode.COLOR);
        blendModes.put("lum ", BlendMode.LUMINOSITY);
        // blend modes stored in descriptors have different keys
        blendModes.put("Nrml", BlendMode.NORMAL);
        blendModes.put("Dslv", BlendMode.DISSOLVE);
        blendModes.put("Drkn", BlendMode.DARKEN);
        blendModes.put("Mltp", BlendMode.MULTIPLY);
        blendModes.put("CBrn", BlendMode.COLOR_BURN);
        blendModes.put("linearBurn", BlendMode.LINEAR_BURN);
        blendModes.put("darkerColor", BlendMode.DARKER_COLOR);
        blendModes.put("Lghn", BlendMode.LIGHTEN);
        blendModes.put("Scrn", BlendMode.SCREEN);
        blendModes.put("CDdg", BlendMode.COLOR_DODGE);
        blendModes.put("linearDodge", BlendMode.LINEAR_DODGE);
        blendModes.put("lighterColor", BlendMode.LIGHTER_COLOR);
        blendModes.put("Ovrl", BlendMode.OVERLAY);
        blendModes.put("SftL", BlendMode.SOFT_LIGHT);
        blendModes.put("HrdL", BlendMode.HARD_LIGHT);
        blendModes.put("vividLight", BlendMode.VIVID_LIGHT);
        blendModes.put("linearLight", BlendMode.LINEAR_LIGHT);
        blendModes.put("pinLight", BlendMode.PIN_LIGHT);
        blendModes.put("hardMix", BlendMode.HARD_MIX);
        blendModes.put("Dfrn", BlendMode.DIFFERENCE);
        blendModes.put("Xclu", BlendMode.EXCLUSION);
        blendModes.put("blendSubtraction", BlendMode.SUBTRACT);
        blendModes.put("blendDivide", BlendMode.DIVIDE);
        blendModes.put("H   ", BlendMode.HUE);
        blendModes.put("Strt", BlendMode.SATURATION);
        blendModes.put("Clr ", BlendMode.COLOR);
        blendModes.put("Lmns", BlendMode.LUMINOSITY);
  }

    private static final Set<String> adjustmentLayers = new HashSet<>(20);
    static {
        adjustmentLayers.add("SoCo");
        adjustmentLayers.add("GdFl");
        adjustmentLayers.add("PtFl");
        adjustmentLayers.add("brit");
        adjustmentLayers.add("levl");
        adjustmentLayers.add("curv");
        adjustmentLayers.add("expA");
        adjustmentLayers.add("vibA");
        adjustmentLayers.add("hue ");
        adjustmentLayers.add("hue2");
        adjustmentLayers.add("blnc");
        adjustmentLayers.add("blwh");
        adjustmentLayers.add("phfl");
        adjustmentLayers.add("mixr");
        adjustmentLayers.add("clrL");
        adjustmentLayers.add("nvrt");
        adjustmentLayers.add("post");
        adjustmentLayers.add("thrs");
        adjustmentLayers.add("grdm");
        adjustmentLayers.add("selc");
    }

    private Constants() {
    }

    /**
     * Returns the blend mode associated with the key found
     * in a PSD file.
     *
     * @param mode Blending mode key as found in a PSD file
     *
     * @return The blend mode matching the specified key, or
     *         {@link BlendMode#NORMAL} if the key is unknown
     */
    static BlendMode getBlendMode(String mode) {
        BlendMode blendMode = blendModes.get(mode);
        return blendMode != null ? blendMode : BlendMode.NORMAL;
    }

    /**
     * Returns the known list of adjustment layer keys.
     */
    static Set<String> getAdjustmentLayerKeys() {
        return adjustmentLayers;
    }
}
