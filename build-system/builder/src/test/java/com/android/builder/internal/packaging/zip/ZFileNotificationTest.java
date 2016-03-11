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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.utils.IOExceptionRunnable;
import com.android.utils.Pair;
import com.google.common.collect.Lists;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ZFileNotificationTest {
    private static class KeepListener extends ZFileExtension {
        public int open;
        public int beforeUpdated;
        public int updated;
        public int closed;
        public List<Pair<StoredEntry, StoredEntry>> added;
        public List<StoredEntry> removed;
        public IOExceptionRunnable returnRunnable;

        KeepListener() {
            reset();
        }

        @Nullable
        @Override
        public IOExceptionRunnable open() {
            open++;
            return returnRunnable;
        }

        @Nullable
        @Override
        public IOExceptionRunnable beforeUpdate() {
            beforeUpdated++;
            return returnRunnable;
        }

        @Override
        public void updated() {
            updated++;
        }

        @Override
        public void closed() {
            closed++;
        }

        @Nullable
        @Override
        public IOExceptionRunnable added(@NonNull StoredEntry entry, @Nullable StoredEntry replaced) {
            added.add(Pair.of(entry, replaced));
            return returnRunnable;
        }

        @Nullable
        @Override
        public IOExceptionRunnable removed(@NonNull StoredEntry entry) {
            removed.add(entry);
            return returnRunnable;
        }

        void reset() {
            open = 0;
            beforeUpdated = 0;
            updated = 0;
            closed = 0;
            added = Lists.newArrayList();
            removed = Lists.newArrayList();
        }

        void assertClear() {
            assertEquals(0, open);
            assertEquals(0, beforeUpdated);
            assertEquals(0, updated);
            assertEquals(0, closed);
            assertEquals(0, added.size());
            assertEquals(0, removed.size());
        }
    }

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void notifyAddFile() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();
        assertEquals(1, kl.added.size());
        StoredEntry addedSe = kl.added.get(0).getFirst();
        assertNull(kl.added.get(0).getSecond());
        kl.added.clear();
        kl.assertClear();

        StoredEntry foo = zf.get("foo");
        assertNotNull(foo);

        assertSame(foo, addedSe);

        zf.close();
    }

    @Test
    public void notifyRemoveFile() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();
        kl.reset();

        StoredEntry foo = zf.get("foo");
        assertNotNull(foo);

        foo.delete();
        assertEquals(1, kl.removed.size());
        assertSame(foo, kl.removed.get(0));
        kl.removed.clear();
        kl.assertClear();

        zf.close();
    }

    @Test
    public void notifyUpdateFile() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();
        StoredEntry foo1 = zf.get("foo");
        kl.reset();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 2, 3 }));
        zf.finishAllBackgroundTasks();
        StoredEntry foo2 = zf.get("foo");

        assertEquals(1, kl.added.size());
        assertSame(foo2, kl.added.get(0).getFirst());
        assertSame(foo1, kl.added.get(0).getSecond());

        kl.added.clear();
        kl.assertClear();

        zf.close();
    }

    @Test
    public void notifyOpenUpdateClose() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();
        kl.reset();
        zf.close();

        assertEquals(1, kl.open);
        kl.open = 0;
        assertEquals(1, kl.beforeUpdated);
        assertEquals(1, kl.updated);
        kl.beforeUpdated = 0;
        kl.updated = 0;
        assertEquals(1, kl.closed);
        kl.closed = 0;
        kl.assertClear();
    }

    @Test
    public void notifyOpenUpdate() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();
        kl.reset();
        zf.update();

        assertEquals(1, kl.open);
        kl.open = 0;
        assertEquals(1, kl.beforeUpdated);
        assertEquals(1, kl.updated);
        kl.beforeUpdated = 0;
        kl.updated = 0;
        kl.assertClear();

        zf.close();
    }

    @Test
    public void notifyUpdate() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.update();
        kl.reset();

        zf.add("bar", new ByteArrayInputStream(new byte[] { 2, 3 }));
        zf.finishAllBackgroundTasks();
        kl.reset();

        zf.update();
        assertEquals(1, kl.beforeUpdated);
        assertEquals(1, kl.updated);
        kl.beforeUpdated = 0;
        kl.updated = 0;
        kl.assertClear();

        zf.close();
    }

    @Test
    public void removedListenersAreNotNotified() throws Exception {
        ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));
        KeepListener kl = new KeepListener();
        zf.addZFileExtension(kl);

        kl.assertClear();

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();
        assertEquals(1, kl.added.size());
        kl.added.clear();
        kl.assertClear();

        zf.removeZFileExtension(kl);

        zf.add("foo", new ByteArrayInputStream(new byte[] { 2, 3 }));
        zf.finishAllBackgroundTasks();
        kl.assertClear();

        zf.close();
    }

    @Test
    public void actionsExecutedAtEndOfNotification() throws Exception {
        final ZFile zf = new ZFile(new File(mTemporaryFolder.getRoot(), "a.zip"));

        final IOException death[] = new IOException[1];

        final KeepListener kl1 = new KeepListener();
        zf.addZFileExtension(kl1);
        kl1.returnRunnable = new IOExceptionRunnable() {
            private boolean once = false;

            @Override
            public void run() {
                if (once) {
                    return;
                }

                once = true;

                try {
                    zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
                } catch (IOException e ) {
                    death[0] = e;
                }
            }
        };

        final KeepListener kl2 = new KeepListener();
        zf.addZFileExtension(kl2);
        kl2.returnRunnable = new IOExceptionRunnable() {
            private boolean once = false;

            @Override
            public void run() {
                if (once) {
                    return;
                }

                once = true;
                try {
                    zf.add("bar", new ByteArrayInputStream(new byte[] { 1, 2 }));
                } catch (IOException e ) {
                    death[0] = e;
                }
            }
        };

        kl1.assertClear();
        kl2.assertClear();

        zf.add("xpto", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();

        assertEquals(3, kl1.added.size());
        kl1.added.clear();
        kl1.assertClear();
        assertEquals(3, kl2.added.size());
        kl2.added.clear();
        kl2.assertClear();

        assertNull(death[0]);

        zf.close();
    }

    @Test
    public void canAddFilesDuringUpdateNotification() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        final ZFile zf = new ZFile(zipFile);

        final IOException death[] = new IOException[1];

        final KeepListener kl1 = new KeepListener();
        zf.addZFileExtension(kl1);

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();

        kl1.returnRunnable = new IOExceptionRunnable() {
            private boolean once = false;

            @Override
            public void run() {
                if (once) {
                    return;
                }

                once = true;

                try {
                    zf.add("bar", new ByteArrayInputStream(new byte[] { 1, 2 }));
                } catch (IOException e ) {
                    death[0] = e;
                }
            }
        };

        zf.close();
        ZFile zf2 = new ZFile(zipFile);
        StoredEntry fooFile = zf2.get("foo");
        assertNotNull(fooFile);
        StoredEntry barFile = zf2.get("bar");
        assertNotNull(barFile);
        zf2.close();
    }

    @Test
    public void notifyOnceEntriesWritten() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        final ZFile zf = new ZFile(zipFile);

        ZFileExtension ext = Mockito.mock(ZFileExtension.class);
        zf.addZFileExtension(ext);

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();

        Mockito.verify(ext, Mockito.times(0)).entriesWritten();
        zf.close();
        Mockito.verify(ext, Mockito.times(1)).entriesWritten();
    }

    @Test
    public void notifyTwiceEntriesWrittenIfCdChanged() throws Exception {
        File zipFile = new File(mTemporaryFolder.getRoot(), "a.zip");
        final ZFile zf = new ZFile(zipFile);

        final ZFileExtension ext = Mockito.mock(ZFileExtension.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                zf.setExtraDirectoryOffset(10);
                Mockito.doNothing().when(ext).entriesWritten();
                return null;
            }
        }).when(ext).entriesWritten();

        zf.addZFileExtension(ext);

        zf.add("foo", new ByteArrayInputStream(new byte[] { 1, 2 }));
        zf.finishAllBackgroundTasks();

        Mockito.verify(ext, Mockito.times(0)).entriesWritten();
        zf.close();
        Mockito.verify(ext, Mockito.times(2)).entriesWritten();
    }
}
