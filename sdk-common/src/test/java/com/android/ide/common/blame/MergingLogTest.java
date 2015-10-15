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

package com.android.ide.common.blame;

import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MergingLogTest {

    @Test
    public void testMergingLog() throws IOException {

        final SourceFilePosition position1 = new SourceFilePosition(
                new SourceFile(absoluteFile("exploded/a/values/values.xml")),
                new SourcePosition(7, 8, 20));

        final SourceFilePosition position2 = new SourceFilePosition(
                new SourceFile(absoluteFile("exploded/b/values/values.xml")),
                new SourcePosition(2, 3, 14));

        File tempDir = Files.createTempDir();
        MergingLog mergingLog = new MergingLog(tempDir);

        mergingLog.logCopy(absoluteFile("exploded/layout/a"), absoluteFile("merged/layout/a"));
        mergingLog.logCopy(absoluteFile("exploded/layout-land/a"),
                absoluteFile("merged/layout-land/a"));

        Map<SourcePosition, SourceFilePosition> map = Maps.newLinkedHashMap();
        map.put(new SourcePosition(1, 2, 3, 7, 1, 120), position1);
        map.put(new SourcePosition(4, 1, 34, 6, 20, 100), position2);
        mergingLog.logSource(new SourceFile(absoluteFile("merged/values/values.xml")), map);

        Map<SourcePosition, SourceFilePosition> map2 = Maps.newLinkedHashMap();
        map2.put(
                new SourcePosition(3, 4, 34),
                new SourceFilePosition(
                        new SourceFile(absoluteFile("exploded/values-de/values.xml")),
                        new SourcePosition(0, 5, 5)));
        mergingLog.logSource(new SourceFile(absoluteFile("merged/values-de/values.xml")), map2);

        // Write and then reload (won't load anything immediately).
        mergingLog.write();
        mergingLog = new MergingLog(tempDir);

        mergingLog.logRemove(new SourceFile(absoluteFile("merged/layout/a")));

        mergingLog.write();
        mergingLog = new MergingLog(tempDir);

        Assert.assertEquals("", new SourceFile(absoluteFile("exploded/layout-land/a")),
                mergingLog.find(new SourceFile(absoluteFile("merged/layout-land/a"))));


        /*
           Test
            |---search query----|
           |---------target----------|
         */
        Assert.assertEquals("", position2,
                mergingLog.find(new SourceFilePosition(
                        new SourceFile(absoluteFile("merged/values/values.xml")),
                        new SourcePosition(4, 1, 35, 4, 2, 36))));

        /*
           Test
                      |---search query----|
           |------------target-------------|
                   |-----wrong----|
         */
        Assert.assertEquals("", position1,
                mergingLog.find(new SourceFilePosition(
                        new SourceFile(absoluteFile("merged/values/values.xml")),
                        new SourcePosition(5, 20, 35, 6, 25, 105))));

        // Check that an unknown file returns itself.
        SourceFilePosition noMatch1 = new SourceFilePosition(
                new SourceFile(absoluteFile("unknownFile")),
                new SourcePosition(1, 2, 3));
        Assert.assertEquals("", noMatch1, mergingLog.find(noMatch1));

        // And that a position that is not mapped in the file also returns itself.
        SourceFilePosition noMatch2 = new SourceFilePosition(
                new SourceFile(absoluteFile("merged/values/values.xml")),
                new SourcePosition(100, 0, 3000));
        Assert.assertEquals("", noMatch2, mergingLog.find(noMatch2));

        mergingLog.write();
    }

    private File testPath = Files.createTempDir();

    private File absoluteFile(String path) {
        return new File(testPath, path);
    }
}
