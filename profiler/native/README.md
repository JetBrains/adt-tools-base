# Performance tools native

Native (C++) binaries and dependencies used by preformance tools insfrastructure.

## Initializing gRPC

```
# In .../tools/base/profiler/native
git submodule update --init --recursive
cd grpc
git am ../../patches/grpc-android.patch
```

