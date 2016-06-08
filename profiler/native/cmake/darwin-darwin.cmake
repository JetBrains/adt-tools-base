set(PREBUILTS "${CMAKE_CURRENT_LIST_DIR}/../../../../../prebuilts")
set(STL "${PREBUILTS}/clang/darwin-x86/sdk/3.5/include/c++/v1")

set(JDK "${PREBUILTS}/studio/jdk/mac/Contents/Home")
set(TARGET "-target x86_64-apple-macosx10.11.0")
set(COMMON_FLAGS "${TARGET} -I${JDK}/include -I${JDK}/include/darwin")

set(CMAKE_C_COMPILER   "${PREBUILTS}/clang/darwin-x86/host/3.5/bin/clang" CACHE PATH "C compiler")
set(CMAKE_CXX_COMPILER "${PREBUILTS}/clang/darwin-x86/host/3.5/bin/clang++" CACHE PATH "CXX compiler")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} ${TARGET}" CACHE STRING "ASM Flags")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS}" CACHE STRING "C flags")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -stdlib=libc++ -I${STL}" CACHE STRING "CXX flags")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS}" CACHE STRING "Exevutable Library linker flags")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}" CACHE STRING "Shared Library linker flags")

