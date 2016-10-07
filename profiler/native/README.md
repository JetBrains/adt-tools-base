# Performance tools native

Native (C++) binaries and dependencies used by preformance tools insfrastructure.

**All sections below expect you to start in `.../tools/base/profiler/native`**

## To compile all the android and host binaries:
```
../../../gradlew compileAndroid
```
## To compile only the host binaries:
```
../../../gradlew compileHost
```
## To run the host unit tests (If testdata/ is not updated, clear gen/):
```
../../../gradlew checkHost
```
## To compile for a specific ABI (arm64-v8a for example):
```
../../../gradlew compileAndroidArm64-v8a
```

