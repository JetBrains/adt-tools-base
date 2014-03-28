/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.DOT_PROPERTIES;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Splitter;

import java.io.File;
import java.util.Iterator;

/**
 * Check for errors in .property files
 * <p>
 * TODO: Warn about bad paths like sdk properties with ' in the path, or suffix of " " etc
 */
public class PropertyFileDetector extends Detector {
    /** Property file not escaped */
    public static final Issue ISSUE = Issue.create(
            "PropertyEscape", //$NON-NLS-1$
            "Incorrect property escapes",
            "Looks for property files with incorrect paths",
            "All backslashes and colons in .property files must be escaped with " +
            "a backslash (\\). This means that when writing a Windows path, you " +
            "must escape the file separators, so the path \\My\\Files should be " +
            "written as `key=\\\\My\\\\Files.`",

            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    PropertyFileDetector.class,
                    Scope.PROPERTY_SCOPE));

    /** Constructs a new {@link PropertyFileDetector} */
    public PropertyFileDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return file.getPath().endsWith(DOT_PROPERTIES);
    }

    @Override
    public void run(@NonNull Context context) {
        String contents = context.getContents();
        if (contents == null) {
            return;
        }
        int offset = 0;
        Iterator<String> iterator = Splitter.on('\n').split(contents).iterator();
        String line;
        for (; iterator.hasNext(); offset += line.length() + 1) {
            line = iterator.next();
            if (line.startsWith("#") || line.startsWith(" ")) {
                continue;
            }
            if (line.indexOf('\\') == -1) {
                continue;
            }
            int valueStart = line.indexOf('=') + 1;
            if (valueStart == 0) {
                continue;
            }
            if (line.indexOf('\\') == -1) {
                continue;
            }
            checkLine(context, contents, line, offset, valueStart);
        }
    }

    private static void checkLine(@NonNull Context context, @NonNull String contents,
            @NonNull String line, int offset, int valueStart) {
        boolean escaped = false;
        int hadUnescapedColon = -1;
        boolean hadNonPathEscape = false;
        StringBuilder path = new StringBuilder();
        for (int i = valueStart; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                escaped = !escaped;
                if (escaped) {
                    path.append(c);
                }
            } else {
                if (escaped) {
                    if (c != ':') {
                        hadNonPathEscape = true;
                    }
                } else if (c == ':' && hadUnescapedColon == -1) {
                    hadUnescapedColon = i;
                }
                escaped = false;
                path.append(c);
            }
        }
        String pathString = path.toString();
        String key = line.substring(0, valueStart);
        if ((hadNonPathEscape || hadUnescapedColon != -1) &&
                key.endsWith(".dir=") || new File(pathString).exists()) {
            if (hadNonPathEscape) {
                String escapedPath = pathString.replace("\\", "\\\\");
                String message = "Windows file separators (\\) must be escaped (\\\\); use "
                        + escapedPath;
                int startOffset = offset + valueStart;
                int endOffset = offset + line.length();
                Location location = Location.create(context.file, contents, startOffset,
                        endOffset);
                context.report(ISSUE, location, message, null);
            }
            if (hadUnescapedColon != -1) {
                String message = "Colon (:) must be escaped in .property files";
                int startOffset = offset + hadUnescapedColon;
                Location location = Location.create(context.file, contents, startOffset,
                        startOffset + 1);
                context.report(ISSUE, location, message, null);
            }
        }
    }
}
