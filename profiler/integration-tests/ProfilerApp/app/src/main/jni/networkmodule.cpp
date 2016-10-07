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

#include "profiler_util.h"

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
const int CONNECTION_UID_TOKEN_INDEX = 7;
const basic_regex<char> REGEX_CONNECTION_LISTENING_ALL_INTERFACES =
        regex("^[ ]*[0-9]+:[ ]+0+:[0-9A-Fa-f]{4}[ ]+0+:[0-9A-Fa-f]{4}[ ]+0A.+$");

const char* TRAFFIC_BYTES_FILE = "/proc/net/xt_qtaguid/stats";
const int BYTES_UID_TOKEN_INDEX = 3;
const int BYTES_TX_TOKEN_INDEX = 7;
const int BYTES_RX_TOKEN_INDEX = 5;

/**
 * Returns whether a token at the specific index is present, also returns the token start and end position.
 * Tokens are delimited by whitespaces.
 *
 * @parameter line          Input line that has tokens joined by whitespaces.
 * @parameter tokenIndex    Needed token's index.
 * @parameter tokenStart    Initial parse start position input and token start position output.
 */
static bool getPosition(const string& line, int tokenIndex, size_t* tokenStart) {
    int index = -1;
    size_t tokenEnd;
    while((tokenEnd = line.find_first_of(" \t\r\n\f", *tokenStart)) != string::npos) {
        if (tokenEnd != *tokenStart && ++index == tokenIndex) {
            return true;
        }
        *tokenStart = tokenEnd + 1;
    }
    return false;
}

/**
 * Returns true if the uid is present in line at a specific location, false otherwise.
 *
 * @parameter line              Input line that has tokens joined by whitespaces.
 * @parameter uidTokenIndex     Uid token index.
 * @parameter uid               Uid string reference.
 */
static bool matchUid(const string& line, const char* uid, int uidTokenIndex) {
    size_t uidStartPosition = 0;
    bool hasPosition = getPosition(line, uidTokenIndex, &uidStartPosition);
    return hasPosition && !line.compare(uidStartPosition, strlen(uid), uid);
}

/**
 * Returns the number of connections open that belongs to a specific app.
 *
 * @parameter uidString The app uid string.
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
                bool isListeningLocalInterfaceConnection = regex_match(line, REGEX_CONNECTION_LISTENING_ALL_INTERFACES);
                if (!isListeningLocalInterfaceConnection && matchUid(line, uidChars, CONNECTION_UID_TOKEN_INDEX)) {
                    ++connectionCount;
                }
            }
            inStream.close();
        }
    }

    return connectionCount;
}

/**
 * Returns the traffic bytes array belonging to an app. For example, app sent 1KB and received 10KB totally so far,
 * the returned array is {@code {1000LL, 10000LL}}.
 */
jlongArray Java_com_android_profilerapp_network_NetworkFragment_getTrafficBytes(JNIEnv* env, jobject thisObject, jstring uidString) {
    long long bytes[2] = {0};

    ifstream inStream(TRAFFIC_BYTES_FILE);
    if (inStream.is_open()) {
        // Converts the jstring to be compatible with the string comparison.
        const char* uidChars = env->GetStringUTFChars(uidString, JNI_FALSE);

        string line;

        size_t receiveTokenStart;
        size_t sendTokenStart;

        while(getline(inStream, line)) {
            if (matchUid(line, uidChars, BYTES_UID_TOKEN_INDEX)) {
                receiveTokenStart = 0;
                if (!getPosition(line, BYTES_RX_TOKEN_INDEX, &receiveTokenStart)) {
                    continue;
                }
                sendTokenStart = receiveTokenStart;
                if (!getPosition(line, BYTES_TX_TOKEN_INDEX - BYTES_RX_TOKEN_INDEX, &sendTokenStart)) {
                    continue;
                }

                bytes[0] += strtoll(&line[sendTokenStart], nullptr, 10);
                bytes[1] += strtoll(&line[receiveTokenStart], nullptr, 10);
            }
        }
        inStream.close();
    }

    jlongArray jniBytes = env->NewLongArray(2);
    env->SetLongArrayRegion(jniBytes, 0, 2, bytes);
    return jniBytes;
}

}
