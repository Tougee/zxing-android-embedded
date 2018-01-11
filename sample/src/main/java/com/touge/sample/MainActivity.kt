package com.touge.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import kotlinx.android.synthetic.main.activity_main.*
import one.mixin.android.ui.qr.SubCaptureManager
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {

    private val mCaptureManager: SubCaptureManager by lazy {
        SubCaptureManager(this, zxing_barcode_scanner)
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
//                toast("On click ")
            }

            override fun onProgressStart() {
                this@MainActivity.vibrate(longArrayOf(0, 30))
            }

            override fun onProgressStop() {
//                toast("On Progress stop")
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
}
