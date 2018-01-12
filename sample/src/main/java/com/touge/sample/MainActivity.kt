package com.touge.sample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.CaptureManagerCallback
import com.journeyapps.barcodescanner.SourceData
import kotlinx.android.synthetic.main.activity_main.*
import one.mixin.android.ui.qr.EditFragment

class MainActivity : AppCompatActivity(), EditFragment.Callback {

    private enum class Mode {
        SCAN,
        CAPTURE,
        RECORD
    }

    private var mode = Mode.SCAN
    private var sourceData: SourceData? = null

    private val mCaptureManager: CaptureManager by lazy {
        CaptureManager(this, zxing_barcode_scanner, captureCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mCaptureManager.initializeFromIntent(intent, savedInstanceState)
        mCaptureManager.decode()
        close.setOnClickListener { finish() }
        switch_camera.setOnClickListener { zxing_barcode_scanner.switchCamera() }
        op.setCameraOpCallback(object : CameraOpView.CameraOpCallback {
            override fun onClick() {
                mCaptureManager.capture()
                mode = Mode.CAPTURE
            }

            override fun onProgressStart() {
                this@MainActivity.vibrate(longArrayOf(0, 30))
                mCaptureManager.record()
                mode = Mode.RECORD
            }

            override fun onProgressStop() {
                mCaptureManager.pause()
                mode = Mode.SCAN
            }

        })
    }

    override fun onResume() {
        super.onResume()
        mCaptureManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        mCaptureManager.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mCaptureManager.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        mCaptureManager.onSaveInstanceState(outState!!)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        mCaptureManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
            zxing_barcode_scanner!!.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun getSourceData(): SourceData {
        return sourceData!!
    }

    override fun resume() {
        mCaptureManager.resume()
    }

    private val captureCallback = object : CaptureManagerCallback {
        override fun onScanResult(result: BarcodeResult) {
        }

        override fun onPreview(sourceData: SourceData) {
            if (mode == Mode.CAPTURE) {
                this@MainActivity.sourceData = sourceData
                this@MainActivity.supportFragmentManager.beginTransaction()
                        .add(R.id.container, EditFragment.newInstance(), EditFragment.TAG)
                        .addToBackStack(null)
                        .commit()
            } else if (mode == Mode.RECORD) {
            }
        }

    }
}
