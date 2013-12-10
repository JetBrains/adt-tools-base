/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.sdklib.repository.remote;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.internal.repository.DownloadCache;
import com.android.sdklib.internal.repository.NullTaskMonitor;
import com.android.sdklib.internal.repository.packages.Package;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.internal.repository.sources.SdkSources;
import com.android.sdklib.internal.repository.updater.SettingsController;
import com.android.sdklib.internal.repository.updater.SettingsController.OnChangedListener;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


/**
 * This class keeps information on the remote SDK repository.
 */
public class RemoteSdk {

    private DownloadCache mDownloadCache;
    private final SettingsController mSettingsController;

    public RemoteSdk() {
        mSettingsController = initSettingsController();
    }

    /**
     * Fetches the remote list of packages.
     * <p/>
     * This respects the settings from the {@link SettingsController} which
     * dictates whether the {@link DownloadCache} is used and whether HTTP
     * is enforced over HTTPS.
     * <p/>
     * The call may block on network access. Callers will likely want to invoke this
     * from a thread and make sure the logger is thread-safe with regard to UI updates.
     *
     * @param sources The sources to download from.
     * @param logger A logger to report status & progress.
     * @return A non-null potentially map of {@link PkgType} to {@link RemotePkgInfo}
     *         describing the remote packages available for install/download.
     */
    public Multimap<PkgType, RemotePkgInfo> fetch(@NonNull SdkSources sources,
                                                  @NonNull ILogger logger) {

        // TODO loadRemoteAddonsList
        Multimap<PkgType, RemotePkgInfo> remotes = HashMultimap.create();

        boolean forceHttp = getSettingsController().getSettings().getForceHttp();

        // Implementation detail: right now this reuses the SdkSource(s) classes
        // from the sdk-repository v2. The problem with that is that the sources are
        // mutable and hold the fetch logic and hold the packages array.
        // Instead I'd prefer to have the sources be immutable descriptors and move
        // the fetch logic here. Eventually my goal is to get rid of them
        // and include the logic directly here instead but for right now lets
        // just start with what we have to avoid implementing it all at once.
        // It does mean however that this code needs to convert the old Package
        // type into the new RemotePkgInfo type.

        for (SdkSource source : sources.getAllSources()) {
            source.load(getDownloadCache(),
                        new NullTaskMonitor(logger),
                        forceHttp);
            Package[] pkgs = source.getPackages();
            if (pkgs == null || pkgs.length == 0) {
                continue;
            }

            // Adapt the legacy Package instances into the new RemotePkgInfo
            for (Package p : pkgs) {
                IPkgDesc d = p.getPkgDesc();
                RemotePkgInfo r = new RemotePkgInfo(d, source);
                remotes.put(d.getType(), r);
            }
        }

        return remotes;
    }

    /**
     * Returns the {@link DownloadCache}
     * Extracted so that we can override this in unit tests.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected DownloadCache getDownloadCache() {
        if (mDownloadCache == null) {
            mDownloadCache = new DownloadCache(
                    getSettingsController().getSettings().getUseDownloadCache() ?
                            DownloadCache.Strategy.FRESH_CACHE :
                            DownloadCache.Strategy.DIRECT);
        }
        return mDownloadCache;
    }

    /**
     * Returns the {@link SettingsController}
     * Extracted so that we can override this in unit tests.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected SettingsController getSettingsController() {
        return mSettingsController;
    };

    /**
     * Initializes the {@link SettingsController}
     * Extracted so that we can override this in unit tests.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected SettingsController initSettingsController() {
        SettingsController settingsController = new SettingsController(new NullLogger() /* TODO */);
        settingsController.registerOnChangedListener(new OnChangedListener() {
            @Override
            public void onSettingsChanged(
                    SettingsController controller,
                    SettingsController.Settings oldSettings) {

                // Reset the download cache if it doesn't match the right strategy.
                // The cache instance gets lazily recreated later in getDownloadCache().
                mDownloadCache = null;
            }
        });
        return settingsController;
    }

}
