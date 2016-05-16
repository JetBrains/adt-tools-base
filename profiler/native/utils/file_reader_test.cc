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
#include "file_reader.h"

#include <gtest/gtest.h>

using profiler::utils::FileReader;

const std::string kTestLine("Twinkle Twinkle Little star !");

TEST(FindToken, TokenIsFirst) {
  size_t token_start = 0;
  bool is_found = FileReader::FindTokenPosition(kTestLine, 0, &token_start);
  EXPECT_TRUE(is_found);
  EXPECT_EQ(0, token_start);
}

TEST(FindToken, TokenIsLast) {
  size_t token_start = 0;
  bool is_found = FileReader::FindTokenPosition(kTestLine, 4, &token_start);
  EXPECT_TRUE(is_found);
  EXPECT_EQ(28, token_start);
}

TEST(FindToken, TokenIsMiddle) {
  size_t token_start = 0;
  bool is_found = FileReader::FindTokenPosition(kTestLine, 2, &token_start);
  EXPECT_TRUE(is_found);
  EXPECT_EQ(16, token_start);
}

TEST(FindToken, TokenValueIsDuplicate) {
  size_t token_start = 0;
  bool is_found = FileReader::FindTokenPosition(kTestLine, 1, &token_start);
  EXPECT_TRUE(is_found);
  EXPECT_EQ(8, token_start);
}

TEST(FindToken, TokenIndexTooLarge) {
  size_t token_start = 7;
  bool is_found = FileReader::FindTokenPosition(kTestLine, 6, &token_start);
  EXPECT_FALSE(is_found);
}

TEST(FindToken, LineEmptyAndStartPositionIsPositive) {
  size_t token_start = 1;
  std::string empty_line = "";
  bool is_found = FileReader::FindTokenPosition(empty_line, 0, &token_start);
  EXPECT_FALSE(is_found);
}

TEST(CompareToken, TokenMatches) {
  std::string token("Little");
  bool matches = FileReader::CompareToken(kTestLine, token, 2);
  EXPECT_TRUE(matches);
}

TEST(CompareToken, TokenIndexTooLarge) {
  std::string token("Little");
  bool matches = FileReader::CompareToken(kTestLine, token, 4);
  EXPECT_FALSE(matches);
}

TEST(CompareToken, TokenNotMatch) {
  std::string token("Large");
  bool matches = FileReader::CompareToken(kTestLine, token, 2);
  EXPECT_FALSE(matches);
}

TEST(Read, FileSizeIsSmallerThanPageSize) {
  std::string content;
  FileReader::Read("file_reader_small.txt", &content);
  EXPECT_EQ(37, content.size());
}

TEST(Read, ReadFileSizeLargerThanBufferSize) {
  std::string content;
  FileReader::Read("file_reader_large.txt", &content);
  EXPECT_EQ(5264, content.size());
}

TEST(Read, ReadFileAbsent) {
  std::string content;
  EXPECT_FALSE(FileReader::Read("file_reader_absent.txt", &content));
}

TEST(ReadToLines, MultipleLineBreakChars) {
  std::vector<std::string> lines;
  FileReader::Read("file_reader_multiple_lines.txt", &lines);
  EXPECT_EQ(2, lines.size());
  EXPECT_EQ("It contains two lines.", lines.at(0));
  EXPECT_EQ("This is the second line.", lines.at(1));
}
