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

import static com.android.SdkConstants.CONSTRUCTOR_NAME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.List;

/**
 * Checks for errors related to locale handling
 */
public class DateFormatDetector extends Detector implements ClassScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            DateFormatDetector.class,
            Scope.CLASS_FILE_SCOPE);

    /** Constructing SimpleDateFormat without an explicit locale */
    public static final Issue DATE_FORMAT = Issue.create(
            "SimpleDateFormat", //$NON-NLS-1$
            "Implied locale in date format",

            "Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, or " +
            "`getTimeInstance()` to get a ready-made instance of SimpleDateFormat suitable " +
            "for the user's locale. The main reason you'd create an instance this class " +
            "directly is because you need to format/parse a specific machine-readable format, " +
            "in which case you almost certainly want to explicitly ask for US to ensure that " +
            "you get ASCII digits (rather than, say, Arabic digits).\n" +
            "\n" +
            "Therefore, you should either use the form of the SimpleDateFormat constructor " +
            "where you pass in an explicit locale, such as Locale.US, or use one of the " +
            "get instance methods, or suppress this error if really know what you are doing.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo(
            "http://developer.android.com/reference/java/text/SimpleDateFormat.html"); //$NON-NLS-1$

    static final String DATE_FORMAT_OWNER = "java/text/SimpleDateFormat"; //$NON-NLS-1$

    /** Constructs a new {@link DateFormatDetector} */
    public DateFormatDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallOwners() {
        return Collections.singletonList(DATE_FORMAT_OWNER);
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode,
            @NonNull MethodNode method, @NonNull MethodInsnNode call) {
        String owner = call.owner;
        String desc = call.desc;
        String name = call.name;
        if (owner.equals(DATE_FORMAT_OWNER)) {
            if (!name.equals(CONSTRUCTOR_NAME)) {
                return;
            }
            if (desc.equals("(Ljava/lang/String;Ljava/text/DateFormatSymbols;)V")   //$NON-NLS-1$
                    || desc.equals("()V")                                           //$NON-NLS-1$
                    || desc.equals("(Ljava/lang/String;)V")) {                      //$NON-NLS-1$
                Location location = context.getLocation(call);
                String message =
                    "To get local formatting use `getDateInstance()`, `getDateTimeInstance()`, " +
                    "or `getTimeInstance()`, or use `new SimpleDateFormat(String template, " +
                    "Locale locale)` with for example `Locale.US` for ASCII dates.";
                context.report(DATE_FORMAT, method, call, location, message);
            }
        }
    }
}
