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
package com.android.resources;

import junit.framework.TestCase;

/**
 * Test that a sample of ResourceType enum variants and their methods.
 */
public class ResourceTypeTest extends TestCase {

  public void testName() {
    assertEquals("array", ResourceType.ARRAY.getName());
    assertEquals("declare-styleable", ResourceType.DECLARE_STYLEABLE.getName());
    assertEquals("mipmap", ResourceType.MIPMAP.getName());
  }

  public void testGetDisplayName() {
    assertEquals("Array", ResourceType.ARRAY.getDisplayName());
    assertEquals("Declare Styleable", ResourceType.DECLARE_STYLEABLE.getDisplayName());
    assertEquals("Mip Map", ResourceType.MIPMAP.getDisplayName());
  }

  public void testStringToEnum() {
    assertEquals(ResourceType.ANIM, ResourceType.getEnum("anim"));
    assertEquals(ResourceType.ANIMATOR, ResourceType.getEnum("animator"));
    assertEquals(ResourceType.DECLARE_STYLEABLE, ResourceType.getEnum("declare-styleable"));
    assertEquals(ResourceType.MIPMAP, ResourceType.getEnum("mipmap"));
    assertEquals(ResourceType.STRING, ResourceType.getEnum("string"));
    assertEquals(ResourceType.STYLE, ResourceType.getEnum("style"));
    assertEquals(ResourceType.STYLEABLE, ResourceType.getEnum("styleable"));
    assertEquals(ResourceType.XML, ResourceType.getEnum("xml"));

    // Alternate names should work:
    assertEquals(ResourceType.ARRAY, ResourceType.getEnum("array"));
    assertEquals(ResourceType.ARRAY, ResourceType.getEnum("string-array"));
    assertEquals(ResourceType.ARRAY, ResourceType.getEnum("integer-array"));

    // Display names should not work.
    assertNull(ResourceType.getEnum("Array"));
    assertNull(ResourceType.getEnum("Declare Styleable"));

    // Misc values should not work.
    assertNull(ResourceType.getEnum(""));
    assertNull(ResourceType.getEnum("declare"));
    assertNull(ResourceType.getEnum("pluralz"));
    assertNull(ResourceType.getEnum("strin"));

    for (ResourceType type : ResourceType.values()) {
      assertEquals(type, ResourceType.getEnum(type.getName()));
    }
  }
}
