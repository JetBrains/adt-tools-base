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
package com.android.assetstudiolib;

import com.android.ide.common.vectordrawable.VdIcon;

import java.net.URL;

public class AndroidVectorIcons {
  private static final String MATERIAL_DESIGN_ICONS_PATH = "images/material_design_icons/";

  private static VdIcon load(String path, int size) {
    URL url = GraphicGenerator.class.getClassLoader().getResource(MATERIAL_DESIGN_ICONS_PATH + path);
    return new VdIcon(url, size, size);
  }

  public static class EditorIcons {
    public static final VdIcon Bold = load("editor/ic_format_bold_black_24dp.xml", 16);
    public static final VdIcon Italic = load("editor/ic_format_italic_black_24dp.xml", 16);
    public static final VdIcon AllCaps = load("editor/ic_text_fields_black_24dp.xml", 16);
    public static final VdIcon AlignLeft = load("editor/ic_format_align_left_black_24dp.xml", 16);
    public static final VdIcon AlignCenter = load("editor/ic_format_align_center_black_24dp.xml", 16);
    public static final VdIcon AlignRight = load("editor/ic_format_align_right_black_24dp.xml", 16);
  }

  public static class LayoutEditorIcons {
    public static final VdIcon UnSetUp = load("navigation/ic_more_horiz_black_24dp.xml", 10);
    public static final VdIcon UnSetDown = load("navigation/ic_more_horiz_black_24dp.xml", 10);
    public static final VdIcon ArrowUp = load("navigation/ic_arrow_upward_black_24dp.xml", 10);
    public static final VdIcon ArrowDown = load("navigation/ic_arrow_downward_black_24dp.xml", 10);
    public static final VdIcon Clip = load("content/ic_content_cut_black_24dp.xml", 16);
  }
}
