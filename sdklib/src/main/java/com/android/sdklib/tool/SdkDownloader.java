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

import com.android.annotations.NonNull;
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
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple tool for downloading SDK packages, to be used in the windows studio release process.
 *
 * Can be run from the commandline like:
 * {@code java -cp plugins/android/lib/repository.jar:plugins/android/lib/sdklib.jar:\
 *          plugins/android/lib/common.jar:lib/guava-17.0.jar:lib/httpcore-4.4.1.jar:\
 *          lib/httpclient-4.4.1.jar:lib/commons-logging-1.2.jar \
 *          com.android.sdklib.tool.SdkDownloader <sdkdir> <paths...>}
 */
public class SdkDownloader {

    public static void main(@NonNull String args[]) {
        Settings settings = Settings.createSettings(args);
        if (settings == null) {
            usageAndExit();
        }

        File localPath = settings.getLocalPath();
        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(localPath);
        RepoManager mgr = handler.getSdkManager(progress);
        LegacyDownloader downloader = new LegacyDownloader(handler.getFileOp());
        mgr.loadSynchronously(0, progress, downloader, settings);
        RepositoryPackages packages = mgr.getPackages();
        List<RemotePackage> remotes = Lists.newArrayList();
        for (String path : settings.mPackages) {
            RemotePackage p = packages.getRemotePackages().get(path);
            if (p == null) {
                progress.logError("Failed to find package " + path);
                usageAndExit();
            }
            remotes.add(p);
        }
        remotes = InstallerUtil.computeRequiredPackages(remotes, packages, progress);
        for (RemotePackage p : remotes) {
            License l = p.getLicense();
            if (l != null) {
                if (!l.checkAccepted(localPath, handler.getFileOp())) {
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
        System.out.println("Usage: java com.android.sdklib.tool.SdkDownloader "
          + "[--channel=channelId] <sdk path> "
          + "<package path> <package path>...\n"
          + "    <package path> is a sdk-style path (e.g. build-tools;23.0.0 or "
          + "platforms;android-23)\n"
          + "    channelId is the id of the least stable channel to check.");
        System.exit(1);
    }

    private static class Settings implements SettingsController {

        private static final String CHANNEL_ARG = "--channel=";

        private File mLocalPath;
        private List<String> mPackages = new ArrayList<>();
        private int mChannel = 0;

        @Nullable
        public static Settings createSettings(@NonNull String[] args) {
            Settings result = new Settings();
            for (String arg : args) {
                if (arg.startsWith(CHANNEL_ARG)) {
                    try {
                        result.mChannel = Integer.parseInt(arg.substring(CHANNEL_ARG.length()));
                    }
                    catch (NumberFormatException e) {
                        return null;
                    }
                }
                else if (result.mLocalPath == null) {
                    File path = new File(arg);
                    ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
                    if (!path.exists()) {
                        if (!path.mkdirs()) {
                            progress.logError("Failed to create SDK root dir: " + path);
                            return null;
                        }
                    }
                    result.mLocalPath = path;
                }
                else {
                    result.mPackages.add(arg);
                }
            }
            if (result.mLocalPath == null || result.mPackages.isEmpty()) {
                return null;
            }
            return result;
        }

        @Nullable
        @Override
        public Channel getChannel() {
            return Channel.create(mChannel);
        }

        @Override
        public boolean getForceHttp() {
            return false;
        }

        @Override
        public void setForceHttp(boolean force) {

        }

        @NonNull
        public List<String> getPaths() {
            return mPackages;
        }

        @NonNull
        public File getLocalPath() {
            return mLocalPath;
        }

        private Settings() {}
    }
}
