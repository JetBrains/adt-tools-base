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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;

/**
 * A utility class that finds {@link LocalPackage}s under a given path based on {@code package.xml}
 * files.
 */
public final class LocalRepoLoaderImpl implements LocalRepoLoader {

    /**
     * The name of the package metadata file we can read.
     */
    public static final String PACKAGE_XML_FN = "package.xml";

    /**
     * The maximum depth we'll descend into the directory tree while looking for packages. TODO:
     * adjust once the path of the current deepest package is known (e.g. maven packages).
     */
    private static final int MAX_SCAN_DEPTH = 10;

    /**
     * Cache of found packages.
     */
    private Map<String, LocalPackage> mPackages = null;

    /**
     * Set of directories we think probably have packages in them.
     */
    private Set<File> mPackageRoots = null;

    /**
     * Directory under which we look for packages.
     */
    private final File mRoot;

    private final RepoManager mRepoManager;

    private final FileOp mFop;

    /**
     * If we can't find a package in a directory, we ask mFallback to find one. If it does, we write
     * out a {@code package.xml} so we can read it next time.
     */
    private FallbackLocalRepoLoader mFallback;

    /**
     * Constructor. Probably should only be used within repository framework.
     *
     * @param root     The root directory under which we'll look for packages.
     * @param manager  A RepoManager, notably containing the {@link SchemaModule}s we'll use for
     *                 reading and writing {@link LocalPackage}s
     * @param fallback The {@link FallbackLocalRepoLoader} we'll use if we can't find a package in a
     *                 directory.
     * @param fop      The {@link FileOp} to use for file operations. Should be
     *                 {@link FileOpUtils#create()} for normal operation.
     */
    public LocalRepoLoaderImpl(@NonNull File root, @NonNull RepoManager manager,
            @Nullable FallbackLocalRepoLoader fallback, @NonNull FileOp fop) {
        mRoot = root;
        mRepoManager = manager;
        mFop = fop;
        mFallback = fallback;
    }

    /**
     * Gets a hash of the known (suspected) package directories. In order to be as fast as possible
     * this doesn't include the content of the packages or package metadata file, just the
     * directories paths themselves.
     */
    @Override
    @Nullable
    public byte[] getLocalPackagesHash() {
        Set<File> dirs = collectPackages();
        try {
            MessageDigest digester = MessageDigest.getInstance("md5");
            for (File f : dirs) {
                digester.update(f.getAbsolutePath().getBytes());
            }
            return digester.digest();
        }
        catch (NoSuchAlgorithmException e) {
            // shouldn't happen
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * For our purposes, we use the update timestamp of the {@code package.xml} file.
     */
    @Override
    public long getLatestPackageUpdateTime() {
        long latest = 0;
        for (File f : collectPackages()) {
            long t = mFop.lastModified(f);
            latest = t > latest ? t : latest;
        }
        return latest;
    }

    @Override
    @NonNull
    public Map<String, LocalPackage> getPackages(@NonNull ProgressIndicator progress) {
        if (mPackages == null) {
            Set<File> possiblePackageDirs = collectPackages();
            mPackages = parsePackages(possiblePackageDirs, progress);
        }
        return Collections.unmodifiableMap(mPackages);
    }

    @NonNull
    private Map<String, LocalPackage> parsePackages(@NonNull Collection<File> possiblePackageDirs,
            @NonNull ProgressIndicator progress) {
        Map<String, LocalPackage> result = Maps.newHashMap();
        for (File packageDir : possiblePackageDirs) {
            File packageXml = new File(packageDir, PACKAGE_XML_FN);
            LocalPackage p = null;
            if (mFop.exists(packageXml)) {
                try {
                    p = parsePackage(packageXml, progress);
                }
                catch (Exception e) {
                    // There was a problem parsing the package. Try the fallback loader.
                    progress.logWarning("Found corrupted package.xml at " + packageXml);
                }
            }
            if (p == null && mFallback != null) {
                p = mFallback.parseLegacyLocalPackage(packageDir, progress);
                if (p != null) {
                    writePackage(p, packageXml, progress);
                }
                else if (mFop.exists(packageXml)) {
                    progress.logWarning(String.format(
                      "Invalid package.xml found at %1$s and failed to parse using fallback.", packageXml));
                /*
                TODO: decide what the behavior should be when an xml is consistently unparsable.
                      Leaving it as-is (the above code) will cause there to be a warning each time
                      we try to parse the package. But renaming it means we never get a chance
                      (e.g. with a future version of the code) to try to recover.
                File bad = new File(packageXml.getPath() + ".bad");
                progress.logWarning(String.format(
                        "Invalid package.xml found and failed to parse using fallback. Renaming %1$s to %2$s",
                        packageXml, bad));
                mFop.renameTo(packageXml, bad);
                */
                }
            }
            if (p != null) {
                addPackage(p, result, progress);
            }
        }
        return result;
    }

    /**
     * Gets a sorted set of all paths that might contain packages.
     */
    @NonNull
    private Set<File> collectPackages() {
        if (mPackageRoots == null) {
            Set<File> dirs = Sets.newTreeSet();
            collectPackages(dirs, mRoot, 0);
            mPackageRoots = dirs;
        }
        return mPackageRoots;
    }

    /**
     * Collect packages under the given root into {@code collector}.
     *
     * @param collector The collector.
     * @param root      Directory we're looking in.
     * @param depth     The depth we've descended to so far. Once we reach {@link #MAX_SCAN_DEPTH}
     *                  we'll stop recursing.
     */
    private void collectPackages(@NonNull Collection<File> collector, @NonNull File root, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        File packageXml = new File(root, PACKAGE_XML_FN);
        if (mFop.exists(packageXml) ||
            (mFallback != null && mFallback.shouldParse(root))) {
            collector.add(root);
        } else {
            for (File f : mFop.listFiles(root)) {
                if (mFop.isDirectory(f)) {
                    collectPackages(collector, f, depth + 1);
                }
            }
        }
    }

    private void addPackage(@NonNull LocalPackage p, @NonNull Map<String, LocalPackage> collector,
            @NonNull ProgressIndicator progress) {
        String filePath = p.getPath().replace(RepoPackage.PATH_SEPARATOR, File.separatorChar);
        File desired = new File(mRoot, filePath);
        File actual = p.getLocation();
        if (!desired.equals(actual)) {
            progress.logWarning(String.format(
                    "Observed package id '%1$s' in inconsistent location '%2$s' (Expected '%3$s')",
                    p.getPath(), actual.getPath(), desired.getPath()));
            LocalPackage existing = collector.get(p.getPath());
            if (existing != null) {
                progress.logWarning(String.format(
                        "Already observed package id '%1$s' in '%2$s'. Skipping duplicate at '%3$s'",
                        p.getPath(), existing.getLocation().getPath(), actual.getPath()));
                return;
            }
        }
        collector.put(p.getPath(), p);
    }

    /**
     * If the {@link FallbackLocalRepoLoader} finds a package, we write out a package.xml so we can
     * load it next time without falling back.
     *
     * @param p          The {@link LocalPackage} to write out.
     * @param packageXml The destination to write to.
     * @param progress   {@link ProgressIndicator} for logging.
     */
    private void writePackage(@NonNull LocalPackage p, @NonNull File packageXml,
            @NonNull ProgressIndicator progress) {
        // We need a LocalPackageImpl to be able to save it.
        LocalPackageImpl impl = LocalPackageImpl.create(p);
        OutputStream fos = null;
        try {
            fos = mFop.newFileOutputStream(packageXml);
            Repository repo = impl.createFactory().createRepositoryType();
            repo.setLocalPackage(impl);
            License license = impl.getLicense();
            if (license != null) {
                repo.addLicense(license);
            }

            CommonFactory factory = ((CommonFactory) RepoManager.getCommonModule()
              .createLatestFactory());
            SchemaModuleUtil.marshal(factory.generateRepository(repo),
                                     mRepoManager.getSchemaModules(), fos,
                                     mRepoManager.getResourceResolver(progress), progress);
        } catch (IOException e) {
            progress.logInfo("Exception while marshalling " + packageXml
                    + ". Probably the SDK is read-only");
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // ignore.
                }
            }
        }
    }

    /**
     * Unmarshal a package.xml file and extract the {@link LocalPackage}.
     */
    @Nullable
    private LocalPackage parsePackage(@NonNull File packageXml,
            @NonNull ProgressIndicator progress) throws JAXBException {
        Repository repo;
        try {
            progress.logInfo("Parsing " + packageXml);
            repo = (Repository) SchemaModuleUtil.unmarshal(mFop.newFileInputStream(packageXml),
                    mRepoManager.getSchemaModules(), mRepoManager.getResourceResolver(progress),
                    false, progress);
        } catch (IOException e) {
            // This shouldn't ever happen
            progress.logError(String.format("XML file %s doesn't exist", packageXml), e);
            return null;
        }
        if (repo == null) {
            progress.logWarning(String.format("Failed to parse %s", packageXml));
            return null;
        } else {
            LocalPackage p = repo.getLocalPackage();
            if (p == null) {
                progress.logWarning("Didn't find any local package in repository");
                return null;
            }
            p.setInstalledPath(packageXml.getParentFile());
            return p;
        }
    }
}
