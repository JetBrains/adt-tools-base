package com.android.ide.common.resources;

import static com.android.SdkConstants.FD_RES;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import com.android.annotations.NonNull;
import com.android.io.FolderWrapper;
import com.android.io.IAbstractFolder;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

class TestResourceRepository extends ResourceRepository {
    private final File mDir;

    TestResourceRepository(@NonNull IAbstractFolder resFolder, boolean isFrameworkRepository,
            File dir) {
        super(resFolder, isFrameworkRepository);
        mDir = dir;
    }

    @NonNull
    @Override
    protected ResourceItem createResourceItem(@NonNull String name) {
        return new TestResourceItem(name);
    }

    public File getDir() {
        return mDir;
    }

    public void dispose() {
        deleteFile(mDir);
    }

    private static void deleteFile(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        } else if (dir.isFile()) {
            assertTrue(dir.getPath(), dir.delete());
        }
    }

    /**
     * Creates a resource repository for a resource folder whose contents is identified
     * by the pairs of relative paths and file contents
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    static TestResourceRepository create(boolean isFramework, Object[] data) throws IOException {
        File dir = Files.createTempDir();
        File res = new File(dir, FD_RES);
        res.mkdirs();

        assertTrue("Expected even number of items (path,contents)", data.length % 2 == 0);
        for (int i = 0; i < data.length; i += 2) {
            Object relativePathObject = data[i];
            assertTrue(relativePathObject instanceof String);
            String relativePath = (String) relativePathObject;
            relativePath = relativePath.replace('/', File.separatorChar);
            File file = new File(res, relativePath);
            File parent = file.getParentFile();
            parent.mkdirs();

            Object fileContents = data[i + 1];
            if (fileContents instanceof String) {
                String text = (String) fileContents;
                Files.write(text, file, Charsets.UTF_8);
            } else if (fileContents instanceof byte[]) {
                byte[] bytes = (byte[]) fileContents;
                Files.write(bytes, file);
            } else {
                fail("File contents must be Strings or byte[]'s");
            }
        }

        IAbstractFolder resFolder = new FolderWrapper(dir, FD_RES);
        return new TestResourceRepository(resFolder, isFramework, dir);
    }

    private static class TestResourceItem extends ResourceItem {
        TestResourceItem(String name) {
            super(name);
        }
    }
}
