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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Detect calls to get device Identifiers.
 */
public class HardwareIdDetector extends Detector implements Detector.JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            HardwareIdDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    /** Hardware Id Usages  */
    public static final Issue ISSUE = Issue.create(
            "HardwareIds", //$NON-NLS-1$
            "Hardware Id Usage",

            "Using device identifiers is not recommended " +
            "other than for high value fraud prevention and advanced telephony use-cases. " +
            "For advertising use-cases, use `AdvertisingIdClient$Info#getId` and for " +
            "analytics, use `InstanceId#getId`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "https://developer.android.com/training/articles/user-data-ids.html");

    private static final String BLUETOOTH_ADAPTER_GET_ADDRESS = "getAddress"; //$NON-NLS-1$
    private static final String WIFI_INFO_GET_MAC_ADDRESS = "getMacAddress"; //$NON-NLS-1$
    private static final String TELEPHONY_MANAGER_GET_DEVICE_ID = "getDeviceId"; //$NON-NLS-1$
    private static final String TELEPHONY_MANAGER_GET_LINE_1_NUMBER =
            "getLine1Number"; //$NON-NLS-1$
    private static final String TELEPHONY_MANAGER_GET_SIM_SERIAL_NUMBER =
            "getSimSerialNumber"; //$NON-NLS-1$
    private static final String TELEPHONY_MANAGER_GET_SUBSCRIBER_ID =
            "getSubscriberId"; //$NON-NLS-1$
    private static final String SETTINGS_SECURE_GET_STRING = "getString"; //$NON-NLS-1$
    private static final String PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION
            = "com.google.android.gms.common.GooglePlayServicesNotAvailableException"; //$NON-NLS-1$

    /**
     * Constructs a new {@link HardwareIdDetector}
     */
    public HardwareIdDetector() {
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                BLUETOOTH_ADAPTER_GET_ADDRESS,
                WIFI_INFO_GET_MAC_ADDRESS,
                TELEPHONY_MANAGER_GET_DEVICE_ID,
                TELEPHONY_MANAGER_GET_LINE_1_NUMBER,
                TELEPHONY_MANAGER_GET_SIM_SERIAL_NUMBER,
                TELEPHONY_MANAGER_GET_SUBSCRIBER_ID,
                SETTINGS_SECURE_GET_STRING
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        String className = null;
        switch (method.getName()) {
            case BLUETOOTH_ADAPTER_GET_ADDRESS:
                className = "android.bluetooth.BluetoothAdapter"; //$NON-NLS-1$
                break;
            case WIFI_INFO_GET_MAC_ADDRESS:
                className = "android.net.wifi.WifiInfo"; //$NON-NLS-1$
                break;
            case TELEPHONY_MANAGER_GET_DEVICE_ID:
            case TELEPHONY_MANAGER_GET_LINE_1_NUMBER:
            case TELEPHONY_MANAGER_GET_SIM_SERIAL_NUMBER:
            case TELEPHONY_MANAGER_GET_SUBSCRIBER_ID:
                className = "android.telephony.TelephonyManager"; //$NON-NLS-1$
                break;
            case SETTINGS_SECURE_GET_STRING:
                className = "android.provider.Settings.Secure"; //$NON-NLS-1$
                break;
            default:
                assert false;
        }

        if (!evaluator.isMemberInClass(method, className)) {
            return;
        }

        if (method.getName().equals(SETTINGS_SECURE_GET_STRING)) {
            if (evaluator.getParameterCount(method) != 2
                    || node.getArgumentList().getExpressions().length != 2) {
                // we are explicitly looking for Secure.getString(x, ANDROID_ID) here
                return;
            }
            String value = ConstantEvaluator.evaluateString(
                    context, node.getArgumentList().getExpressions()[1], false);
            // Check if the value matches Settings.Secure.ANDROID_ID
            if (!"android_id".equals(value)) { //$NON-NLS-1$
                return;
            }
            // The 2nd parameter resolved to the constant value Settings.Secure.ANDROID_ID
            // which is not recommended so continue and show an error.
        }

        PsiCatchSection surroundingCatchSection =
                PsiTreeUtil.getParentOfType(node, PsiCatchSection.class, true);
        // If any of the calls to get device identifiers are explicitly in a catch block for
        // GooglePlayServicesNotAvailableException, then don't report a warning.
        // This is to handle the case where the alternate play services api is unavailable on
        // the device.
        if (inCatchPlayServicesNotAvailableException(surroundingCatchSection)) {
            return;
        }

        String message = String.format(
                "Using `%1$s` to get device identifiers is not recommended.", method.getName());
        context.report(ISSUE, node, context.getLocation(node), message);
    }

    private static boolean inCatchPlayServicesNotAvailableException(
            PsiCatchSection surroundingCatchSection) {
        if (surroundingCatchSection != null && surroundingCatchSection.getCatchType() != null) {
            PsiType catchType = surroundingCatchSection.getCatchType();
            // Handle multi-catch statements such as (IOException | AnotherException e)
            if (catchType instanceof PsiDisjunctionType) {
                PsiDisjunctionType disjunctionType = (PsiDisjunctionType) catchType;
                if (disjunctionType.getDisjunctions()
                        .stream()
                        .anyMatch(t -> t.equalsToText(PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION))) {
                    return true;
                }
            } else if (catchType.equalsToText(PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION)) {
                return true;
            }
        }
        return false;
    }
}
