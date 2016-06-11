set(PREBUILTS "${CMAKE_CURRENT_LIST_DIR}/../../../../../prebuilts")
set(STL "${PREBUILTS}/gcc/linux-x86/host/x86_64-linux-glibc2.15-4.8")

set(JDK "${PREBUILTS}/studio/jdk/linux")
set(TARGET "-target x86_64-unknown-linux")
set(COMMON_FLAGS "${TARGET} -B${STL}/bin/x86_64-linux- -I${JDK}/include -I${JDK}/include/linux")


set(CMAKE_C_COMPILER   "${PREBUILTS}/clang/linux-x86/host/3.6/bin/clang" CACHE PATH "C compiler")
set(CMAKE_CXX_COMPILER "${PREBUILTS}/clang/linux-x86/host/3.6/bin/clang++" CACHE PATH "CXX compiler")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} ${TARGET}" CACHE STRING "ASM Flags")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS}" CACHE STRING "C flags")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -std=c++11 -stdlib=libstdc++ -I${STL}/x86_64-linux/include/c++/4.8/ -I${STL}/x86_64-linux/include/c++/4.8/x86_64-linux/" CACHE STRING "CXX flags")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -L${STL}/x86_64-linux/lib64" CACHE STRING "Executable Library linker flags")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -L${STL}/x86_64-linux/lib64" CACHE STRING "Shared Library linker flags")
