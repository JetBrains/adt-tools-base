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
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>
#include <cinttypes>
#include <cstdio>

#include "system_data.h"
#include "system_data_collector.h"

#define PORT 7890

using android_studio_profiler::SystemDataCollector;
using android_studio_profiler::SystemData;

static void error(const char *msg) {
  perror(msg);
  exit(1);
}

int main(int argc, char *argv[]) {
  int listen_socket, data_socket;
  char buffer[256];
  struct sockaddr_in serv_addr;
  int n;
  SystemData data;
  SystemDataCollector collector;

  if (!collector.prepare()) error("Cannot open /proc/stat");

  listen_socket = socket(AF_INET, SOCK_STREAM, 0);
  if (listen_socket < 0) error("ERROR opening socket");
  bzero((char *)&serv_addr, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  serv_addr.sin_addr.s_addr = INADDR_ANY;
  serv_addr.sin_port = htons(PORT);
  if (bind(listen_socket, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0)
    error("ERROR on binding");

  listen(listen_socket, 5);
  data_socket = accept(listen_socket, nullptr, nullptr);
  if (data_socket < 0) error("ERROR on accept");

  while (true) {
    n = read(data_socket, buffer, 255);
    if (n < 0) error("ERROR reading from socket");

    if (collector.read(&data)) {
      printf("Read: %lld %lld %lld\n", data.user, data.system, data.idle);
    } else {
      printf("error\n");
      continue;
    }

    n = write(data_socket, &data, sizeof(SystemData));
    if (n < 0) error("ERROR writing to socket");
  }
  // Note: The following code is actually dead code because the while loop above
  // never exits. It is probably good enough as the server is expected to live
  // very shortly (e.g., a week).
  close(data_socket);
  close(listen_socket);
  collector.close();
  return 0;
}
