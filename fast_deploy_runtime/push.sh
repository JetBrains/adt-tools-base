#!/bin/sh
cd /Users/tnorbye/dev/studio/internal-1.4-dev/tools/base/fast_deploy_runtime
export JAVA_HOME=$JAVA7_HOME
export PATH=$JAVA_HOME/bin:$PATH
#adb shell rm -rf /storage/sdcard/studio-fd/com.android.tools.fd.runtime.testapp
adb shell rm -rf /data/data/com.android.tools.fd.runtime.testapp/files/studio-fd
./gradlew clean assembleDebug installDebug && adb shell am start  -n "com.android.tools.fd.runtime.testapp/com.android.tools.fd.runtime.testapp.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
