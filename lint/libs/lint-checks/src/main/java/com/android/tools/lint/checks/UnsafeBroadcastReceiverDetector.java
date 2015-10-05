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

package com.android.tools.lint.checks;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.Opcodes;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

public class UnsafeBroadcastReceiverDetector extends Detector
        implements ClassScanner, XmlScanner {

    /* Description of check implementations:
     *
     * UnsafeProtectedBroadcastReceiver check
     *
     * If a receiver is declared in the application manifest that has an intent-filter
     * with an action string that matches a protected-broadcast action string,
     * then if that receiver has an onReceive method, ensure that the method calls
     * getAction at least once.
     *
     * With this check alone, false positives will occur if the onReceive method
     * passes the received intent to another method that calls getAction.
     * We look for any calls to aload_2 within the method bytecode, which could
     * indicate loading the inputted intent onto the stack to use in a call
     * to another method. In those cases, still report the issue, but
     * report in the description that the finding may be a false positive.
     * An alternative implementation option would be to omit reporting the issue
     * at all when a call to aload_2 exists.
     *
     * UnprotectedSMSBroadcastReceiver check
     *
     * If a receiver is declared in AndroidManifest that has an intent-filter
     * with action string SMS_DELIVER or SMS_RECEIVED, ensure that the
     * receiver requires callers to have the BROADCAST_SMS permission.
     *
     * It is possible that the receiver may check the sender's permission by
     * calling checkCallingPermission, which could cause a false positive.
     * However, application developers should still be encouraged to declare
     * the permission requirement in the manifest where it can be easily
     * audited.
     *
     * Future work: Add checks for other action strings that should require
     * particular permissions be checked, such as
     * android.provider.Telephony.WAP_PUSH_DELIVER
     *
     * Note that neither of these checks address receivers dynamically created at runtime,
     * only ones that are declared in the application manifest.
     */

    public static final Issue ACTION_STRING = Issue.create(
            "UnsafeProtectedBroadcastReceiver",
            "Unsafe Protected BroadcastReceiver",
            "BroadcastReceivers that declare an intent-filter for a protected-broadcast action " +
            "string must check that the received intent's action string matches the expected " +
            "value, otherwise it is possible for malicious actors to spoof intents.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeBroadcastReceiverDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.ALL_CLASS_FILES)));

    public static final Issue BROADCAST_SMS = Issue.create(
            "UnprotectedSMSBroadcastReceiver",
            "Unprotected SMS BroadcastReceiver",
            "BroadcastReceivers that declare an intent-filter for SMS_DELIVER or " +
            "SMS_RECEIVED must ensure that the caller has the BROADCAST_SMS permission, " +
            "otherwise it is possible for malicious actors to spoof intents.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeBroadcastReceiverDetector.class,
                    Scope.MANIFEST_SCOPE));

    /* List of protected broadcast strings. This list must be sorted alphabetically.
     * Protected broadcast strings are defined by <protected-broadcast> entries in the
     * manifest of system-level components or applications.
     * The below list is copied from frameworks/base/core/res/AndroidManifest.xml
     * and packages/services/Telephony/AndroidManifest.xml .
     * It should be periodically updated. This list will likely not be complete, since
     * protected-broadcast entries can be defined elsewhere, but should address
     * most situations.
     */
    private static final String[] PROTECTED_BROADCASTS = new String[] {
            "android.app.action.ENTER_CAR_MODE",
            "android.app.action.ENTER_DESK_MODE",
            "android.app.action.EXIT_CAR_MODE",
            "android.app.action.EXIT_DESK_MODE",
            "android.app.action.NEXT_ALARM_CLOCK_CHANGED",
            "android.appwidget.action.APPWIDGET_DELETED",
            "android.appwidget.action.APPWIDGET_DISABLED",
            "android.appwidget.action.APPWIDGET_ENABLED",
            "android.appwidget.action.APPWIDGET_HOST_RESTORED",
            "android.appwidget.action.APPWIDGET_RESTORED",
            "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS",
            "android.backup.intent.CLEAR",
            "android.backup.intent.INIT",
            "android.backup.intent.RUN",
            "android.bluetooth.a2dp-sink.profile.action.AUDIO_CONFIG_CHANGED",
            "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED",
            "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.adapter.action.DISCOVERY_FINISHED",
            "android.bluetooth.adapter.action.DISCOVERY_STARTED",
            "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED",
            "android.bluetooth.adapter.action.SCAN_MODE_CHANGED",
            "android.bluetooth.adapter.action.STATE_CHANGED",
            "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.device.action.ACL_CONNECTED",
            "android.bluetooth.device.action.ACL_DISCONNECTED",
            "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED",
            "android.bluetooth.device.action.ALIAS_CHANGED",
            "android.bluetooth.device.action.BOND_STATE_CHANGED",
            "android.bluetooth.device.action.CLASS_CHANGED",
            "android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL",
            "android.bluetooth.device.action.CONNECTION_ACCESS_REPLY",
            "android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST",
            "android.bluetooth.device.action.DISAPPEARED",
            "android.bluetooth.device.action.FOUND",
            "android.bluetooth.device.action.MAS_INSTANCE",
            "android.bluetooth.device.action.NAME_CHANGED",
            "android.bluetooth.device.action.NAME_FAILED",
            "android.bluetooth.device.action.PAIRING_CANCEL",
            "android.bluetooth.device.action.PAIRING_REQUEST",
            "android.bluetooth.device.action.UUID",
            "android.bluetooth.devicepicker.action.DEVICE_SELECTED",
            "android.bluetooth.devicepicker.action.LAUNCH",
            "android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT",
            "android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED",
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED",
            "android.bluetooth.headsetclient.profile.action.AG_EVENT",
            "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED",
            "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.headsetclient.profile.action.LAST_VTAG",
            "android.bluetooth.headsetclient.profile.action.RESULT",
            "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.input.profile.action.PROTOCOL_MODE_CHANGED",
            "android.bluetooth.input.profile.action.VIRTUAL_UNPLUG_STATUS",
            "android.bluetooth.pan.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.pbap.intent.action.PBAP_STATE_CHANGED",
            "android.btopp.intent.action.CONFIRM",
            "android.btopp.intent.action.HIDE",
            "android.btopp.intent.action.HIDE_COMPLETE",
            "android.btopp.intent.action.INCOMING_FILE_NOTIFICATION",
            "android.btopp.intent.action.LIST",
            "android.btopp.intent.action.OPEN",
            "android.btopp.intent.action.OPEN_INBOUND",
            "android.btopp.intent.action.OPEN_OUTBOUND",
            "android.btopp.intent.action.RETRY",
            "android.btopp.intent.action.USER_CONFIRMATION_TIMEOUT",
            "android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED",
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
            "android.hardware.usb.action.USB_DEVICE_ATTACHED",
            "android.hardware.usb.action.USB_DEVICE_DETACHED",
            "android.hardware.usb.action.USB_STATE",
            "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED",
            "android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED",
            "android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED",
            "android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED",
            "android.intent.action.ACTION_IDLE_MAINTENANCE_END",
            "android.intent.action.ACTION_IDLE_MAINTENANCE_START",
            "android.intent.action.ACTION_MDN_STATE_CHANGED",
            "android.intent.action.ACTION_POWER_CONNECTED",
            "android.intent.action.ACTION_POWER_DISCONNECTED",
            "android.intent.action.ACTION_SET_RADIO_CAPABILITY_DONE",
            "android.intent.action.ACTION_SET_RADIO_CAPABILITY_FAILED",
            "android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS",
            "android.intent.action.ACTION_SHUTDOWN",
            "android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE",
            "android.intent.action.ACTION_SUBINFO_RECORD_UPDATED",
            "android.intent.action.ADVANCED_SETTINGS",
            "android.intent.action.AIRPLANE_MODE",
            "android.intent.action.ANY_DATA_STATE",
            "android.intent.action.APPLICATION_RESTRICTIONS_CHANGED",
            "android.intent.action.BATTERY_CHANGED",
            "android.intent.action.BATTERY_LOW",
            "android.intent.action.BATTERY_OKAY",
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.BUGREPORT_FINISHED",
            "android.intent.action.CLEAR_DNS_CACHE",
            "android.intent.action.CONFIGURATION_CHANGED",
            "android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN",
            "android.intent.action.DATA_CONNECTION_FAILED",
            "android.intent.action.DATE_CHANGED",
            "android.intent.action.DEVICE_STORAGE_FULL",
            "android.intent.action.DEVICE_STORAGE_LOW",
            "android.intent.action.DEVICE_STORAGE_NOT_FULL",
            "android.intent.action.DEVICE_STORAGE_OK",
            "android.intent.action.DOCK_EVENT",
            "android.intent.action.DREAMING_STARTED",
            "android.intent.action.DREAMING_STOPPED",
            "android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED",
            "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE",
            "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE",
            "android.intent.action.HDMI_PLUGGED",
            "android.intent.action.HEADSET_PLUG",
            "android.intent.action.LOCALE_CHANGED",
            "android.intent.action.MASTER_CLEAR_NOTIFICATION",
            "android.intent.action.MEDIA_BAD_REMOVAL",
            "android.intent.action.MEDIA_CHECKING",
            "android.intent.action.MEDIA_EJECT",
            "android.intent.action.MEDIA_MOUNTED",
            "android.intent.action.MEDIA_NOFS",
            "android.intent.action.MEDIA_REMOVED",
            "android.intent.action.MEDIA_SHARED",
            "android.intent.action.MEDIA_UNMOUNTABLE",
            "android.intent.action.MEDIA_UNMOUNTED",
            "android.intent.action.MEDIA_UNSHARED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.NETWORK_SET_TIME",
            "android.intent.action.NETWORK_SET_TIMEZONE",
            "android.intent.action.NEW_OUTGOING_CALL",
            "android.intent.action.PACKAGE_ADDED",
            "android.intent.action.PACKAGE_CHANGED",
            "android.intent.action.PACKAGE_DATA_CLEARED",
            "android.intent.action.PACKAGE_FIRST_LAUNCH",
            "android.intent.action.PACKAGE_FULLY_REMOVED",
            "android.intent.action.PACKAGE_INSTALL",
            "android.intent.action.PACKAGE_NEEDS_VERIFICATION",
            "android.intent.action.PACKAGE_REMOVED",
            "android.intent.action.PACKAGE_REPLACED",
            "android.intent.action.PACKAGE_RESTARTED",
            "android.intent.action.PACKAGE_VERIFIED",
            "android.intent.action.PERMISSION_RESPONSE_RECEIVED",
            "android.intent.action.PHONE_STATE",
            "android.intent.action.PROXY_CHANGE",
            "android.intent.action.QUERY_PACKAGE_RESTART",
            "android.intent.action.RADIO_TECHNOLOGY",
            "android.intent.action.REBOOT",
            "android.intent.action.REQUEST_PERMISSION",
            "android.intent.action.SCREEN_OFF",
            "android.intent.action.SCREEN_ON",
            "android.intent.action.SERVICE_STATE",
            "android.intent.action.SIG_STR",
            "android.intent.action.SIM_STATE_CHANGED",
            "android.intent.action.SUB_DEFAULT_CHANGED",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIME_TICK",
            "android.intent.action.UID_REMOVED",
            "android.intent.action.USER_ADDED",
            "android.intent.action.USER_BACKGROUND",
            "android.intent.action.USER_FOREGROUND",
            "android.intent.action.USER_PRESENT",
            "android.intent.action.USER_REMOVED",
            "android.intent.action.USER_STARTED",
            "android.intent.action.USER_STARTING",
            "android.intent.action.USER_STOPPED",
            "android.intent.action.USER_STOPPING",
            "android.intent.action.USER_SWITCHED",
            "android.location.GPS_ENABLED_CHANGE",
            "android.location.GPS_FIX_CHANGE",
            "android.location.MODE_CHANGED",
            "android.location.PROVIDERS_CHANGED",
            "android.media.ACTION_SCO_AUDIO_STATE_UPDATED",
            "android.media.AUDIO_BECOMING_NOISY",
            "android.media.MASTER_MUTE_CHANGED_ACTION",
            "android.media.MASTER_VOLUME_CHANGED_ACTION",
            "android.media.RINGER_MODE_CHANGED",
            "android.media.SCO_AUDIO_STATE_CHANGED",
            "android.media.VIBRATE_SETTING_CHANGED",
            "android.media.VOLUME_CHANGED_ACTION",
            "android.media.action.ANALOG_AUDIO_DOCK_PLUG",
            "android.media.action.DIGITAL_AUDIO_DOCK_PLUG",
            "android.media.action.HDMI_AUDIO_PLUG",
            "android.media.action.USB_AUDIO_ACCESSORY_PLUG",
            "android.media.action.USB_AUDIO_DEVICE_PLUG",
            "android.net.ConnectivityService.action.PKT_CNT_SAMPLE_INTERVAL_ELAPSED",
            "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED",
            "android.net.conn.CAPTIVE_PORTAL_TEST_COMPLETED",
            "android.net.conn.CONNECTIVITY_CHANGE",
            "android.net.conn.CONNECTIVITY_CHANGE_IMMEDIATE",
            "android.net.conn.DATA_ACTIVITY_CHANGE",
            "android.net.conn.INET_CONDITION_ACTION",
            "android.net.conn.NETWORK_CONDITIONS_MEASURED",
            "android.net.conn.TETHER_STATE_CHANGED",
            "android.net.nsd.STATE_CHANGED",
            "android.net.proxy.PAC_REFRESH",
            "android.net.scoring.SCORER_CHANGED",
            "android.net.scoring.SCORE_NETWORKS",
            "android.net.wifi.CONFIGURED_NETWORKS_CHANGE",
            "android.net.wifi.LINK_CONFIGURATION_CHANGED",
            "android.net.wifi.RSSI_CHANGED",
            "android.net.wifi.SCAN_RESULTS",
            "android.net.wifi.STATE_CHANGE",
            "android.net.wifi.WIFI_AP_STATE_CHANGED",
            "android.net.wifi.WIFI_SCAN_AVAILABLE",
            "android.net.wifi.WIFI_STATE_CHANGED",
            "android.net.wifi.p2p.CONNECTION_STATE_CHANGE",
            "android.net.wifi.p2p.DISCOVERY_STATE_CHANGE",
            "android.net.wifi.p2p.PEERS_CHANGED",
            "android.net.wifi.p2p.PERSISTENT_GROUPS_CHANGED",
            "android.net.wifi.p2p.STATE_CHANGED",
            "android.net.wifi.p2p.THIS_DEVICE_CHANGED",
            "android.net.wifi.supplicant.CONNECTION_CHANGE",
            "android.net.wifi.supplicant.STATE_CHANGE",
            "android.nfc.action.LLCP_LINK_STATE_CHANGED",
            "android.nfc.action.TRANSACTION_DETECTED",
            "android.nfc.handover.intent.action.HANDOVER_STARTED",
            "android.nfc.handover.intent.action.TRANSFER_DONE",
            "android.nfc.handover.intent.action.TRANSFER_DONE",
            "android.nfc.handover.intent.action.TRANSFER_PROGRESS",
            "android.os.UpdateLock.UPDATE_LOCK_CHANGED",
            "android.os.action.POWER_SAVE_MODE_CHANGED",
            "android.os.action.POWER_SAVE_MODE_CHANGING",
            "android.provider.Telephony.SIM_FULL",
            "android.provider.Telephony.SPN_STRINGS_UPDATED",
            "com.android.bluetooth.pbap.authcancelled",
            "com.android.bluetooth.pbap.authchall",
            "com.android.bluetooth.pbap.authresponse",
            "com.android.bluetooth.pbap.userconfirmtimeout",
            "com.android.internal.telephony.data-restart-trysetup",
            "com.android.internal.telephony.data-stall",
            "com.android.nfc_extras.action.AID_SELECTED",
            "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED",
            "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED",
            "com.android.server.WifiManager.action.DELAYED_DRIVER_STOP",
            "com.android.server.WifiManager.action.START_SCAN",
            "com.android.server.connectivityservice.CONNECTED_TO_PROVISIONING_NETWORK_ACTION"
    };

    private static final Set<String> PROTECTED_BROADCAST_SET =
            new HashSet<String>(Arrays.asList(PROTECTED_BROADCASTS));

    public UnsafeBroadcastReceiverDetector() {
    }

    private Set<String> mReceiversWithProtectedBroadcastIntentFilter = new HashSet<String>();

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }

    @SuppressWarnings("rawtypes")
    public void checkClass(@NonNull final ClassContext context,
            @NonNull ClassNode classNode) {
        // If class isn't in mReceiversWithIntentFilter (set below by
        // XmlScanner), skip it.
        if (!mReceiversWithProtectedBroadcastIntentFilter.contains(classNode.name)) {
            return;
        }

        // Search for "void onReceive(android.content.Context, android.content.Intent)" method
        List methodList = classNode.methods;

        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            if (!"onReceive".equals(method.name) ||
                    !"(Landroid/content/Context;Landroid/content/Intent;)V".equals(method.desc)) {
                continue;
            }
            // Search for call to getAction but also search for references to aload_2,
            // which indicates that the method is making use of the received intent in
            // some way.
            //
            // If the onReceive method doesn't call getAction but does make use of
            // the received intent, it is possible that it is passing it to another
            // method that might be performing the getAction check, so we warn that the
            // finding may be a false positive. (An alternative option would be to not
            // report a finding at all in this case.)
            boolean getActionEncountered = false;
            boolean intentParameterEncountered = false;
            InsnList nodes = method.instructions;
            for (int i = 0, n = nodes.size(); i < n; i++) {
                AbstractInsnNode instruction = nodes.get(i);
                int type = instruction.getType();
                if (type == AbstractInsnNode.VAR_INSN) {
                    VarInsnNode node = (VarInsnNode) instruction;
                    if (node.getOpcode() == Opcodes.ALOAD) {
                        if (node.var == 2) {
                            intentParameterEncountered = true;
                        }
                    }
                }
                else if (type == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    if ("android/content/Intent".equals(node.owner) &&
                            "getAction".equals(node.name)) {
                        getActionEncountered = true;
                        break;
                    }
                }
            }
            if (!getActionEncountered) {
                Location location = context.getLocation(method, classNode);
                String report;
                if (!intentParameterEncountered) {
                    report = "This broadcast receiver declares an intent-filter for a protected " +
                             "broadcast action string, which can only be sent by the system, " +
                             "not third-party applications. However, the receiver's onReceive " +
                             "method does not appear to call getAction to ensure that the " +
                             "received Intent's action string matches the expected value, " +
                             "potentially making it possible for another actor to send a " +
                             "spoofed intent with no action string or a different action " +
                             "string and cause undesired behavior.";
                } else {
                    // An alternative implementation option is to not report a finding at all in
                    // this case, if we are worried about false positives causing confusion or
                    // resulting in developers ignoring other lint warnings.
                    report = "This broadcast receiver declares an intent-filter for a protected " +
                             "broadcast action string, which can only be sent by the system, " +
                             "not third-party applications. However, the receiver's onReceive " +
                             "method does not appear to call getAction to ensure that the " +
                             "received Intent's action string matches the expected value, " +
                             "potentially making it possible for another actor to send a " +
                             "spoofed intent with no action string or a different action " +
                             "string and cause undesired behavior. In this case, it is " +
                             "possible that the onReceive method passed the received Intent " +
                             "to another method that checked the action string. If so, this " +
                             "finding can safely be ignored.";
                }
                context.report(ACTION_STRING, method, null, location, report);
            }
        }
    }

    // Implements Detector.XmlScanner
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context,
            @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_RECEIVER.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            // If no permission attribute, then if any exists at the application
            // element, it applies
            if (permission == null || permission.isEmpty()) {
                Element parent = (Element) element.getParentNode();
                permission = parent.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            }
            List<Element> children = LintUtils.getChildren(element);
            for (Element child : children) {
                String tagName = child.getTagName();
                if (TAG_INTENT_FILTER.equals(tagName)) {
                    if (name.startsWith(".")) {
                        name = context.getProject().getPackage() + name;
                    }
                    name = name.replace('.', '/');
                    List<Element> children2 = LintUtils.getChildren(child);
                    for (Element child2 : children2) {
                        if ("action".equals(child2.getTagName())) {
                            String actionName = child2.getAttributeNS(
                                    ANDROID_URI, ATTR_NAME);
                            if (("android.provider.Telephony.SMS_DELIVER".equals(actionName) ||
                                    "android.provider.Telephony.SMS_RECEIVED".
                                        equals(actionName)) &&
                                    !"android.permission.BROADCAST_SMS".equals(permission)) {
                                context.report(
                                        BROADCAST_SMS,
                                        element,
                                        context.getLocation(element),
                                        "BroadcastReceivers that declare an intent-filter for " +
                                        "SMS_DELIVER or SMS_RECEIVED must ensure that the " +
                                        "caller has the BROADCAST_SMS permission, otherwise it " +
                                        "is possible for malicious actors to spoof intents.");
                            }
                            else if (PROTECTED_BROADCAST_SET.contains(actionName)) {
                                mReceiversWithProtectedBroadcastIntentFilter.add(name);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
}
