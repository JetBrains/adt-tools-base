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
package com.android.ide.common.rendering;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Class loader that filters the passed set of filtered classes and avoids loading them from the parent class loader. The inner classes
 * do not need to be in the filteredClasses set as they will be filtered using the parent class name (i.e. if ClassA is in the filtered set,
 * ClassA$Inner will also be filtered).
 */
public class FilteredClassLoader extends ClassLoader {
  private final ImmutableSet<String> myFilteredClasses;

  /**
   * @param parent parent ClassLoader instance
   * @param filteredClasses the list of class names to filter with the format package.ClassName
   */
  FilteredClassLoader(@NonNull ClassLoader parent, @NonNull Set<String> filteredClasses) {
    super(parent);

    myFilteredClasses = ImmutableSet.copyOf(filteredClasses);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    String lookupName = name;
    int dollarIdx = lookupName.indexOf("$");
    if (dollarIdx != -1) {
      // If it's an inner class, filter it if the parent class is contained in the list
      lookupName = lookupName.substring(0, dollarIdx);
    }
    if (myFilteredClasses.contains(lookupName)) {
      throw new ClassNotFoundException();
    }
    return super.loadClass(name, resolve);
  }
}
