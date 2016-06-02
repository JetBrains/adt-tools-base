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

package com.android.repository.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for FileOpUtils
 */
public class FileOpUtilsTest {

    @Test
    public void makeRelative() throws Exception {
        assertEquals("dir3",
                FileOpUtils.makeRelativeImpl("/dir1/dir2",
                        "/dir1/dir2/dir3",
                        false, "/"));

        assertEquals("../../../dir3",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/dir4/dir5/dir6",
                        "/dir1/dir2/dir3",
                        false, "/"));

        assertEquals("dir3/dir4/dir5/dir6",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/",
                        "/dir1/dir2/dir3/dir4/dir5/dir6",
                        false, "/"));

        // case-sensitive on non-Windows.
        assertEquals("../DIR2/dir3/DIR4/dir5/DIR6",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/",
                        "/dir1/DIR2/dir3/DIR4/dir5/DIR6",
                        false, "/"));

        // same path: empty result.
        assertEquals("",
                FileOpUtils.makeRelativeImpl("/dir1/dir2/dir3",
                        "/dir1/dir2/dir3",
                        false, "/"));

        // same drive letters on Windows
        assertEquals("..\\..\\..\\dir3",
                FileOpUtils.makeRelativeImpl("C:\\dir1\\dir2\\dir4\\dir5\\dir6",
                        "C:\\dir1\\dir2\\dir3",
                        true, "\\"));

        // not case-sensitive on Windows, results will be mixed.
        assertEquals("dir3/DIR4/dir5/DIR6",
                FileOpUtils.makeRelativeImpl("/DIR1/dir2/",
                        "/dir1/DIR2/dir3/DIR4/dir5/DIR6",
                        true, "/"));

        // UNC path on Windows
        assertEquals("..\\..\\..\\dir3",
                FileOpUtils.makeRelativeImpl("\\\\myserver.domain\\dir1\\dir2\\dir4\\dir5\\dir6",
                        "\\\\myserver.domain\\dir1\\dir2\\dir3",
                        true, "\\"));

        // different drive letters are not supported
        try {
            FileOpUtils.makeRelativeImpl("C:\\dir1\\dir2\\dir4\\dir5\\dir6",
                    "D:\\dir1\\dir2\\dir3",
                    true, "\\");
            fail("Expected: IOException. Actual: no exception.");
        } catch (IOException e) {
            assertEquals("makeRelative: incompatible drive letters", e.getMessage());
        }
    }

    @Test
    public void recursiveCopySuccess() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File s3 = new File("/root/src/foo/b");
        File s4 = new File("/root/src/foo/bar/a");
        File s5 = new File("/root/src/baz/c");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(s3.getPath(), "content3");
        fop.recordExistingFile(s4.getPath(), "content4");
        fop.recordExistingFile(s5.getPath(), "content5");

        FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), fop,
                new FakeProgressIndicator());

        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));
        assertEquals("content3", new String(fop.getContent(new File("/root/dest/foo/b"))));
        assertEquals("content4", new String(fop.getContent(new File("/root/dest/foo/bar/a"))));
        assertEquals("content5", new String(fop.getContent(new File("/root/dest/baz/c"))));

        // Also verify the sources are unchanged
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(s3)));
        assertEquals("content4", new String(fop.getContent(s4)));
        assertEquals("content5", new String(fop.getContent(s5)));

        // Finally verify that nothing else is created
        assertEquals(10, fop.getExistingFiles().length);
    }

    @Test
    public void recursiveCopyAlreadyExists() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");

        File d1 = new File("/root/dest/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        try {
            FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), fop,
                    new FakeProgressIndicator());
            fail("Expected exception");
        } catch (IOException expected) {
        }

        // verify that nothing is changed
        assertEquals("content3", new String(fop.getContent(d1)));
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));

        // Finally verify that nothing else is created
        assertEquals(3, fop.getExistingFiles().length);
    }

    @Test
    public void recursiveCopyMerge() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File s3 = new File("/root/src/foo/b");

        File d1 = new File("/root/dest/b");
        File d2 = new File("/root/dest/foo/b");
        File d3 = new File("/root/dest/bar/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(s3.getPath(), "content3");
        fop.recordExistingFile(d1.getPath(), "content4");
        fop.recordExistingFile(d2.getPath(), "content5");
        fop.recordExistingFile(d3.getPath(), "content6");

        FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), true, fop,
                new FakeProgressIndicator());

        // Verify the existing dest files
        assertEquals("content4", new String(fop.getContent(d1)));
        assertEquals("content5", new String(fop.getContent(d2)));
        assertEquals("content6", new String(fop.getContent(d3)));

        // Verify the new dest files
        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));

        // Finally verify that nothing else is created
        assertEquals(8, fop.getExistingFiles().length);
    }

    @Test
    public void recursiveCopyMergeFailed() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");

        File d1 = new File("/root/dest/a/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        try {
            FileOpUtils.recursiveCopy(new File("/root/src/"), new File("/root/dest"), true, fop,
                    new FakeProgressIndicator());
            fail();
        } catch (IOException expected) {
        }

        // Ensure nothing changed
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(d1)));

        // Finally verify that nothing else is created
        assertEquals(3, fop.getExistingFiles().length);
    }

    @Test
    public void safeRecursiveOverwriteSimpleMove() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File s3 = new File("/root/src/foo/b");
        File s4 = new File("/root/src/foo/bar/a");
        File s5 = new File("/root/src/baz/c");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(s3.getPath(), "content3");
        fop.recordExistingFile(s4.getPath(), "content4");
        fop.recordExistingFile(s5.getPath(), "content5");

        FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                new FakeProgressIndicator());

        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));
        assertEquals("content3", new String(fop.getContent(new File("/root/dest/foo/b"))));
        assertEquals("content4", new String(fop.getContent(new File("/root/dest/foo/bar/a"))));
        assertEquals("content5", new String(fop.getContent(new File("/root/dest/baz/c"))));

        // Verify that the original files are gone
        assertEquals(5, fop.getExistingFiles().length);
    }

    @Test
    public void safeRecursiveOverwriteActuallyOverwrite() throws Exception {
        MockFileOp fop = new MockFileOp();
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File s3 = new File("/root/src/foo/bar/a");

        File d1 = new File("/root/dest/a");
        File d2 = new File("/root/dest/foo/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(s3.getPath(), "content3");
        fop.recordExistingFile(d1.getPath(), "content4");
        fop.recordExistingFile(d2.getPath(), "content5");

        FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                new FakeProgressIndicator());

        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));
        assertEquals("content3", new String(fop.getContent(new File("/root/dest/foo/bar/a"))));

        // Verify that the original files are gone
        assertEquals(3, fop.getExistingFiles().length);
    }

    @Test
    public void safeRecursiveOverwriteCantMoveDest() throws Exception {
        final AtomicBoolean hitRename = new AtomicBoolean(false);

        MockFileOp fop = new MockFileOp() {
            @Override
            public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
                if (oldFile.equals(new File("/root/dest"))) {
                    hitRename.set(true);
                    return false;
                }
                return super.renameTo(oldFile, newFile);
            }
        };
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File d1 = new File("/root/dest/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                new FakeProgressIndicator());

        // Make sure we tried and failed to move the dest.
        assertTrue(hitRename.get());

        // verify the files were moved
        assertEquals("content1", new String(fop.getContent(new File("/root/dest/a"))));
        assertEquals("content2", new String(fop.getContent(new File("/root/dest/foo/a"))));

        // Finally verify that nothing else is created
        assertEquals(2, fop.getExistingFiles().length);

    }

    @Test
    public void safeRecursiveOverwriteCantDeleteDest() throws Exception {
        MockFileOp fop = new MockFileOp() {
            @Override
            public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
                if (oldFile.equals(new File("/root/dest"))) {
                    return false;
                }
                return super.renameTo(oldFile, newFile);
            }

            @Override
            public boolean delete(@NonNull File oldFile) {
                if (oldFile.equals(new File("/root/dest"))) {
                    return false;
                }
                return super.delete(oldFile);
            }
        };
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File d1 = new File("/root/dest/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        try {
            FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                    new FakeProgressIndicator());
            fail("Expected exception");
        }
        catch (Exception expected) {}

        // Ensure nothing changed
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(d1)));

        // Finally verify that nothing else is created
        assertEquals(3, fop.getExistingFiles().length);
    }

    @Test
    public void safeRecursiveOverwriteCantDeleteDestPartial() throws Exception {
        AtomicBoolean deletedSomething = new AtomicBoolean(false);
        MockFileOp fop = new MockFileOp() {
            @Override
            public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
                if (getAgnosticAbsPath(oldFile).equals("/root/dest")) {
                    return false;
                }
                return super.renameTo(oldFile, newFile);
            }

            @Override
            public boolean delete(@NonNull File oldFile) {
                if (getAgnosticAbsPath(oldFile.getPath()).startsWith(("/root/dest/"))) {
                    if (deletedSomething.compareAndSet(false, true)) {
                        return super.delete(oldFile);
                    }
                    return false;
                }
                return super.delete(oldFile);
            }
        };
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File d1 = new File("/root/dest/b");
        File d2 = new File("/root/dest/bar/b");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");
        fop.recordExistingFile(d2.getPath(), "content4");

        try {
            FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                    new FakeProgressIndicator());
            fail("Expected exception");
        }
        catch (IOException expected) {}

        assertTrue(deletedSomething.get());
        // Ensure nothing changed
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(d1)));
        assertEquals("content4", new String(fop.getContent(d2)));

        // Finally verify that nothing else is created
        assertEquals(4, fop.getExistingFiles().length);
    }

    @Test
    public void safeRecursiveOverwriteCantWrite() throws Exception {
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");
        File d1 = new File("/root/dest/a");

        MockFileOp fop = new MockFileOp() {
            @Override
            public void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
                if (source.equals(s1) && dest.equals(d1)) {
                    throw new IOException("failed to copy");
                }
                super.copyFile(source, dest);
            }

            @Override
            public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
                if (getAgnosticAbsPath(oldFile.getPath()).equals("/root/src") &&
                        getAgnosticAbsPath(newFile.getPath()).equals("/root/dest")) {
                    return false;
                }
                return super.renameTo(oldFile, newFile);
            }
        };

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");

        try {
            FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                    new FakeProgressIndicator());
            fail("Expected exception");
        }
        catch (IOException expected) {}

        // Ensure nothing changed
        assertEquals("content1", new String(fop.getContent(s1)));
        assertEquals("content2", new String(fop.getContent(s2)));
        assertEquals("content3", new String(fop.getContent(d1)));

        // Finally verify that nothing else is created
        assertEquals(3, fop.getExistingFiles().length);
    }

    @Test
    public void safeRecursiveOverwriteCantDeleteDestThenCantMoveBack() throws Exception {
        File d1 = new File("/root/dest/b");
        File d2 = new File("/root/dest/foo/b");

        MockFileOp fop = new MockFileOp() {
            @Override
            public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
                if (oldFile.equals(new File("/root/dest"))) {
                    return false;
                }
                return super.renameTo(oldFile, newFile);
            }

            @Override
            public boolean delete(@NonNull File oldFile) {
                if (oldFile.equals(new File("/root/dest"))) {
                    return false;
                }
                return super.delete(oldFile);
            }

            @Override
            public void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
                if (dest.equals(d1)) {
                    throw new IOException("failed to copy");
                }
                super.copyFile(source, dest);
            }
        };
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");
        fop.recordExistingFile(d2.getPath(), "content4");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        try {
            FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                    progress);
            fail("Expected exception");
        }
        catch (IOException expected) {}

        final String marker = "available at ";
        String message = progress.getWarnings().stream()
                .filter(in -> in.contains(marker))
                .findAny()
                .get();

        String backupPath = message
                .substring(message.indexOf(marker) + marker.length(), message.indexOf('\n'));

        // Ensure backup is correct
        assertEquals("content3", new String(fop.getContent(new File(backupPath, "b"))));
        assertEquals("content4", new String(fop.getContent(new File(backupPath, "foo/b"))));
    }

    @Test
    public void safeRecursiveOverwriteCantCopyThenCantRestore() throws Exception {
        File d1 = new File("/root/dest/a");
        File d2 = new File("/root/dest/foo/b");
        File s1 = new File("/root/src/a");
        File s2 = new File("/root/src/foo/a");

        MockFileOp fop = new MockFileOp() {
            @Override
            public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
                if (newFile.equals(new File("/root/dest"))) {
                    return false;
                }
                return super.renameTo(oldFile, newFile);
            }

            @Override
            public void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
                if (dest.equals(d1)) {
                    throw new IOException("failed to copy");
                }
                super.copyFile(source, dest);
            }
        };

        fop.recordExistingFile(s1.getPath(), "content1");
        fop.recordExistingFile(s2.getPath(), "content2");
        fop.recordExistingFile(d1.getPath(), "content3");
        fop.recordExistingFile(d2.getPath(), "content4");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        try {
            FileOpUtils.safeRecursiveOverwrite(new File("/root/src/"), new File("/root/dest"), fop,
                    progress);
            fail("Expected exception");
        }
        catch (IOException expected) {}

        final String marker = "available at ";
        String message = progress.getWarnings().stream()
                .filter(in -> in.contains(marker))
                .findAny()
                .get();

        String backupPath = message
                .substring(message.indexOf(marker) + marker.length(), message.indexOf('\n'));

        // Ensure backup is correct
        assertEquals("content3", new String(fop.getContent(new File(backupPath, "a"))));
        assertEquals("content4", new String(fop.getContent(new File(backupPath, "foo/b"))));
    }

}
