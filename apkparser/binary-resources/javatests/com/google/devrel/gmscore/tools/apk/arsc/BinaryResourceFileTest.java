/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.devrel.gmscore.tools.apk.arsc;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.ByteStreams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@RunWith(JUnit4.class)
/** Tests {@link BinaryResourceFile}. */
public final class BinaryResourceFileTest {

  /** Tests that resource files, when reassembled, are identical. */
  @Test
  public void testToByteArray() throws Exception {
    URL resource = getClass().getResource("/test.apk");
    File apk = new File(resource.getFile());

    // Get all .arsc and encoded .xml files
    String regex = "(.*?\\.arsc)|(AndroidManifest\\.xml)|(res/.*?\\.xml)";
    Map<String, byte[]> resourceFiles = getFiles(apk, Pattern.compile(regex));
    for (Entry<String, byte[]> entry : resourceFiles.entrySet()) {
      String name = entry.getKey();
      byte[] fileBytes = entry.getValue();
      if (!name.startsWith("res/raw/")) {  // xml files in res/raw/ are not compact XML
        BinaryResourceFile file = new BinaryResourceFile(fileBytes);
        assertThat(file.toByteArray()).named(name).isEqualTo(fileBytes);
      }
    }
  }

  /**
   * Returns all files in an apk that match a given regular expression.
   *
   * @param apkFile The file containing the apk zip archive.
   * @param regex A regular expression to match the requested filenames.
   * @return A mapping of the matched filenames to their byte contents.
   * @throws IOException Thrown if a matching file cannot be read from the apk.
   */
  private static Map<String, byte[]> getFiles(File apkFile, Pattern regex) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();  // Retain insertion order
    // Extract apk
    try (ZipFile apkZip = new ZipFile(apkFile)) {
      Enumeration<? extends ZipEntry> zipEntries = apkZip.entries();
      while (zipEntries.hasMoreElements()) {
        ZipEntry zipEntry = zipEntries.nextElement();
        // Visit all files with the given extension
        if (regex.matcher(zipEntry.getName()).matches()) {
          // Map class name to definition
          try (InputStream is = new BufferedInputStream(apkZip.getInputStream(zipEntry))) {
            files.put(zipEntry.getName(), ByteStreams.toByteArray(is));
          }
        }
      }
    }
    return files;
  }

}
