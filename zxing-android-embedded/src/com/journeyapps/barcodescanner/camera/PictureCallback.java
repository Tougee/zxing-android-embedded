package com.journeyapps.barcodescanner.camera;

import com.journeyapps.barcodescanner.SourceData;

public interface PictureCallback {
    void onPicture(SourceData data);

    void onPictureError(Exception e);
}
