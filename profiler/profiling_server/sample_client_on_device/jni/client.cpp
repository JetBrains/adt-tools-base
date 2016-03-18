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
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <cinttypes>

#define HOSTNAME "localhost"

// The port number should be identical to the server's.
#define PORT 7890

// This data structure should be identical to the server's.
struct SystemData {
 public:
  std::int64_t user, nice, system, idle, iowait, irq, softirq, steal, guest,
      guest_nice;
};

void error(const char *msg) {
  perror(msg);
  exit(EXIT_FAILURE);
}

int main(int argc, char *argv[]) {
  int socket_fd = socket(AF_INET, SOCK_STREAM, 0);
  int n = 0;
  sockaddr_in serv_addr = {};
  hostent *server = nullptr;

  char buffer[256];
  if (socket_fd < 0) error("ERROR opening socket");
  server = gethostbyname(HOSTNAME);
  if (server == nullptr) error("ERROR, no such host\n");

  serv_addr.sin_family = AF_INET;
  bcopy((char *)server->h_addr, (char *)&serv_addr.sin_addr.s_addr,
        server->h_length);
  serv_addr.sin_port = htons(PORT);
  if (connect(socket_fd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0)
    error("ERROR connecting");

  int something = 0;
  SystemData data;
  for (int i = 0; i < 10; i++) {
    n = write(socket_fd, &something, sizeof(int));
    if (n < 0) error("ERROR writing to socket");

    bzero(&data, sizeof(SystemData));
    n = read(socket_fd, &data, sizeof(SystemData));
    if (n < 0) error("ERROR reading from socket");

    printf("user: %lld system: %lld idle: %lld\n", data.user, data.system,
           data.idle);
    sleep(1);
  }
  close(socket_fd);
  return 0;
}
