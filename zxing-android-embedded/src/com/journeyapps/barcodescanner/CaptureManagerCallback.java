package com.journeyapps.barcodescanner;

import android.support.annotation.NonNull;

public interface CaptureManagerCallback {

    void onScanResult(@NonNull  BarcodeResult result);

    void onPreview(@NonNull SourceData sourceData);
}
