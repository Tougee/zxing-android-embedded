/*
 * Copyright (C) 2008 ZXing authors
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

package com.journeyapps.barcodescanner.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.google.zxing.client.android.AmbientLightManager;
import com.google.zxing.client.android.camera.CameraConfigurationUtils;
import com.google.zxing.client.android.camera.open.OpenCameraInterface;
import com.journeyapps.barcodescanner.Size;
import com.journeyapps.barcodescanner.SourceData;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper to manage the Camera. This is not thread-safe, and the methods must always be called
 * from the same thread.
 * <p>
 * <p>
 * Call order:
 * <p>
 * 1. setCameraSettings()
 * 2. open(), set desired preview size (any order)
 * 3. configure(), setPreviewDisplay(holder) (any order)
 * 4. startPreview()
 * 5. requestPreviewFrame (repeat)
 * 6. stopPreview()
 * 7. close()
 */
public final class CameraManager {

    private static final String TAG = CameraManager.class.getSimpleName();

    private Camera camera;
    private Camera.CameraInfo cameraInfo;

    private AutoFocusManager autoFocusManager;
    private AmbientLightManager ambientLightManager;

    private boolean previewing;
    private String defaultParameters;

    // User parameters
    private CameraSettings settings = new CameraSettings();

    private DisplayConfiguration displayConfiguration;

    // Actual chosen preview size
    private Size requestedPreviewSize;
    private Size previewSize;
    private Size requestedPictureSize;
    private Size pictureSize;

    private int rotationDegrees = -1;    // camera rotation vs display rotation

    private Context context;

    private final class CameraPreviewCallback implements Camera.PreviewCallback {
        private PreviewCallback callback;

        private Size resolution;

        public CameraPreviewCallback() {
        }

        public void setResolution(Size resolution) {
            this.resolution = resolution;
        }

        public void setCallback(PreviewCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Size cameraResolution = resolution;
            PreviewCallback callback = this.callback;
            if (cameraResolution != null && callback != null) {
                try {
                    if (data == null) {
                        throw new NullPointerException("No preview data received");
                    }
                    int format = camera.getParameters().getPreviewFormat();
                    SourceData source =
                            new SourceData(data, cameraResolution.width, cameraResolution.height, format,
                                    getCameraRotation());
                    callback.onPreview(source);
                } catch (RuntimeException e) {
                    // Could be:
                    // java.lang.RuntimeException: getParameters failed (empty parameters)
                    // IllegalArgumentException: Image data does not match the resolution
                    Log.e(TAG, "Camera preview failed", e);
                    callback.onPreviewError(e);
                }
            } else {
                Log.d(TAG, "Got preview callback, but no handler or resolution available");
                if (callback != null) {
                    // Should generally not happen
                    callback.onPreviewError(new Exception("No resolution available"));
                }
            }
        }
    }

    private final class CameraPictureCallback implements Camera.PictureCallback {
        private PictureCallback callback;

        private Size resolution;

        public CameraPictureCallback() {
        }

        public void setResolution(Size resolution) {
            this.resolution = resolution;
        }

        public void setCallback(PictureCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Size cameraResolution = resolution;
            PictureCallback callback = this.callback;
            if (callback != null) {
                try {
                    if (data == null) {
                        throw new NullPointerException("No preview data received");
                    }
                    int format = camera.getParameters().getPreviewFormat();
                    SourceData source = new SourceData(data, cameraResolution.width, cameraResolution.height, format,
                            getCameraRotation());
                    callback.onPicture(source);
                } catch (RuntimeException e) {
                    // Could be:
                    // java.lang.RuntimeException: getParameters failed (empty parameters)
                    // IllegalArgumentException: Image data does not match the resolution
                    Log.e(TAG, "Camera preview failed", e);
                    callback.onPictureError(e);
                }
            } else {
                Log.d(TAG, "Got preview callback, but no handler or resolution available");
            }
        }
    }

    /**
     * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
     * clear the handler so it will only receive one message.
     */
    private final CameraPreviewCallback cameraPreviewCallback;
    private final CameraPictureCallback pictureCallback;

    public CameraManager(Context context) {
        this.context = context;
        cameraPreviewCallback = new CameraPreviewCallback();
        pictureCallback = new CameraPictureCallback();
    }

    /**
     * Must be called from camera thread.
     */
    public void open() {
        camera = OpenCameraInterface.open(settings.getRequestedCameraId());
        if (camera == null) {
            throw new RuntimeException("Failed to open camera");
        }

        int cameraId = OpenCameraInterface.getCameraId(settings.getRequestedCameraId());
        cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);
    }

    /**
     * Configure the camera parameters, including preview size.
     * <p>
     * The camera must be opened before calling this.
     * <p>
     * Must be called from camera thread.
     */
    public void configure() {
        if (camera == null) {
            throw new RuntimeException("Camera not open");
        }
        setParameters();
    }

    /**
     * Must be called from camera thread.
     */
    public void setPreviewDisplay(SurfaceHolder holder) throws IOException {
        setPreviewDisplay(new CameraSurface(holder));
    }

    public void setPreviewDisplay(CameraSurface surface) throws IOException {
        surface.setPreview(camera);
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     * <p>
     * Must be called from camera thread.
     */
    public void startPreview() {
        Camera theCamera = camera;
        if (theCamera != null && !previewing) {
            theCamera.startPreview();
            previewing = true;
            autoFocusManager = new AutoFocusManager(camera, settings);
            ambientLightManager = new AmbientLightManager(context, this, settings);
            ambientLightManager.start();
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     * <p>
     * Must be called from camera thread.
     */
    public void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (ambientLightManager != null) {
            ambientLightManager.stop();
            ambientLightManager = null;
        }
        if (camera != null && previewing) {
            camera.stopPreview();
            cameraPreviewCallback.setCallback(null);
            pictureCallback.setCallback(null);
            previewing = false;
        }
    }

    /**
     * Closes the camera driver if still in use.
     * <p>
     * Must be called from camera thread.
     */
    public void close() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    /**
     * @return true if the camera rotation is perpendicular to the current display rotation.
     */
    public boolean isCameraRotated() {
        if (rotationDegrees == -1) {
            throw new IllegalStateException("Rotation not calculated yet. Call configure() first.");
        }
        return rotationDegrees % 180 != 0;
    }

    /**
     * @return the camera rotation relative to display rotation, in degrees. Typically 0 if the
     * display is in landscape orientation.
     */
    public int getCameraRotation() {
        return rotationDegrees;
    }

    private Camera.Parameters getDefaultCameraParameters() {
        Camera.Parameters parameters = camera.getParameters();
        if (defaultParameters == null) {
            defaultParameters = parameters.flatten();
        } else {
            parameters.unflatten(defaultParameters);
        }
        return parameters;
    }

    private void setDesiredParameters(boolean safeMode) {
        Camera.Parameters parameters = getDefaultCameraParameters();

        //noinspection ConstantConditions
        if (parameters == null) {
            Log.w(TAG,
                    "Device error: no camera parameters are available. Proceeding without configuration.");
            return;
        }

        Log.i(TAG, "Initial camera parameters: " + parameters.flatten());

        if (safeMode) {
            Log.w(TAG, "In camera config safe mode -- most settings will not be honored");
        }

        CameraConfigurationUtils.setFocus(parameters, settings.getFocusMode(), safeMode);

        if (!safeMode) {
            CameraConfigurationUtils.setTorch(parameters, false);

            if (settings.isScanInverted()) {
                CameraConfigurationUtils.setInvertColor(parameters);
            }

            if (settings.isBarcodeSceneModeEnabled()) {
                CameraConfigurationUtils.setBarcodeSceneMode(parameters);
            }

            if (settings.isMeteringEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    CameraConfigurationUtils.setVideoStabilization(parameters);
                    CameraConfigurationUtils.setFocusArea(parameters);
                    CameraConfigurationUtils.setMetering(parameters);
                }
            }
        }

        List<Size> previewSizes = getPreviewSizes(parameters, true);
        if (previewSizes.size() == 0) {
            requestedPreviewSize = null;
        } else {
            requestedPreviewSize =
                    displayConfiguration.getBestPreviewSize(previewSizes, isCameraRotated());

            parameters.setPreviewSize(requestedPreviewSize.width, requestedPreviewSize.height);
        }

        List<Size> pictureSizes = getPictureSizes(parameters);
        if (pictureSizes.isEmpty()) {
            requestedPictureSize = null;
        } else {
            requestedPictureSize = displayConfiguration.getBestPreviewSize(pictureSizes, isCameraRotated());
            parameters.setPictureSize(requestedPictureSize.width, requestedPictureSize.height);
        }

        if (Build.DEVICE.equals("glass-1")) {
            // We need to set the FPS on Google Glass devices, otherwise the preview is scrambled.
            // FIXME - can/should we do this for other devices as well?
            CameraConfigurationUtils.setBestPreviewFPS(parameters);
        }

        Log.i(TAG, "Final camera parameters: " + parameters.flatten());

        camera.setParameters(parameters);
    }

    private static List<Size> getPreviewSizes(Camera.Parameters parameters, boolean preview) {
        List<Camera.Size> rawSupportedSizes;
        if (preview) {
            rawSupportedSizes = parameters.getSupportedPreviewSizes();
        } else {
            rawSupportedSizes = parameters.getSupportedVideoSizes();
        }
        List<Size> previewSizes = new ArrayList<>();
        if (rawSupportedSizes == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            if (defaultSize != null) {
                // Work around potential platform bugs
                previewSizes.add(new Size(defaultSize.width, defaultSize.height));
            }
            return previewSizes;
        }
        for (Camera.Size size : rawSupportedSizes) {
            previewSizes.add(new Size(size.width, size.height));
        }
        return previewSizes;
    }

    private static List<Size> getPictureSizes(Camera.Parameters parameters) {
        List<Camera.Size> rawSupportedSizes;
        rawSupportedSizes = parameters.getSupportedPictureSizes();
        List<Size> pictureSizes = new ArrayList<>();
        if (rawSupportedSizes == null) {
            Camera.Size defaultSize = parameters.getPictureSize();
            if (defaultSize != null) {
                // Work around potential platform bugs
                pictureSizes.add(new Size(defaultSize.width, defaultSize.height));
            }
            return pictureSizes;
        }
        for (Camera.Size size : rawSupportedSizes) {
            pictureSizes.add(new Size(size.width, size.height));
        }
        return pictureSizes;
    }


    private int calculateDisplayRotation() {
        // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
        int rotation = displayConfiguration.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        Log.i(TAG, "Camera Display Orientation: " + result);
        return result;
    }

    private void setCameraDisplayOrientation(int rotation) {
        camera.setDisplayOrientation(rotation);
    }

    private void setParameters() {
        try {
            this.rotationDegrees = calculateDisplayRotation();
            setCameraDisplayOrientation(rotationDegrees);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set rotation.");
        }
        try {
            setDesiredParameters(false);
        } catch (Exception e) {
            // Failed, use safe mode
            try {
                setDesiredParameters(true);
            } catch (Exception e2) {
                // Well, darn. Give up
                Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
            }
        }

        Camera.Size realPreviewSize = camera.getParameters().getPreviewSize();
        if (realPreviewSize == null) {
            previewSize = requestedPreviewSize;
        } else {
            previewSize = new Size(realPreviewSize.width, realPreviewSize.height);
        }
        cameraPreviewCallback.setResolution(previewSize);

        Camera.Size realPictureSize = camera.getParameters().getPictureSize();
        if (requestedPictureSize != null) {
            pictureSize = requestedPictureSize;
        } else {
            pictureSize = new Size(realPictureSize.width, realPictureSize.height);
        }
        pictureCallback.setResolution(pictureSize);
    }

    /**
     * This returns false if the camera is not opened yet, failed to open, or has
     * been closed.
     */
    public boolean isOpen() {
        return camera != null;
    }

    /**
     * Actual preview size in *natural camera* orientation. null if not determined yet.
     *
     * @return preview size
     */
    public Size getNaturalPreviewSize() {
        return previewSize;
    }

    /**
     * Actual preview size in *current display* rotation. null if not determined yet.
     *
     * @return preview size
     */
    public Size getPreviewSize() {
        if (previewSize == null) {
            return null;
        } else if (this.isCameraRotated()) {
            return previewSize.rotate();
        } else {
            return previewSize;
        }
    }

    /**
     * A single preview frame will be returned to the supplied callback.
     * <p>
     * The thread on which this called is undefined, so a Handler should be used to post the result
     * to the correct thread.
     *
     * @param callback The callback to receive the preview.
     */
    public void requestPreviewFrame(PreviewCallback callback) {
        Camera theCamera = camera;
        if (theCamera != null && previewing) {
            cameraPreviewCallback.setCallback(callback);
            theCamera.setOneShotPreviewCallback(cameraPreviewCallback);
        }
    }

    public void requestPicture(PictureCallback callback) {
        Camera theCamera = camera;
        if (theCamera != null && previewing) {
            pictureCallback.setCallback(callback);
            theCamera.takePicture(null, null, pictureCallback);
        }
    }

    public CameraSettings getCameraSettings() {
        return settings;
    }

    public void setCameraSettings(CameraSettings settings) {
        this.settings = settings;
    }

    public DisplayConfiguration getDisplayConfiguration() {
        return displayConfiguration;
    }

    public void setDisplayConfiguration(DisplayConfiguration displayConfiguration) {
        this.displayConfiguration = displayConfiguration;
    }

    public void setTorch(boolean on) {
        if (camera != null) {
            try {
                boolean isOn = isTorchOn();
                if (on != isOn) {
                    if (autoFocusManager != null) {
                        autoFocusManager.stop();
                    }

                    Camera.Parameters parameters = camera.getParameters();
                    CameraConfigurationUtils.setTorch(parameters, on);
                    if (settings.isExposureEnabled()) {
                        CameraConfigurationUtils.setBestExposure(parameters, on);
                    }
                    camera.setParameters(parameters);

                    if (autoFocusManager != null) {
                        autoFocusManager.start();
                    }
                }
            } catch (RuntimeException e) {
                // Camera error. Could happen if the camera is being closed.
                Log.e(TAG, "Failed to set torch", e);
            }
        }
    }

    /**
     * @return true if the torch is on
     * @throws RuntimeException if there is a camera error
     */
    public boolean isTorchOn() {
        Camera.Parameters parameters = camera.getParameters();
        if (parameters != null) {
            String flashMode = parameters.getFlashMode();
            return flashMode != null && (Camera.Parameters.FLASH_MODE_ON.equals(flashMode)
                    || Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
        } else {
            return false;
        }
    }

    /**
     * Returns the Camera. This returns null if the camera is not opened yet, failed to open, or has
     * been closed.
     *
     * @return the Camera
     */
    public Camera getCamera() {
        return camera;
    }

    private MediaRecorder mediaRecorder;

    @SuppressLint("NewApi")
    public void startRecord(File file, int maxDuration) {
        if (mediaRecorder == null) {
            try {
                mediaRecorder = new MediaRecorder();
                List<Size> videoSizes = getPreviewSizes(camera.getParameters(), false);
                Size size = null;
                for (int i = 0; i < videoSizes.size(); i++) {
                    size = videoSizes.get(i);
                    if (size.width <= 1280 && size.height <= 720) {
                        break;
                    }
                }
                if (size == null) {
                    size = displayConfiguration.getBestPreviewSize(videoSizes, isCameraRotated());
                }
                camera.unlock();
                mediaRecorder.setCamera(camera);

                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                mediaRecorder.setOrientationHint(cameraInfo.orientation);
                CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
                if (size != null) {
                    camcorderProfile.videoFrameWidth = size.width;
                    camcorderProfile.videoFrameHeight = size.height;
                    mediaRecorder.setProfile(camcorderProfile);
                }

                mediaRecorder.setMaxDuration(maxDuration * 1000);
                mediaRecorder.setOutputFile(file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to init mediaRecorder", e);
            }
        }

        try {
            mediaRecorder.prepare();
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            stopRecord();
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            stopRecord();
        }

        try {
            mediaRecorder.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start mediaRecorder", e);
        }
    }

    public void stopRecord() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException stopException) {
                Log.e(TAG, "Failed to stop mediaRecorder");
            }
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            camera.lock();
        }
    }
}
