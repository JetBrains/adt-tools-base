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
#include "timespec_math.h"

#include <gtest/gtest.h>

using utils::TimespecMath;

TEST(Add, PositiveNanoSecondOverload) {
  // sec are random positive integers, sum of nsec is > 2 * 1e9.
  timespec t1 = {2, 500000001};
  timespec t2 = {7, 1500000008};
  timespec result;

  TimespecMath::Add(t1, t2, &result);
  EXPECT_EQ(11, result.tv_sec);
  EXPECT_EQ(9, result.tv_nsec);
}

TEST(Add, NegativeNanoSecondOverload) {
  // sec are random negative integers, sum of nsec is < -2 * 1e9.
  timespec t1 = {-2, -500000001};
  timespec t2 = {-7, -1500000008};
  timespec result;

  TimespecMath::Add(t1, t2, &result);
  EXPECT_EQ(-12, result.tv_sec);
  EXPECT_EQ(999999991, result.tv_nsec);
}

TEST(Add, OnePositiveOneNegativeToZero) {
  timespec t1 = {1, 100000000};
  timespec t2 = {-1, -100000000};
  timespec result;

  TimespecMath::Add(t1, t2, &result);
  EXPECT_EQ(0, result.tv_sec);
  EXPECT_EQ(0, result.tv_nsec);
}

TEST(Subtract, PositiveNanoSecondOverload) {
  timespec t1 = {2, 500000001};
  timespec t2 = {-7, -1500000008};
  timespec result;

  TimespecMath::Subtract(t1, t2, &result);
  EXPECT_EQ(11, result.tv_sec);
  EXPECT_EQ(9, result.tv_nsec);
}

TEST(Subtract, NegativeNanoSecondOverload) {
  timespec t1 = {-2, -500000001};
  timespec t2 = {7, 1500000008};
  timespec result;

  TimespecMath::Subtract(t1, t2, &result);
  EXPECT_EQ(-12, result.tv_sec);
  EXPECT_EQ(999999991, result.tv_nsec);
}

TEST(Subtract, SameValueToZero) {
  timespec t1 = {1, 100000000};
  timespec t2 = {1, 100000000};
  timespec result;

  TimespecMath::Subtract(t1, t2, &result);
  EXPECT_EQ(0, result.tv_sec);
  EXPECT_EQ(0, result.tv_nsec);
}

TEST(Compare, AllPossibleOutputs) {
  timespec t1 = {1, 0};
  timespec t2 = {2, -2000000000};
  EXPECT_EQ(1, TimespecMath::Compare(t1, t2));

  timespec t3 = {0, 2000000000};
  EXPECT_EQ(-1, TimespecMath::Compare(t1, t3));

  timespec t4 = {0, 1000000000};
  EXPECT_EQ(0, TimespecMath::Compare(t1, t4));
}
