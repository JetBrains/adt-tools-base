/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sdklib.repository.local;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.PkgProps;

import java.util.Properties;

/**
 * Misc utilities to help extracting elements and attributes out of a repository XML document.
 */
class PackageParserUtils {
  /**
   * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a full
   * revision (major.minor.micro.preview).
   *
   * @param props The properties to parse.
   * @return A {@link FullRevision} or null if there is no such property or it couldn't be parsed.
   * @param propKey The name of the property. Must not be null.
   */
  @Nullable
  public static FullRevision getPropertyFull(
          @Nullable Properties props,
          @NonNull String propKey) {
    String revStr = getProperty(props, propKey, null);

    FullRevision rev = null;
    if (revStr != null) {
      try {
        rev = FullRevision.parseRevision(revStr);
      } catch (NumberFormatException ignore) {}
    }

    return rev;
  }

  /**
   * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a major
   * revision (major integer, no minor/micro/preview parts.)
   *
   * @param props The properties to parse.
   * @return A {@link MajorRevision} or null if there is no such property or it couldn't be parsed.
   * @param propKey The name of the property. Must not be null.
   */
  @Nullable
  public static MajorRevision getPropertyMajor(
          @Nullable Properties props,
          @NonNull String propKey) {
    String revStr = getProperty(props, propKey, null);

    MajorRevision rev = null;
    if (revStr != null) {
      try {
        rev = MajorRevision.parseRevision(revStr);
      } catch (NumberFormatException ignore) {}
    }

    return rev;
  }

  /**
   * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a no-preview
   * revision (major.minor.micro integers but no preview part.)
   *
   * @param props The properties to parse.
   * @return A {@link NoPreviewRevision} or
   *         null if there is no such property or it couldn't be parsed.
   * @param propKey The name of the property. Must not be null.
   */
  @Nullable
  public static NoPreviewRevision getPropertyNoPreview(
          @Nullable Properties props,
          @NonNull String propKey) {
    String revStr = getProperty(props, propKey, null);

    NoPreviewRevision rev = null;
    if (revStr != null) {
      try {
        rev = NoPreviewRevision.parseRevision(revStr);
      } catch (NumberFormatException ignore) {}
    }

    return rev;
  }


  /**
   * Utility method that returns a property from a {@link Properties} object.
   * Returns the default value if props is null or if the property is not defined.
   *
   * @param props The {@link Properties} to search into.
   *   If null, the default value is returned.
   * @param propKey The name of the property. Must not be null.
   * @param defaultValue The default value to return if {@code props} is null or if the
   *   key is not found. Can be null.
   * @return The string value of the given key in the properties, or null if the key
   *   isn't found or if {@code props} is null.
   */
  @Nullable
  public static String getProperty(
          @Nullable Properties props,
          @NonNull String propKey,
          @Nullable String defaultValue) {
    if (props == null) {
      return defaultValue;
    }
    return props.getProperty(propKey, defaultValue);
  }
}
