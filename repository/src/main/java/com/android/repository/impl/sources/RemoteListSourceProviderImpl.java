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

package com.android.repository.impl.sources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RemoteSource;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.w3c.dom.ls.LSResourceResolver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;

/**
 * A {@link RepositorySourceProvider} that downloads a list of sources.
 */
public class RemoteListSourceProviderImpl extends RemoteListSourceProvider {

    /**
     * The {@link SchemaModule} specifying the core schema for the downloaded list of sources. This
     * may be extended by the {@link SchemaModule} specified in the constructor.
     */
    private static SchemaModule sAddonListModule;

    /**
     * The URL to download from.
     */
    private final String mUrl;

    /**
     * The {@link SchemaModule}s that are allowed to be used, depending on the type of the source.
     */
    private final Map<Class<? extends RepositorySource>, Collection<SchemaModule>>
            mAllowedModules;

    /**
     * An extension to the core {@link SchemaModule}.
     */
    private final SchemaModule mSourceListModule;

    /**
     * Cached list of source.
     */
    private List<RepositorySource> mSources;

    /**
     * Create a {@code RemoteListSourceProviderImpl}
     *
     * @param url                    The URL to download from
     * @param sourceListModule       The concrete {@link SchemaModule} specifying the schema of the
     *                               downloaded file.
     * @param permittedSchemaModules Map of concrete {@link RepositorySource} type, as defined in
     *                               {@code sourceListModule}, to collection of {@link
     *                               SchemaModule}s allowed to be used by that source type.
     * @throws URISyntaxException If {@code url} can't be parsed into a URL.
     */
    public RemoteListSourceProviderImpl(@NonNull String url, @NonNull SchemaModule sourceListModule,
            @NonNull Map<Class<? extends RepositorySource>,
                         Collection<SchemaModule>> permittedSchemaModules)
            throws URISyntaxException {
        mUrl = url;
        mAllowedModules = permittedSchemaModules;
        if (sAddonListModule == null) {
            try {
                sAddonListModule = new SchemaModule(
                        getClass().getPackage().getName() + ".generated.v%d.ObjectFactory",
                        "repo-sites-common-%d.xsd", RepoManager.class);
            } catch (InstantiationException e) {
                assert false : "Problem instantiating repo-sites-common schema module!";
            }
        }
        mSourceListModule = sourceListModule;
    }

    /**
     * Gets the sources from this provider.
     *
     * @param downloader         The {@link Downloader} to use to download the source list.
     * @param settingsController The {@link SettingsController} to use for the download settings.
     * @param progress           {@link ProgressIndicator} for logging.
     * @param forceRefresh       If true, this provider should refresh its list of sources, rather
     *                           than return cached ones.
     * @return The fetched {@link RepositorySource}s.
     */
    @NonNull
    @Override
    public List<RepositorySource> getSources(@NonNull Downloader downloader,
            @Nullable SettingsController settingsController, @NonNull ProgressIndicator progress,
            boolean forceRefresh) {
        if (mSources != null && !forceRefresh) {
            return mSources;
        }

        List<RepositorySource> result;
        InputStream xml = null;

        URL url = null;
        for (int version = mSourceListModule.getNamespaceVersionMap().size();
                xml == null && version > 0; version--) {
            String urlStr = String.format(mUrl, version);
            try {
                url = new URL(urlStr);
                xml = downloader.download(url, settingsController, progress);
            } catch (FileNotFoundException expected) {
                // do nothing
            } catch (UnknownHostException e) {
                progress.logWarning("Failed to connect to host: " + urlStr);
            } catch (MalformedURLException e) {
                progress.logWarning("Invalid URL: " + urlStr);
            } catch (IOException e) {
                progress.logWarning("IOException: " + urlStr, e);
            }
        }

        if (xml != null) {
            result = parse(xml, progress, url);
            if (result != null) {
                mSources = result;
                return mSources;
            }
        }

        return ImmutableList.of();
    }

    @NonNull
    private List<RepositorySource> parse(@NonNull InputStream xml,
            @NonNull ProgressIndicator progress, @NonNull URL url) {
        LSResourceResolver resourceResolver = SchemaModuleUtil
                .createResourceResolver(
                        ImmutableList.of(sAddonListModule, mSourceListModule));
        SiteList sl = (SiteList) SchemaModuleUtil
                .unmarshal(xml, ImmutableList.of(mSourceListModule), resourceResolver, progress);
        List<RepositorySource> result = Lists.newArrayList();
        for (RemoteSource s : sl.getSite()) {
            for (Class<? extends RepositorySource> c : mAllowedModules.keySet()) {
                if (c.isInstance(s)) {
                    s.setPermittedSchemaModules(mAllowedModules.get(c));
                }
            }
            String urlStr = s.getUrl();
            try {
                URL fullUrl = new URL(url, urlStr);
                s.setUrl(fullUrl.toExternalForm());
            } catch (MalformedURLException e) {
                progress.logWarning("Failed to parse URL in remote source list", e);
            }
            s.setProvider(this);
            result.add(s);
        }

        return result;
    }

    /**
     * Not supported by this provider.
     *
     * @return {@code false}.
     */
    @Override
    public boolean addSource(@NonNull RepositorySource source) {
        throw new UnsupportedOperationException("Can't add to RemoteListSourceProvider");
    }

    /**
     * Not supported by this provider.
     *
     * @return {@code false}.
     */
    @Override
    public boolean isModifiable() {
        return false;
    }

    /**
     * Not supported by this provider.
     */
    @Override
    public void save(@NonNull ProgressIndicator progress) {
        // Nothing, not modifiable.
    }

    /**
     * Not supported by this provider.
     *
     * @return {@code false}.
     */
    @Override
    public boolean removeSource(@NonNull RepositorySource source) {
        throw new UnsupportedOperationException("Can't add to RemoteListSourceProvider");
    }

    /**
     * Superclass for xjc-created JAXB-usable classes into which a site list can be unmarshalled.
     */
    @XmlTransient
    public static class SiteList {

        /**
         * Gets the parsed list of {@link RemoteSource}s.
         */
        @NonNull
        public List<RemoteSource> getSite() {
            // Stub. Implementation provided to fall back to older versions.
            //noinspection unchecked
            return (List) getAddonSiteOrSysImgSite();
        }

        @NonNull
        protected List<Object> getAddonSiteOrSysImgSite() {
            // Stub. for backward compatibility only.
            //noinspection unchecked
            return (List) getAddonSite();
        }

        @NonNull
        protected List<RemoteSource> getAddonSite() {
            // Stub. for backward compatibility only
            throw new UnsupportedOperationException();
        }
    }

}
