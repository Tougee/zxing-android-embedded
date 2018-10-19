package one.mixin.android.ui.qr

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.journeyapps.barcodescanner.SourceData
import com.touge.sample.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_edit.*
import java.io.ByteArrayOutputStream

class EditFragment : Fragment() {

    companion object {
        const val TAG = "EditFragment"

        fun newInstance() = EditFragment()
    }

    private lateinit var callback: Callback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is Callback) {
            throw IllegalArgumentException("")
        }
        callback = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sourceData = callback.getSourceData()
        val imageBytes = sourceData.data
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        val w = options.outWidth
        val h = options.outHeight
        val ratioW = sourceData.dataWidth.toFloat() / w
        val ratioH = sourceData.dataWidth.toFloat() / h
        val ratio: Float = if (ratioW > ratioH) {
            ratioH
        } else {
            ratioW
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val matrix = Matrix().apply {
            postRotate(sourceData.rotation.toFloat())
            postScale(ratio, ratio)
        }
        val result = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
        preview_iv.setImageBitmap(result)
    }

    override fun onPause() {
        super.onPause()
        callback.resume()
    }

    interface Callback {
        fun getSourceData(): SourceData
        fun resume()
        fun getData(): ByteArray
    }
}