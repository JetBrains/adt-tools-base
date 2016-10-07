package com.android.tests.conditionalApiUse;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresApi(21)
public class AccessToApi21AndAbove {

    public List<String> lowLevelistCameras(Context context) throws MyException {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d("InconsistentAPI", "Nb of cameras " + cameraManager.getCameraIdList().length);
            return Arrays.asList(cameraManager.getCameraIdList());
        } catch (CameraAccessException e) {
            throw new MyException(e.getReason(), e.getMessage(), e.getCause());
        }
    }

    public List<String> listCameras(Context context) {
        try {
            return lowLevelistCameras(context);
        } catch (MyException e) {
            List<String> errors = new ArrayList<>();
            errors.add(e.getMessage());
            return errors;
        }
    }
}
