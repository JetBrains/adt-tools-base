set(PREBUILTS "${CMAKE_CURRENT_LIST_DIR}/../../../../../prebuilts")
set(CMAKE_C_COMPILER   "${PREBUILTS}/gcc/darwin-x86/x86/x86_64-linux-android-4.9/bin/x86_64-linux-android-gcc" CACHE PATH "C compiler")
set(CMAKE_CXX_COMPILER "${PREBUILTS}/gcc/darwin-x86/x86/x86_64-linux-android-4.9/bin/x86_64-linux-android-g++" CACHE PATH "CXX compiler")

include(${CMAKE_CURRENT_LIST_DIR}/android-x86.cmake)
