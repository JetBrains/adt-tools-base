package com.android.sdklib.repositoryv2.targets;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.local.PackageParserUtils;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.sdklib.repositoryv2.meta.SysImgFactory;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@code SystemImageManager} finds {@link ISystemImage}s in the sdk, either using a
 * {@link RepoManager} or (until adoption of {@link RepoManager} is complete), a {@link LocalSdk}.
 */
public class SystemImageManager {

    /**
     * Implementation of the system image manager logic, either using a {@link RepoManager} or
     * {@link LocalSdk}.
     */
    private SystemImageManagerImpl mImpl;

    /**
     * The known system images and their associated packages.
     */
    private Map<ISystemImage, LocalPackage> mImageToPackage;

    /**
     * Map of tag, version, and vendor to set of system image, for convenient lookup.
     */
    private Table<IdDisplay, AndroidVersion, Multimap<IdDisplay, ISystemImage>> mValuesToImage;

    /**
     * Create a new {@code SystemImageManager} using the given (legacy) {@link LocalSdk}.
     * This should be removed once adoption of {@link RepoManager} is complete.
     */
    public SystemImageManager(@NonNull LocalSdk sdk) {
        mImpl = new OldImpl(sdk);
    }

    /**
     * Create a new {@link SystemImageManager} using the given {@link RepoManager}.<br/>
     * {@code factory} is used to enable validation.
     */
    public SystemImageManager(@NonNull RepoManager mgr, @NonNull SysImgFactory factory,
            @NonNull FileOp fop) {
        mImpl = new NewImpl(mgr, factory, fop);
    }

    /**
     * Gets all the {@link ISystemImage}s.
     */
    @NonNull
    public Collection<ISystemImage> getImages(@NonNull ProgressIndicator progress) {
        if (mImageToPackage == null) {
            init(progress);
        }
        return mImageToPackage.keySet();
    }

    /**
     * Gets a map from all our {@link ISystemImage}s to their containing {@link LocalPackage}s.
     */
    @NonNull
    public Map<ISystemImage, LocalPackage> getImageMap(@NonNull ProgressIndicator progress) {
        return mImpl.getImageMap(progress);
    }

    /**
     * Lookup all the {@link ISystemImage} with the given property values.
     */
    @NonNull
    public Collection<ISystemImage> lookup(@NonNull IdDisplay tag, @NonNull AndroidVersion version,
            @Nullable IdDisplay vendor, @NonNull ProgressIndicator progress) {
        if (mValuesToImage == null) {
            init(progress);
        }
        Multimap<IdDisplay, ISystemImage> m = mValuesToImage.get(tag, version);
        return m == null ? ImmutableList.<ISystemImage>of() : m.get(vendor);
    }

    /**
     * Initialze our maps using our {@link SystemImageManagerImpl}.
     */
    private void init(@NonNull ProgressIndicator progress) {
        Map<ISystemImage, LocalPackage> images = mImpl.getImageMap(progress);
        Table<IdDisplay, AndroidVersion, Multimap<IdDisplay, ISystemImage>> valuesToImage =
                HashBasedTable.create();
        for (ISystemImage img : images.keySet()) {
            IdDisplay vendor = img.getAddonVendor();
            IdDisplay tag = img.getTag();
            // TODO: simplify after adoption: get version directly from the image.
            AndroidVersion version = mImpl.getVersion(img);
            Multimap<IdDisplay, ISystemImage> vendorImageMap = valuesToImage.get(tag, version);
            if (vendorImageMap == null) {
                vendorImageMap = HashMultimap.create();
                valuesToImage.put(tag, version, vendorImageMap);
            }
            vendorImageMap.put(vendor, img);
        }
        mValuesToImage = valuesToImage;
        mImageToPackage = images;
    }

    private interface SystemImageManagerImpl {

        @NonNull
        Map<ISystemImage, LocalPackage> getImageMap(@NonNull ProgressIndicator progress);

        // TODO: remove after adoption
        @Deprecated
        @NonNull
        AndroidVersion getVersion(@NonNull ISystemImage img);
    }

    // TODO: remove after adoption
    @Deprecated
    private static class OldImpl implements SystemImageManagerImpl {

        private final LocalSdk mLocalSdk;

        private final Map<ISystemImage, AndroidVersion> mImageVersionMap = Maps.newHashMap();

        public OldImpl(LocalSdk sdk) {
            mLocalSdk = sdk;
        }

        @NonNull
        @Override
        public Map<ISystemImage, LocalPackage> getImageMap(@NonNull ProgressIndicator progress) {
            Map<ISystemImage, LocalPackage> result = Maps.newHashMap();
            for (IAndroidTarget target : mLocalSdk.getTargets(true)) {
                ISystemImage[] systemImages = target.getSystemImages();
                if (systemImages != null) {
                    for (ISystemImage img : systemImages) {
                        result.put(img, null);
                        mImageVersionMap.put(img, target.getVersion());
                    }
                }
            }
            return result;
        }

        @NonNull
        @Override
        public AndroidVersion getVersion(@NonNull ISystemImage img) {
            return mImageVersionMap.get(img);
        }
    }

    private static class NewImpl implements SystemImageManagerImpl {

        private final FileOp mFop;

        private final RepoManager mRepoManager;

        private final DetailsTypes.SysImgDetailsType mValidator;

        private static final String SYS_IMG_NAME = "system.img";

        private static final int MAX_DEPTH = 5;

        public NewImpl(RepoManager repoManager, SysImgFactory factory, FileOp fop) {
            mFop = fop;
            mRepoManager = repoManager;
            mValidator = factory.createSysImgDetailsType();
        }

        @NonNull
        @Override
        public Map<ISystemImage, LocalPackage> getImageMap(@NonNull ProgressIndicator progress) {
            mRepoManager
                    .loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, null,
                            null);
            Map<ISystemImage, LocalPackage> result = Maps.newHashMap();
            Map<AndroidVersion, File> platformSkins = Maps.newHashMap();
            for (LocalPackage p : mRepoManager.getPackages().getLocalPackages().values()) {
                if (p.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType) {
                    File skinDir = new File(p.getLocation(), SdkConstants.FD_SKINS);
                    if (mFop.exists(skinDir)) {
                        platformSkins.put(DetailsTypes.getAndroidVersion(
                                (DetailsTypes.PlatformDetailsType) p.getTypeDetails()), skinDir);
                    }
                }
            }
            for (LocalPackage p : mRepoManager.getPackages().getLocalPackages().values()) {
                collectImages(p.getLocation(), p, 0, platformSkins, result);
            }
            return result;
        }

        @NonNull
        @Override
        public AndroidVersion getVersion(@NonNull ISystemImage img) {
            return ((SystemImage) img).getAndroidVersion();
        }

        private void collectImages(File dir, LocalPackage p, int depth,
                Map<AndroidVersion, File> platformSkins,
                Map<ISystemImage, LocalPackage> collector) {
            for (File f : mFop.listFiles(dir)) {
                if (f.getName().equals(SYS_IMG_NAME)) {
                    collector.put(createSysImg(p, dir, platformSkins), p);
                }
                if (mFop.isDirectory(f) && depth < MAX_DEPTH) {
                    collectImages(f, p, depth + 1, platformSkins, collector);
                }
            }
        }

        private SystemImage createSysImg(LocalPackage p, File dir,
                Map<AndroidVersion, File> platformSkins) {
            String containingDir = dir.getName();
            String abi;
            TypeDetails details = p.getTypeDetails();
            AndroidVersion version = null;
            if (details instanceof DetailsTypes.ApiDetailsType) {
                version = DetailsTypes.getAndroidVersion((DetailsTypes.ApiDetailsType) details);
            }
            if (details instanceof DetailsTypes.SysImgDetailsType) {
                abi = ((DetailsTypes.SysImgDetailsType) details).getAbi();
            } else if (mValidator.isValidAbi(containingDir)) {
                abi = containingDir;
            } else {
                abi = SdkConstants.ABI_ARMEABI;
            }

            IdDisplay tag;
            IdDisplay vendor = null;
            if (details instanceof DetailsTypes.AddonDetailsType) {
                vendor = ((DetailsTypes.AddonDetailsType) details).getVendor();
            } else if (details instanceof DetailsTypes.SysImgDetailsType) {
                vendor = ((DetailsTypes.SysImgDetailsType) details).getVendor();
            }

            if (details instanceof DetailsTypes.SysImgDetailsType) {
                tag = ((DetailsTypes.SysImgDetailsType) details).getTag();
            } else if (details instanceof DetailsTypes.AddonDetailsType) {
                tag = ((DetailsTypes.AddonDetailsType) details).getTag();
            } else {
                tag = new com.android.sdklib.repository.descriptors.IdDisplay("default", "Default");
            }

            File skinDir = new File(dir, SdkConstants.FD_SKINS);
            if (!mFop.exists(skinDir) && version != null) {
                skinDir = platformSkins.get(version);
            }
            File[] skins;
            if (skinDir != null) {
                List<File> skinList = PackageParserUtils.parseSkinFolder(skinDir, mFop);
                skins = skinList.toArray(new File[skinList.size()]);
            } else {
                skins = new File[0];
            }
            return new SystemImage(dir, tag, vendor, abi, skins, p);
        }
    }
}
