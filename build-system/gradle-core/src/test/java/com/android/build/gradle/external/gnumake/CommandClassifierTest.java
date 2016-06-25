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
package com.android.build.gradle.external.gnumake;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.List;

public class CommandClassifierTest {

    private static String classify(String string) {
        List<BuildStepInfo> commandSummaries = CommandClassifier.classify(string, true);
        StringBuilder sb = new StringBuilder();
        for (BuildStepInfo buildStepInfo : commandSummaries) {
            sb.append("[");
            for (String input : buildStepInfo.getInputs()) {
                sb.append(" in:");
                sb.append(input);
                sb.append(" ");
            }
            for (String output : buildStepInfo.getOutputs()) {
                sb.append("out:");
                sb.append(output);
                sb.append(" ");
            }
            sb.append("]");
        }
        return sb.toString()
                .replace("  ", " ");
    }

    private static void assertClassifyContains(String string, @NonNull String expect) {
        assertThat(classify(string)).contains(expect);
    }

    @Test
    public void define() throws FileNotFoundException {
        assertClassifyContains("g++ test.cpp -D BOB", "[ in:test.cpp ]");
        assertClassifyContains("g++ -D BOB test.cpp", "[ in:test.cpp ]");
    }

    @Test
    public void mmd() throws FileNotFoundException {
        assertClassifyContains("g++ -MMD test.cpp -D BOB", "[ in:test.cpp ]");
    }

    @Test
    public void include1() throws FileNotFoundException {
        assertClassifyContains("g++ -I foo bar.c", "[ in:bar.c ]");
    }

    @Test
    public void include2() throws FileNotFoundException {
        assertClassifyContains("g++ -I foo -o bar.o", "[out:bar.o ]");
    }

    @Test
    public void arNoCreate() throws FileNotFoundException {
        assertClassifyContains("gcc-ar bob a.a b.o", "");
    }

    @Test
    public void arSingleCreate() throws FileNotFoundException {
        assertClassifyContains("gcc-ar c a.a b.o", "[ in:b.o out:a.a ]");
    }

    @Test
    public void arMultiCreate() throws FileNotFoundException {
        assertClassifyContains("gcc-ar c a.a b.o c.o", "[ in:b.o in:c.o out:a.a ]");
    }

    @Test
    public void arMultiCommand() throws FileNotFoundException {
        assertClassifyContains("gcc-ar crsD a.a b.o c.o", "[ in:b.o in:c.o out:a.a ]");
    }

    @Test
    public void arPlugin() throws FileNotFoundException {
        assertClassifyContains("gcc-ar --plugin plug crsD a.a b.o c.o", "[ in:b.o in:c.o out:a.a ]");
    }

    @Test
    public void arTarget() throws FileNotFoundException {
        assertClassifyContains("gcc-ar --target targ crsD a.a b.o c.o", "[ in:b.o in:c.o out:a.a ]");
    }

    @Test
    public void arX32_64() throws FileNotFoundException {
        assertClassifyContains("gcc-ar -X32_64 crsD a.a b.o c.o", "[ in:b.o in:c.o out:a.a ]");
    }

    @Test
    public void ndkBuildExample() throws FileNotFoundException {
        assertClassifyContains(
            "rm -f ./libs/arm64-v8a/lib*.so ./libs/armeabi/lib*.so ./libs/armeabi-v7a/lib*.so ./libs/armeabi-v7a-hard/lib*.so ./libs/mips/lib*.so ./libs/mips64/lib*.so ./libs/x86/lib*.so ./libs/x86_64/lib*.so\n" +
            "rm -f ./libs/arm64-v8a/gdbserver ./libs/armeabi/gdbserver ./libs/armeabi-v7a/gdbserver ./libs/armeabi-v7a-hard/gdbserver ./libs/mips/gdbserver ./libs/mips64/gdbserver ./libs/x86/gdbserver ./libs/x86_64/gdbserver\n" +
            "rm -f ./libs/arm64-v8a/gdb.setup ./libs/armeabi/gdb.setup ./libs/armeabi-v7a/gdb.setup ./libs/armeabi-v7a-hard/gdb.setup ./libs/mips/gdb.setup ./libs/mips64/gdb.setup ./libs/x86/gdb.setup ./libs/x86_64/gdb.setup\n" +
            "mkdir -p libs/arm64-v8a\n" +
            "echo [arm64-v8a] \"Gdbserver      \": \"[aarch64-linux-android-4.9] libs/arm64-v8a/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-arm64/gdbserver/gdbserver ./libs/arm64-v8a/gdbserver\n" +
            "echo [arm64-v8a] \"Gdbsetup       \": \"libs/arm64-v8a/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/arm64-v8a\" > ./libs/arm64-v8a/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/arm64-v8a/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-arm64/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/arm64-v8a/gdb.setup\n" +
            "mkdir -p libs/x86_64\n" +
            "echo [x86_64] \"Gdbserver      \": \"[x86_64-4.9] libs/x86_64/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-x86_64/gdbserver/gdbserver ./libs/x86_64/gdbserver\n" +
            "echo [x86_64] \"Gdbsetup       \": \"libs/x86_64/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/x86_64\" > ./libs/x86_64/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/x86_64/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-x86_64/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/x86_64/gdb.setup\n" +
            "mkdir -p libs/mips64\n" +
            "echo [mips64] \"Gdbserver      \": \"[mips64el-linux-android-4.9] libs/mips64/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-mips64/gdbserver/gdbserver ./libs/mips64/gdbserver\n" +
            "echo [mips64] \"Gdbsetup       \": \"libs/mips64/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/mips64\" > ./libs/mips64/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/mips64/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-mips64/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/mips64/gdb.setup\n" +
            "mkdir -p libs/armeabi-v7a\n" +
            "echo [armeabi-v7a] \"Gdbserver      \": \"[arm-linux-androideabi-4.8] libs/armeabi-v7a/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-arm/gdbserver/gdbserver ./libs/armeabi-v7a/gdbserver\n" +
            "echo [armeabi-v7a] \"Gdbsetup       \": \"libs/armeabi-v7a/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/armeabi-v7a\" > ./libs/armeabi-v7a/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/armeabi-v7a/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-3/arch-arm/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/armeabi-v7a/gdb.setup\n" +
            "mkdir -p libs/armeabi\n" +
            "echo [armeabi] \"Gdbserver      \": \"[arm-linux-androideabi-4.8] libs/armeabi/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-arm/gdbserver/gdbserver ./libs/armeabi/gdbserver\n" +
            "echo [armeabi] \"Gdbsetup       \": \"libs/armeabi/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/armeabi\" > ./libs/armeabi/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/armeabi/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-3/arch-arm/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/armeabi/gdb.setup\n" +
            "mkdir -p libs/x86\n" +
            "echo [x86] \"Gdbserver      \": \"[x86-4.8] libs/x86/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-x86/gdbserver/gdbserver ./libs/x86/gdbserver\n" +
            "echo [x86] \"Gdbsetup       \": \"libs/x86/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/x86\" > ./libs/x86/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/x86/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-9/arch-x86/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/x86/gdb.setup\n" +
            "mkdir -p libs/mips\n" +
            "echo [mips] \"Gdbserver      \": \"[mipsel-linux-android-4.8] libs/mips/gdbserver\"\n" +
            "install -p /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/android-mips/gdbserver/gdbserver ./libs/mips/gdbserver\n" +
            "echo [mips] \"Gdbsetup       \": \"libs/mips/gdb.setup\"\n" +
            "echo \"set solib-search-path ./obj/local/mips\" > ./libs/mips/gdb.setup\n" +
            "echo \"source /usr/local/google/home/jomof/bin/android-ndk-r10e/prebuilt/common/gdb/common.setup\" >> ./libs/mips/gdb.setup\n" +
            "echo \"directory /usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-9/arch-mips/usr/include jni /usr/local/google/home/jomof/bin/android-ndk-r10e/sources/cxx-stl/system\" >> ./libs/mips/gdb.setup\n" +
            "mkdir -p obj/local/arm64-v8a/objs-debug/hello-jni\n" +
            "echo [arm64-v8a] \"Compile        \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-gcc -MMD -MP -MF ./obj/local/arm64-v8a/objs-debug/hello-jni/hello-jni.o.d -fpic -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -O2 -g -DNDEBUG -fomit-frame-pointer -fstrict-aliasing -funswitch-loops -finline-limit=300 -O0 -UNDEBUG -fno-omit-frame-pointer -fno-strict-aliasing -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-arm64/usr/include -c  jni/hello-jni.c -o ./obj/local/arm64-v8a/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/arm64-v8a\n" +
            "echo [arm64-v8a] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-arm64 ./obj/local/arm64-v8a/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/arm64-v8a/libhello-jni.so\n" +
            "echo [arm64-v8a] \"Install        \": \"libhello-jni.so => libs/arm64-v8a/libhello-jni.so\"\n" +
            "install -p ./obj/local/arm64-v8a/libhello-jni.so ./libs/arm64-v8a/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-strip --strip-unneeded  ./libs/arm64-v8a/libhello-jni.so\n" +
            "mkdir -p obj/local/x86_64/objs-debug/hello-jni\n" +
            "echo [x86_64] \"Compile        \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/x86_64-4.9/prebuilt/linux-x86_64/bin/x86_64-linux-android-gcc -MMD -MP -MF ./obj/local/x86_64/objs-debug/hello-jni/hello-jni.o.d -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -O2 -g -DNDEBUG -fomit-frame-pointer -fstrict-aliasing -funswitch-loops -finline-limit=300 -O0 -UNDEBUG -fno-omit-frame-pointer -fno-strict-aliasing -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-x86_64/usr/include -c  jni/hello-jni.c -o ./obj/local/x86_64/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/x86_64\n" +
            "echo [x86_64] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/x86_64-4.9/prebuilt/linux-x86_64/bin/x86_64-linux-android-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-x86_64 ./obj/local/x86_64/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/x86_64/libhello-jni.so\n" +
            "echo [x86_64] \"Install        \": \"libhello-jni.so => libs/x86_64/libhello-jni.so\"\n" +
            "install -p ./obj/local/x86_64/libhello-jni.so ./libs/x86_64/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/x86_64-4.9/prebuilt/linux-x86_64/bin/x86_64-linux-android-strip --strip-unneeded  ./libs/x86_64/libhello-jni.so\n" +
            "mkdir -p obj/local/mips64/objs-debug/hello-jni\n" +
            "echo [mips64] \"Compile        \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/mips64el-linux-android-4.9/prebuilt/linux-x86_64/bin/mips64el-linux-android-gcc -MMD -MP -MF ./obj/local/mips64/objs-debug/hello-jni/hello-jni.o.d -fpic -fno-strict-aliasing -finline-functions -ffunction-sections -funwind-tables -fmessage-length=0 -fno-inline-functions-called-once -fgcse-after-reload -frerun-cse-after-loop -frename-registers -no-canonical-prefixes -O0 -g -fno-omit-frame-pointer -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-mips64/usr/include -c  jni/hello-jni.c -o ./obj/local/mips64/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/mips64\n" +
            "echo [mips64] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/mips64el-linux-android-4.9/prebuilt/linux-x86_64/bin/mips64el-linux-android-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-21/arch-mips64 ./obj/local/mips64/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/mips64/libhello-jni.so\n" +
            "echo [mips64] \"Install        \": \"libhello-jni.so => libs/mips64/libhello-jni.so\"\n" +
            "install -p ./obj/local/mips64/libhello-jni.so ./libs/mips64/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/mips64el-linux-android-4.9/prebuilt/linux-x86_64/bin/mips64el-linux-android-strip --strip-unneeded  ./libs/mips64/libhello-jni.so\n" +
            "mkdir -p obj/local/armeabi-v7a/objs-debug/hello-jni\n" +
            "echo [armeabi-v7a] \"Compile thumb  \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin/arm-linux-androideabi-gcc -MMD -MP -MF ./obj/local/armeabi-v7a/objs-debug/hello-jni/hello-jni.o.d -fpic -ffunction-sections -funwind-tables -fstack-protector -no-canonical-prefixes -march=armv7-a -mfpu=vfpv3-d16 -mfloat-abi=softfp -mthumb -Os -g -DNDEBUG -fomit-frame-pointer -fno-strict-aliasing -finline-limit=64 -O0 -UNDEBUG -marm -fno-omit-frame-pointer -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-3/arch-arm/usr/include -c  jni/hello-jni.c -o ./obj/local/armeabi-v7a/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/armeabi-v7a\n" +
            "echo [armeabi-v7a] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin/arm-linux-androideabi-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-3/arch-arm ./obj/local/armeabi-v7a/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes -march=armv7-a -Wl,--fix-cortex-a8  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/armeabi-v7a/libhello-jni.so\n" +
            "echo [armeabi-v7a] \"Install        \": \"libhello-jni.so => libs/armeabi-v7a/libhello-jni.so\"\n" +
            "install -p ./obj/local/armeabi-v7a/libhello-jni.so ./libs/armeabi-v7a/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin/arm-linux-androideabi-strip --strip-unneeded  ./libs/armeabi-v7a/libhello-jni.so\n" +
            "mkdir -p obj/local/armeabi/objs-debug/hello-jni\n" +
            "echo [armeabi] \"Compile thumb  \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin/arm-linux-androideabi-gcc -MMD -MP -MF ./obj/local/armeabi/objs-debug/hello-jni/hello-jni.o.d -fpic -ffunction-sections -funwind-tables -fstack-protector -no-canonical-prefixes -march=armv5te -mtune=xscale -msoft-float -mthumb -Os -g -DNDEBUG -fomit-frame-pointer -fno-strict-aliasing -finline-limit=64 -O0 -UNDEBUG -marm -fno-omit-frame-pointer -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-3/arch-arm/usr/include -c  jni/hello-jni.c -o ./obj/local/armeabi/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/armeabi\n" +
            "echo [armeabi] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin/arm-linux-androideabi-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-3/arch-arm ./obj/local/armeabi/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/armeabi/libhello-jni.so\n" +
            "echo [armeabi] \"Install        \": \"libhello-jni.so => libs/armeabi/libhello-jni.so\"\n" +
            "install -p ./obj/local/armeabi/libhello-jni.so ./libs/armeabi/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin/arm-linux-androideabi-strip --strip-unneeded  ./libs/armeabi/libhello-jni.so\n" +
            "mkdir -p obj/local/x86/objs-debug/hello-jni\n" +
            "echo [x86] \"Compile        \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/x86-4.8/prebuilt/linux-x86_64/bin/i686-linux-android-gcc -MMD -MP -MF ./obj/local/x86/objs-debug/hello-jni/hello-jni.o.d -ffunction-sections -funwind-tables -no-canonical-prefixes -fstack-protector -O2 -g -DNDEBUG -fomit-frame-pointer -fstrict-aliasing -funswitch-loops -finline-limit=300 -O0 -UNDEBUG -fno-omit-frame-pointer -fno-strict-aliasing -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-9/arch-x86/usr/include -c  jni/hello-jni.c -o ./obj/local/x86/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/x86\n" +
            "echo [x86] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/x86-4.8/prebuilt/linux-x86_64/bin/i686-linux-android-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-9/arch-x86 ./obj/local/x86/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/x86/libhello-jni.so\n" +
            "echo [x86] \"Install        \": \"libhello-jni.so => libs/x86/libhello-jni.so\"\n" +
            "install -p ./obj/local/x86/libhello-jni.so ./libs/x86/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/x86-4.8/prebuilt/linux-x86_64/bin/i686-linux-android-strip --strip-unneeded  ./libs/x86/libhello-jni.so\n" +
            "mkdir -p obj/local/mips/objs-debug/hello-jni\n" +
            "echo [mips] \"Compile        \": \"hello-jni <= hello-jni.c\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/mipsel-linux-android-4.8/prebuilt/linux-x86_64/bin/mipsel-linux-android-gcc -MMD -MP -MF ./obj/local/mips/objs-debug/hello-jni/hello-jni.o.d -fpic -fno-strict-aliasing -finline-functions -ffunction-sections -funwind-tables -fmessage-length=0 -fno-inline-functions-called-once -fgcse-after-reload -frerun-cse-after-loop -frename-registers -no-canonical-prefixes -O0 -g -fno-omit-frame-pointer -Ijni -DANDROID  -Wa,--noexecstack -Wformat -Werror=format-security    -I/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-9/arch-mips/usr/include -c  jni/hello-jni.c -o ./obj/local/mips/objs-debug/hello-jni/hello-jni.o \n" +
            "mkdir -p obj/local/mips\n" +
            "echo [mips] \"SharedLibrary  \": \"libhello-jni.so\"\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/mipsel-linux-android-4.8/prebuilt/linux-x86_64/bin/mipsel-linux-android-g++ -Wl,-soname,libhello-jni.so -shared --sysroot=/usr/local/google/home/jomof/bin/android-ndk-r10e/platforms/android-9/arch-mips ./obj/local/mips/objs-debug/hello-jni/hello-jni.o -lgcc -no-canonical-prefixes  -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now   -lc -lm -o ./obj/local/mips/libhello-jni.so\n" +
            "echo [mips] \"Install        \": \"libhello-jni.so => libs/mips/libhello-jni.so\"\n" +
            "install -p ./obj/local/mips/libhello-jni.so ./libs/mips/libhello-jni.so\n" +
            "/usr/local/google/home/jomof/bin/android-ndk-r10e/toolchains/mipsel-linux-android-4.8/prebuilt/linux-x86_64/bin/mipsel-linux-android-strip --strip-unneeded  ./libs/mips/libhello-jni.so", "[ in:jni/hello-jni.c out:./obj/local/arm64-v8a/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/arm64-v8a/objs-debug/hello-jni/hello-jni.o out:./obj/local/arm64-v8a/libhello-jni.so ][ in:jni/hello-jni.c out:./obj/local/x86_64/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/x86_64/objs-debug/hello-jni/hello-jni.o out:./obj/local/x86_64/libhello-jni.so ][ in:jni/hello-jni.c out:./obj/local/mips64/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/mips64/objs-debug/hello-jni/hello-jni.o out:./obj/local/mips64/libhello-jni.so ][ in:jni/hello-jni.c out:./obj/local/armeabi-v7a/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/armeabi-v7a/objs-debug/hello-jni/hello-jni.o out:./obj/local/armeabi-v7a/libhello-jni.so ][ in:jni/hello-jni.c out:./obj/local/armeabi/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/armeabi/objs-debug/hello-jni/hello-jni.o out:./obj/local/armeabi/libhello-jni.so ][ in:jni/hello-jni.c out:./obj/local/x86/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/x86/objs-debug/hello-jni/hello-jni.o out:./obj/local/x86/libhello-jni.so ][ in:jni/hello-jni.c out:./obj/local/mips/objs-debug/hello-jni/hello-jni.o ][ in:./obj/local/mips/objs-debug/hello-jni/hello-jni.o out:./obj/local/mips/libhello-jni.so ]");

    }

    @Test
    public void simple() throws FileNotFoundException {
        assertClassifyContains("g++ a.c -o a.o\n" +
                "g++ a.o -o a.so", "[ in:a.c out:a.o ][ in:a.o out:a.so ]");
    }

    @Test
    public void undefine() throws FileNotFoundException {
        assertClassifyContains("g++ test.cpp -D BOB", "[ in:test.cpp ]");
        assertClassifyContains("g++ -D BOB test.cpp", "[ in:test.cpp ]");
    }

    @Test
    public void testFlagWithDash() {
        assertClassifyContains("/usr/linux-x86_64/bin/clang++ -MMD -MP "
                + "-MF /usr/hello-jni.o.d "
                + "-gcc-toolchain /usr/prebuilt/linux-x86_64 -target mips64el-none-linux-android "
                + "-fpic -fno-strict-aliasing -finline-functions -ffunction-sections "
                + "-funwind-tables -fmessage-length=0 -Wno-invalid-command-line-argument "
                + "-Wno-unused-command-line-argument -no-canonical-prefixes -fno-exceptions "
                + "-fno-rtti -O2 -g -DNDEBUG -fomit-frame-pointer -I/usr/cxx-stl/system/include "
                + "-I/usr/project/src/main/cxx -DANDROID -Wa,--noexecstack -Wformat "
                + "-Werror=format-security -DTEST_C_FLAG -DTEST_CPP_FLAG -DTEST_CUSTOM_VARIANT_FLAG "
                + "-I/usr/android-21/arch-mips64/usr/include "
                + "-c /usr/project/src/main/cxx/hello-jni.cpp "
                + "-o /usr/project/build/hello-jni.o", "/usr/project/src/main/cxx/hello-jni.cpp");
    }
}
