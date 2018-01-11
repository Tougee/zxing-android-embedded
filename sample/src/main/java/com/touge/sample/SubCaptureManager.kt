package one.mixin.android.ui.qr

import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.touge.sample.MainActivity

class SubCaptureManager(val activity: MainActivity, view: DecoratedBarcodeView) : CaptureManager(activity, view) {

    override fun returnResult(rawResult: BarcodeResult) {
    }

    fun resume() {
        decode()
        onResume()
    }

    fun closeAndFinishInternal() {
        closeAndFinish()
    }
}