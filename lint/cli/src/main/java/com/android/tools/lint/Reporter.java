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

package com.android.tools.lint;

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.PLATFORM_LINUX;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;
import static java.io.File.separatorChar;

import com.android.annotations.Nullable;
import com.android.utils.SdkUtils;
import com.google.common.annotations.Beta;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A reporter is an output generator for lint warnings
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public abstract class Reporter {
    protected final LintCliClient mClient;
    protected final File mOutput;
    protected String mTitle = "Lint Report";
    protected boolean mSimpleFormat;
    protected boolean mBundleResources;
    protected Map<String, String> mUrlMap;
    protected File mResources;
    protected final Map<File, String> mResourceUrl = new HashMap<File, String>();
    protected final Map<String, File> mNameToFile = new HashMap<String, File>();

    /**
     * Write the given warnings into the report
     *
     * @param errorCount the number of errors
     * @param warningCount the number of warnings
     * @param issues the issues to be reported
     * @throws IOException if an error occurs
     */
    public abstract void write(int errorCount, int warningCount, List<Warning> issues)
            throws IOException;

    protected Reporter(LintCliClient client, File output) {
        mClient = client;
        mOutput = output;
    }

    /**
     * Sets the report title
     *
     * @param title the title of the report
     */
    public void setTitle(String title) {
        mTitle = title;
    }

    /** @return the title of the report */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Sets whether the report should bundle up resources along with the HTML report.
     * This implies a non-simple format (see {@link #setSimpleFormat(boolean)}).
     *
     * @param bundleResources if true, copy images into a directory relative to
     *            the report
     */
    public void setBundleResources(boolean bundleResources) {
        mBundleResources = bundleResources;
        mSimpleFormat = false;
    }

    /**
     * Sets whether the report should use simple formatting (meaning no JavaScript,
     * embedded images, etc).
     *
     * @param simpleFormat whether the formatting should be simple
     */
    public void setSimpleFormat(boolean simpleFormat) {
        mSimpleFormat = simpleFormat;
    }

    /**
     * Returns whether the report should use simple formatting (meaning no JavaScript,
     * embedded images, etc).
     *
     * @return whether the report should use simple formatting
     */
    public boolean isSimpleFormat() {
        return mSimpleFormat;
    }


    String getUrl(File file) {
        if (mBundleResources && !mSimpleFormat) {
            String url = getRelativeResourceUrl(file);
            if (url != null) {
                return url;
            }
        }

        if (mUrlMap != null) {
            String path = file.getAbsolutePath();
            // Perform the comparison using URLs such that we properly escape spaces etc.
            String pathUrl = encodeUrl(path);
            for (Map.Entry<String, String> entry : mUrlMap.entrySet()) {
                String prefix = entry.getKey();
                String prefixUrl = encodeUrl(prefix);
                if (pathUrl.startsWith(prefixUrl)) {
                    String relative = pathUrl.substring(prefixUrl.length());
                    return entry.getValue() + relative;
                }
            }
        }

        if (file.isAbsolute()) {
            String relativePath = getRelativePath(mOutput.getParentFile(), file);
            if (relativePath != null) {
                relativePath = relativePath.replace(separatorChar, '/');
                return encodeUrl(relativePath);
            }
        }

        try {
            return SdkUtils.fileToUrlString(file);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /** Encodes the given String as a safe URL substring, escaping spaces etc */
    static String encodeUrl(String url) {
        try {
            url = url.replace('\\', '/');
            return URLEncoder.encode(url, "UTF-8").replace("%2F", "/");         //$NON-NLS-1$
        } catch (UnsupportedEncodingException e) {
            // This shouldn't happen for UTF-8
            System.err.println("Invalid string " + e.getLocalizedMessage());
            return url;
        }
    }

    /** Set mapping of path prefixes to corresponding URLs in the HTML report */
    public void setUrlMap(@Nullable Map<String, String> urlMap) {
        mUrlMap = urlMap;
    }

    /** Gets a pointer to the local resource directory, if any */
    File getResourceDir() {
        if (mResources == null && mBundleResources) {
            mResources = computeResourceDir();
            if (mResources == null) {
                mBundleResources = false;
            }
        }

        return mResources;
    }

    /** Finds/creates the local resource directory, if possible */
    File computeResourceDir() {
        String fileName = mOutput.getName();
        int dot = fileName.indexOf('.');
        if (dot != -1) {
            fileName = fileName.substring(0, dot);
        }

        File resources = new File(mOutput.getParentFile(), fileName + "_files"); //$NON-NLS-1$
        if (!resources.exists() && !resources.mkdir()) {
            resources = null;
        }

        return resources;
    }

    /** Returns a URL to a local copy of the given file, or null */
    protected String getRelativeResourceUrl(File file) {
        String resource = mResourceUrl.get(file);
        if (resource != null) {
            return resource;
        }

        String name = file.getName();
        if (!endsWith(name, DOT_PNG) || endsWith(name, DOT_9PNG)) {
            return null;
        }

        // Attempt to make local copy
        File resourceDir = getResourceDir();
        if (resourceDir != null) {
            String base = file.getName();

            File path = mNameToFile.get(base);
            if (path != null && !path.equals(file)) {
                // That filename already exists and is associated with a different path:
                // make a new unique version
                for (int i = 0; i < 100; i++) {
                    base = '_' + base;
                    path = mNameToFile.get(base);
                    if (path == null || path.equals(file)) {
                        break;
                    }
                }
            }

            File target = new File(resourceDir, base);
            try {
                Files.copy(file, target);
            } catch (IOException e) {
                return null;
            }
            return resourceDir.getName() + '/' + encodeUrl(base);
        }
        return null;
    }

    /** Returns a URL to a local copy of the given resource, or null. There is
     * no filename conflict resolution. */
    protected String addLocalResources(URL url) {
        // Attempt to make local copy
        File resourceDir = computeResourceDir();
        if (resourceDir != null) {
            String base = url.getFile();
            base = base.substring(base.lastIndexOf('/') + 1);
            mNameToFile.put(base, new File(url.toExternalForm()));

            File target = new File(resourceDir, base);
            try {
                FileOutputStream output = new FileOutputStream(target);
                InputStream input = url.openStream();
                ByteStreams.copy(input, output);
                Closeables.closeQuietly(output);
                Closeables.closeQuietly(input);
            } catch (IOException e) {
                return null;
            }
            return resourceDir.getName() + '/' + encodeUrl(base);
        }
        return null;
    }

    // Based on similar code in com.intellij.openapi.util.io.FileUtilRt
    @Nullable
    static String getRelativePath(File base, File file) {
        if (base == null || file == null) {
            return null;
        }
        if (!base.isDirectory()) {
            base = base.getParentFile();
            if (base == null) {
                return null;
            }
        }
        if (base.equals(file)) {
            return ".";
        }

        final String filePath = file.getAbsolutePath();
        String basePath = base.getAbsolutePath();

        // TODO: Make this return null if we go all the way to the root!

        basePath = !basePath.isEmpty() && basePath.charAt(basePath.length() - 1) == separatorChar
                ? basePath : basePath + separatorChar;

        // Whether filesystem is case sensitive. Technically on OSX you could create a
        // sensitive one, but it's not the default.
        boolean caseSensitive = CURRENT_PLATFORM == PLATFORM_LINUX;
        Locale l = Locale.getDefault();
        String basePathToCompare = caseSensitive ? basePath : basePath.toLowerCase(l);
        String filePathToCompare = caseSensitive ? filePath : filePath.toLowerCase(l);
        if (basePathToCompare.equals(!filePathToCompare.isEmpty()
                && filePathToCompare.charAt(filePathToCompare.length() - 1) == separatorChar
                ? filePathToCompare : filePathToCompare + separatorChar)) {
            return ".";
        }
        int len = 0;
        int lastSeparatorIndex = 0;
        // bug in inspection; see http://youtrack.jetbrains.com/issue/IDEA-118971
        //noinspection ConstantConditions
        while (len < filePath.length() && len < basePath.length()
                && filePathToCompare.charAt(len) == basePathToCompare.charAt(len)) {
            if (basePath.charAt(len) == separatorChar) {
                lastSeparatorIndex = len;
            }
            len++;
        }
        if (len == 0) {
            return null;
        }

        StringBuilder relativePath = new StringBuilder();
        for (int i = len; i < basePath.length(); i++) {
            if (basePath.charAt(i) == separatorChar) {
                relativePath.append("..");
                relativePath.append(separatorChar);
            }
        }
        relativePath.append(filePath.substring(lastSeparatorIndex + 1));
        return relativePath.toString();
    }
}
