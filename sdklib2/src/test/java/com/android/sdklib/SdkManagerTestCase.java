/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdklib;


import com.android.SdkConstants;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.mock.MockLog;
import com.android.sdklib.repository.PkgProps;
import com.android.utils.ILogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import junit.framework.TestCase;

/**
 * Test case that allocates a temporary SDK, a temporary AVD base folder
 * with an SdkManager and an AvdManager that points to them.
 */
public class SdkManagerTestCase extends TestCase {

    private File mFakeSdk;
    private MockLog mLog;
    private SdkManager mSdkManager;
    private TmpAvdManager mAvdManager;

    /** Returns the {@link MockLog} for this test case. */
    public MockLog getLog() {
        return mLog;
    }

    /** Returns the {@link SdkManager} for this test case. */
    public SdkManager getSdkManager() {
        return mSdkManager;
    }

    /** Returns the {@link AvdManager} for this test case. */
    public TmpAvdManager getAvdManager() {
        return mAvdManager;
    }

    /**
     * Sets up a {@link MockLog}, a fake SDK in a temporary directory
     * and an AVD Manager pointing to an initially-empty AVD directory.
     */
    @Override
    public void setUp() throws Exception {
        mLog = new MockLog();
        mFakeSdk = makeFakeSdk();
        mSdkManager = SdkManager.createManager(mFakeSdk.getAbsolutePath(), mLog);
        assertNotNull("SdkManager location was invalid", mSdkManager);

        mAvdManager = new TmpAvdManager(mSdkManager, mLog);
    }

    /**
     * Removes the temporary SDK and AVD directories.
     */
    @Override
    public void tearDown() throws Exception {
        deleteDir(mFakeSdk);
    }

    /**
     * A empty test method to placate the JUnit test runner, which doesn't
     * like TestCase classes with no test methods.
     */
    public void testPlaceholder() {
    }

    /**
     * An {@link AvdManager} that uses a temporary directory
     * located <em>inside</em> the SDK directory for testing.
     * The AVD list should be initially empty.
     */
    protected static class TmpAvdManager extends AvdManager {

        /*
         * Implementation detail:
         * - When the super.AvdManager constructor is invoked, it will invoke
         *   the buildAvdFilesList() to fill the initial AVD list, which will in
         *   turn call getBaseAvdFolder().
         * - That's why mTmpAvdRoot is initialized in getAvdRoot() rather than
         *   in the constructor, since we can't initialize fields before the super()
         *   call.
         */

        /**
         * AVD Root, initialized "lazily" when the AVD root is first requested.
         */
        private File mTmpAvdRoot;

        public TmpAvdManager(SdkManager sdkManager, ILogger log) throws AndroidLocationException {
            super(sdkManager, log);
        }

        @Override
        public String getBaseAvdFolder() throws AndroidLocationException {
            if (mTmpAvdRoot == null) {
                mTmpAvdRoot = new File(getSdkManager().getLocation(), "tmp_avds");
                mTmpAvdRoot.mkdirs();
            }
            return mTmpAvdRoot.getAbsolutePath();
        }
    }

    /**
     * Build enough of a skeleton SDK to make the tests pass.
     * <p/>
     * Ideally this wouldn't touch the file system but the current
     * structure of the SdkManager and AvdManager makes this difficult.
     *
     * @return Path to the temporary SDK root
     * @throws IOException
     */
    private File makeFakeSdk() throws IOException {
        // First we create a temp file to "reserve" the temp directory name we want to use.
        File sdkDir = File.createTempFile(
                this.getClass().getSimpleName() + '_' + this.getName(), null);
        // Then erase the file and make the directory
        sdkDir.delete();
        sdkDir.mkdirs();

        AndroidLocation.resetFolder();
        File addonsDir = new File(sdkDir, SdkConstants.FD_ADDONS);
        addonsDir.mkdir();

        File toolsDir = new File(sdkDir, SdkConstants.OS_SDK_TOOLS_FOLDER);
        toolsDir.mkdir();
        new File(toolsDir, SdkConstants.androidCmdName()).createNewFile();
        new File(toolsDir, SdkConstants.FN_EMULATOR).createNewFile();

        // TODO makePlatformTools with at least a source props

        File toolsLibEmuDir = new File(sdkDir, SdkConstants.OS_SDK_TOOLS_LIB_FOLDER + "emulator");
        toolsLibEmuDir.mkdirs();
        new File(toolsLibEmuDir, "snapshots.img").createNewFile();
        File platformsDir = new File(sdkDir, SdkConstants.FD_PLATFORMS);

        // Creating a fake target here on down
        File targetDir = makeFakeTargetInternal(platformsDir);

        File imagesDir = new File(targetDir, "images");
        makeFakeSysImgInternal(imagesDir, SdkConstants.ABI_ARMEABI);

        makeFakeSkinInternal(targetDir);
        makeFakeSourceInternal(sdkDir);
        return sdkDir;
    }

    /**
     * Creates the system image folder and places a fake userdata.img in it.
     *
     * @param systemImage A system image with a valid location.
     * @throws IOException if the file fails to be created.
     */
    protected void makeSystemImageFolder(ISystemImage systemImage) throws IOException {
        File imagesDir = systemImage.getLocation();
        imagesDir.mkdirs();

        makeFakeSysImgInternal(imagesDir, systemImage.getAbiType());
    }

    //----

    /** Utility used by {@link #makeFakeSdk()} to create a fake target with API 0, rev 0. */
    private File makeFakeTargetInternal(File platformsDir) throws IOException {
        File targetDir = new File(platformsDir, "v0_0");
        targetDir.mkdirs();
        new File(targetDir, SdkConstants.FN_FRAMEWORK_LIBRARY).createNewFile();
        new File(targetDir, SdkConstants.FN_FRAMEWORK_AIDL).createNewFile();

        File sourceProp = new File(targetDir, SdkConstants.FN_SOURCE_PROP);
        sourceProp.createNewFile();
        FileWriter out = new FileWriter(sourceProp);
        out.write(PkgProps.LAYOUTLIB_API + "=5\n");
        out.write(PkgProps.LAYOUTLIB_REV + "=2\n");
        out.close();

        File buildProp = new File(targetDir, SdkConstants.FN_BUILD_PROP);
        out = new FileWriter(buildProp);
        out.write(SdkManager.PROP_VERSION_RELEASE + "=0.0\n");
        out.write(SdkManager.PROP_VERSION_SDK + "=0\n");
        out.write(SdkManager.PROP_VERSION_CODENAME + "=REL\n");
        out.close();
        return targetDir;
    }

    /** Utility to create a fake sys image in the given folder. */
    private void makeFakeSysImgInternal(File imagesDir, String abiType) throws IOException {
        imagesDir.mkdirs();
        new File(imagesDir, "userdata.img").createNewFile();

        File sourceProp = new File(imagesDir, SdkConstants.FN_SOURCE_PROP);
        sourceProp.createNewFile();
        FileWriter out = new FileWriter(sourceProp);
        out.write(PkgProps.VERSION_API_LEVEL + "=0\n");
        out.write(PkgProps.SYS_IMG_ABI + "=" + abiType + "\n");
        out.close();
    }

    /** Utility to make a fake skin for the given target */
    private void makeFakeSkinInternal(File targetDir) {
        FileOp.append(targetDir, "skins", "HVGA").mkdirs();
    }

    /** Utility to create a fake source with a few files in the given sdk folder. */
    private void makeFakeSourceInternal(File sdkDir) throws IOException {
        File sourcesDir = FileOp.append(sdkDir, SdkConstants.FD_PKG_SOURCES, "android-0");
        sourcesDir.mkdirs();

        File sourceProp = new File(sourcesDir, SdkConstants.FN_SOURCE_PROP);
        sourceProp.createNewFile();
        FileWriter out = new FileWriter(sourceProp);
        out.write(PkgProps.VERSION_API_LEVEL + "=0\n");
        out.close();

        File dir1 = FileOp.append(sourcesDir, "src", "com", "android");
        dir1.mkdirs();
        FileOp.append(dir1, "File1.java").createNewFile();
        FileOp.append(dir1, "File2.java").createNewFile();

        FileOp.append(sourcesDir, "res", "values").mkdirs();
        FileOp.append(sourcesDir, "res", "values", "styles.xml").createNewFile();
    }

    /**
     * Recursive delete directory. Mostly for fake SDKs.
     *
     * @param root directory to delete
     */
    private void deleteDir(File root) {
        if (root.exists()) {
            for (File file : root.listFiles()) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
            root.delete();
        }
    }

}
