# Android Studio ProfilerApp Setup Instructions

### Environment Variables

###### MAVEL_URL
This env var is needed by the Gradle test infrastructure in anticipation that we will be running the app as part of Gradle integration tests. It can be added either in your ~/.bashrc (~/.bash_profile on Mac) or directly in the Android Studio run configuration in IntelliJ.

This can be pointed to the latest version of the artifacts under your source tree: {studio_master_dev}/prebuilts/tools/common/offline-m2/ 


### Dependencies

###### NDK
The Native Development Kit is required by the Memory component of the app to perform native allocations via JNI.
To setup NDK, follow the instructions here: http://developer.android.com/ndk/guides/setup.html

The NDK directory should be added to $PATH. Also, add the following line to "local.properties" inside the ProfilerApp project:

```sh
ndk.dir={YOUR_NDK_DIR_PATH}
```

