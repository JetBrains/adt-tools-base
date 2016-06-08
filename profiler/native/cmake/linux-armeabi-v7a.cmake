set(PREBUILTS "${CMAKE_CURRENT_LIST_DIR}/../../../../../prebuilts")
set(CMAKE_C_COMPILER   "${PREBUILTS}/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" CACHE PATH "C compiler")
set(CMAKE_CXX_COMPILER "${PREBUILTS}/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-g++" CACHE PATH "CXX compiler")

include(${CMAKE_CURRENT_LIST_DIR}/android-armeabi-v7a.cmake)
