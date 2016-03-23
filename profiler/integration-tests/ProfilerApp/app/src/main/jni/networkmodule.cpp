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

#include <fstream>
#include <jni.h>
#include <regex>
#include <string>

using namespace std;

extern "C" {

const char* CONNECTION_FILES[] = {
    "/proc/net/tcp",
    "/proc/net/tcp6",
    "/proc/net/raw",
    "/proc/net/raw6",
    "/proc/net/udp",
    "/proc/net/udp6"
};
const int CONNECTION_FILE_COUNT = sizeof(CONNECTION_FILES) / sizeof(const char*);;
const int UID_TOKEN_INDEX = 7;
const basic_regex<char> REGEX_CONNECTION_LISTENING_ALL_INTERFACES =
        regex("^[ ]*[0-9]+:[ ]+0+:[0-9A-Fa-f]{4}[ ]+0+:[0-9A-Fa-f]{4}[ ]+0A.+$");

/**
 * Returns the number of connections open that belongs to a specific app.
 *
 * @paramter uidString The app uid string.
 */
jint Java_com_android_profilerapp_network_NetworkFragment_getConnectionCount(JNIEnv* env, jobject thisObject, jstring uidString) {
    jint connectionCount = 0;
    // Converts the jstring to be compatible with the string comparison.
    const char* uidChars = env->GetStringUTFChars(uidString, JNI_FALSE);

    for (unsigned int i = 0; i < CONNECTION_FILE_COUNT; ++i) {
        const char* fileName = CONNECTION_FILES[i];
        ifstream inStream(fileName);
        if (inStream.is_open()) {

            string line;
            while(getline(inStream, line)) {
                // Filters out the connection listening to all local interfaces, input line should look like
                // " 0: 00000000000000000000000000000000:13B4 00000000000000000000000000000000:0000 0A ...".
                if (regex_match(line, REGEX_CONNECTION_LISTENING_ALL_INTERFACES)) {
                    continue;
                }

                int prev = 0;
                int pos;
                int tokenIndex = -1;
                while ((pos = line.find_first_of(" \t\r\n\f", prev)) != std::string::npos) {
                    if (pos != prev && ++tokenIndex == UID_TOKEN_INDEX) {
                        string token = line.substr(prev, pos - prev);
                        if (!token.compare(uidChars)) {
                            ++connectionCount;
                        }
                        break;
                    }
                    prev = pos + 1;
                }
            }
            inStream.close();
        }
    }

    return connectionCount;
}

}
