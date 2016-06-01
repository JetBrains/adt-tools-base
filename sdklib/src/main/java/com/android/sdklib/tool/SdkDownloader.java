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
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.api.Uninstaller;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple tool for downloading SDK packages, to be used in the windows studio release process.
 *
 * Can be run from the commandline like:
 * {@code java -cp plugins/android/lib/repository.jar:plugins/android/lib/sdklib.jar:\
 *          plugins/android/lib/common.jar:lib/guava-17.0.jar:lib/httpcore-4.4.1.jar:\
 *          lib/httpclient-4.4.1.jar:lib/commons-logging-1.2.jar:\
 *          plugins/android/lib/commons-compress-1.0.jar
 *          com.android.sdklib.tool.SdkDownloader <sdkdir> (<path> ...|--update)}
 */
public class SdkDownloader {

    public static void main(@NonNull String args[]) {
        Settings settings = Settings.createSettings(args);
        if (settings == null) {
            usageAndExit();
        }

        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(settings.getLocalPath());
        RepoManager mgr = handler.getSdkManager(progress);

        if (settings.isInstallAction()) {
            installPackages(settings, progress, handler, mgr);
        } else {
            uninstallPackages(settings, progress, handler, mgr);
        }
        progress.logInfo("done");
    }

    private static void installPackages(
            @NonNull Settings settings,
            @NonNull ConsoleProgressIndicator progress,
            @NonNull AndroidSdkHandler handler,
            @NonNull RepoManager mgr) {
        LegacyDownloader downloader = new LegacyDownloader(handler.getFileOp());
        mgr.loadSynchronously(0, progress, downloader, settings);

        List<RemotePackage> remotes = Lists.newArrayList();
        for (String path : settings.getPaths(mgr)) {
            RemotePackage p = mgr.getPackages().getRemotePackages().get(path);
            if (p == null) {
                progress.logError("Failed to find package " + path);
                usageAndExit();
            }
            remotes.add(p);
        }
        remotes = InstallerUtil.computeRequiredPackages(remotes, mgr.getPackages(), progress);
        if (remotes != null) {
            for (RemotePackage p : remotes) {
                License l = p.getLicense();
                if (l != null) {
                    if (!l.checkAccepted(handler.getLocation(), handler.getFileOp())) {
                        progress.logError(String.format(
                                "License for %1$s (%2$s) is not accepted. Please install using "
                                        + "studio, then copy <studio sdk path>/licenses/* to "
                                        + "%3$s/licenses/",
                                p.getDisplayName(), p.getPath(), handler.getLocation()));
                        System.exit(1);
                    }
                }
                Installer installer = SdkInstallerUtil.findBestInstallerFactory(p, handler)
                        .createInstaller(p, mgr, downloader, handler.getFileOp());
                applyPackageOperation(installer, progress);
            }
        } else {
            progress.logError("Unable to compute a complete list of dependencies.");
        }
    }

    private static void uninstallPackages(
            @NonNull Settings settings,
            @NonNull ConsoleProgressIndicator progress,
            @NonNull AndroidSdkHandler handler,
            @NonNull RepoManager mgr) {
        mgr.loadSynchronously(0, progress, null, settings);

        for (String path : settings.getPaths(mgr)) {
            LocalPackage p = mgr.getPackages().getLocalPackages().get(path);
            if (p == null) {
                progress.logWarning("Unable to find package " + path);
            } else {
                Uninstaller uninstaller = SdkInstallerUtil.findBestInstallerFactory(p, handler)
                        .createUninstaller(p, mgr, handler.getFileOp());
                applyPackageOperation(uninstaller, progress);
            }
        }
    }

    private static void applyPackageOperation(
            @NonNull PackageOperation operation,
            @NonNull ConsoleProgressIndicator progress) {
        if (!operation.prepare(progress)) {
            System.exit(1);
        }
        if (!operation.complete(progress)) {
            System.exit(1);
        }
    }

    private static void usageAndExit() {
        System.out.println("Usage: java com.android.sdklib.tool.SdkDownloader "
          + "[--uninstall] [--channel=channelId] <sdk path> "
          + "<package path> <package path>...\n"
          + "    <package path> is a sdk-style path (e.g. build-tools;23.0.0 or "
          + "platforms;android-23)\n"
          + "    channelId is the id of the least stable channel to check.");
        System.exit(1);
    }

    private static class Settings implements SettingsController {

        private static final String CHANNEL_ARG = "--channel=";
        private static final String UNINSTALL_ARG = "--uninstall";
        private static final String UPDATE_ARG = "--update";

        private File mLocalPath;
        private List<String> mPackages = new ArrayList<>();
        private int mChannel = 0;
        private boolean mIsInstall = true;
        private boolean mIsUpdate = false;

        @Nullable
        public static Settings createSettings(@NonNull String[] args) {
            Settings result = new Settings();
            for (String arg : args) {
                if (arg.equals(UNINSTALL_ARG)) {
                    result.mIsInstall = false;
                } else if (arg.equals(UPDATE_ARG)) {
                    result.mIsUpdate = true;
                } else if (arg.startsWith(CHANNEL_ARG)) {
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
            if (result.mLocalPath == null || (result.mPackages.isEmpty() && !result.mIsUpdate)) {
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
        public List<String> getPaths(@NonNull RepoManager mgr) {
            if (mIsUpdate) {
                return mgr.getPackages().getUpdatedPkgs().stream()
                        .map(p -> p.getRepresentative().getPath())
                        .collect(Collectors.toList());
            }
            return mPackages;
        }

        @NonNull
        public File getLocalPath() {
            return mLocalPath;
        }

        public boolean isInstallAction() {
            return mIsInstall;
        }

        private Settings() {}
    }
}
