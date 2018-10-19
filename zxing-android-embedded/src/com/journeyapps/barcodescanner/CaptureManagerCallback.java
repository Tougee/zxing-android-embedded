package com.journeyapps.barcodescanner;

import android.support.annotation.NonNull;

public interface CaptureManagerCallback {

    void onScanResult(@NonNull  BarcodeResult result);

    void onPicture(@NonNull SourceData sourceData);
}
