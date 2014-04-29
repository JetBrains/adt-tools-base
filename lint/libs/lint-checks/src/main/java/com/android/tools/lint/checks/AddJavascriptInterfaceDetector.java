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


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.List;

/**
 * Ensures that addJavascriptInterface is not called for API levels below 17.
 */
public class AddJavascriptInterfaceDetector extends Detector implements Detector.ClassScanner {
    public static final Issue ISSUE = Issue.create(
            "AddJavascriptInterface", //$NON-NLS-1$
            "addJavascriptInterface Called",
            "Checks that `WebView#addJavascriptInterface` is not called for API levels below 17",
            "For applications built for API levels below 17, `WebView#addJavascriptInterface` "
                    + "presents a security hazard as JavaScript on the target web page has the "
                    + "ability to use reflection to access the injected object's public fields and "
                    + "thus manipulate the host application in unintended ways.",
            Category.SECURITY,
            9,
            Severity.WARNING,
            new Implementation(
                    AddJavascriptInterfaceDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    private static final String WEB_VIEW = "android/webkit/WebView"; //$NON-NLS-1$
    private static final String ADD_JAVASCRIPT_INTERFACE = "addJavascriptInterface"; //$NON-NLS-1$
    private static final String ADD_JAVASCRIPT_INTERFACE_SIG = "(Ljava/lang/Object;Ljava/lang/String;)V"; //$NON-NLS-1$

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements ClassScanner ----
    @Nullable
    @Override
    public List<String> getApplicableCallNames() {
        return Collections.singletonList(ADD_JAVASCRIPT_INTERFACE);
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode,
            @NonNull MethodNode method, @NonNull MethodInsnNode call) {
        // Ignore the issue if we never build for any API less than 17.
        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        // Ignore if the method doesn't fit our description.
        if (!call.desc.equals(ADD_JAVASCRIPT_INTERFACE_SIG)) {
            return;
        }

        String ownerClassName = call.owner;
        ClassNode ownerClass = context.getDriver().findClass(context, ownerClassName, 0);

        if (ownerClassName.equals(WEB_VIEW)
                || ((ownerClass != null)
                && context.getDriver().isSubclassOf(ownerClass, WEB_VIEW))) {
            String message = "WebView.addJavascriptInterface should not be called";
            context.report(ISSUE, context.getLocation(call), message, null);
        }
    }
}
