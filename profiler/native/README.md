# Performance tools native

Native (C++) binaries and dependencies used by preformance tools insfrastructure.

## Initializing gRPC

```
# In .../tools/base/profiler/native
git submodule update --init --recursive
cd grpc
git am ../../patches/grpc-android.patch
```
## To compile all the android and host binaries:
```
cd ..
../../../gradlew compileAndroid
```
## To compile only the host binaries:
```
../../../gradlew compileHost
```
## To run the host unit tests:
```
../../../gradlew checkHost
```
## To compile for a specific ABI (arm64-v8a for example):
```
../../../gradlew compileAndroidArm64-v8a
```

