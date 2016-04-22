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
package com.android.sdklib.tool;

import com.android.annotations.Nullable;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Installer;
import com.android.repository.api.License;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.sdklib.repository.legacy.LegacyRemoteRepoLoader;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * Simple tool for downloading SDK packages, to be used in the windows studio release process.
 *
 * Can be run from the commandline like:
 * java -cp plugins/android/lib/repository.jar:plugins/android/lib/sdklib.jar:\
 *          plugins/android/lib/common.jar:lib/guava-17.0.jar:lib/httpcore-4.4.1.jar:\
 *          lib/httpclient-4.4.1.jar:lib/commons-logging-1.2.jar \
 *          com.android.sdklib.tool.SdkDownloader <sdkdir> <paths...>
 */
public class SdkDownloader {

    public static void main(String args[]) {
        if (args.length < 2) {
            usageAndExit();
        }
        String local = args[0];
        File localPath = new File(local);
        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
        if (!localPath.exists()) {
            if (!localPath.mkdirs()) {
                progress.logError("Failed to create SDK root dir: " + local);
                usageAndExit();
            }
        }
        Settings settingsController = new Settings();
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(localPath);
        RepoManager mgr = handler.getSdkManager(progress);
        LegacyDownloader downloader = new LegacyDownloader(handler.getFileOp());
        mgr.loadSynchronously(0, progress, downloader, settingsController);
        RepositoryPackages packages = mgr.getPackages();
        List<RemotePackage> remotes = Lists.newArrayList();
        for (int i = 1; i < args.length; i++) {
            RemotePackage p = packages.getRemotePackages().get(args[i]);
            if (p == null) {
                progress.logError("Failed to find package " + args[i]);
                usageAndExit();
            }
            remotes.add(p);
        }
        remotes = InstallerUtil.computeRequiredPackages(remotes, packages, progress);
        for (RemotePackage p : remotes) {
            License l = p.getLicense();
            if (l != null) {
                if (!l.checkAccepted(localPath)) {
                    progress.logError(String.format(
                            "License for %1$s (%2$s) is not accepted. Please install using "
                                    + "studio, then copy <studio sdk path>/licenses/* to "
                                    + "%3$s/licenses/",
                            p.getDisplayName(), p.getPath(), localPath));
                    System.exit(1);
                }
            }
            Installer installer = SdkInstallerUtil.findBestInstallerFactory(p, handler)
                    .createInstaller(p, mgr, handler.getFileOp());
            if (!installer.prepareInstall(downloader, progress)) {
                System.exit(1);
            }
            if (!installer.completeInstall(progress)) {
                System.exit(1);
            }
        }
        progress.logInfo("done");
    }

    private static void usageAndExit() {
        System.out.println("Usage: java com.android.sdklib.tool.SdkDownloader <sdk path> "
                + "<package path> <package path>...\n"
                + "Where <package path> is a sdk-style path (e.g. build-tools;23.0.0 or "
                + "platforms;android-23)");
        System.exit(1);
    }

    private static class Settings implements SettingsController {

        @Nullable
        @Override
        public Channel getChannel() {
            return Channel.create(0);
        }

        @Override
        public boolean getForceHttp() {
            return false;
        }

        @Override
        public void setForceHttp(boolean force) {

        }
    }
}
