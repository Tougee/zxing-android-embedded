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
import kotlinx.android.synthetic.main.fragment_edit.*
import java.io.ByteArrayOutputStream


class EditFragment : Fragment() {

    companion object {
        val TAG = "EditFragment"

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
        val out = ByteArrayOutputStream()
        val yuvImage = YuvImage(sourceData.data, ImageFormat.NV21, sourceData.dataWidth, sourceData.dataHeight, null)
        yuvImage.compressToJpeg(Rect(0, 0, sourceData.dataWidth, sourceData.dataHeight), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val matrix = Matrix().apply { postRotate(sourceData.rotation.toFloat()) }
        val result = Bitmap.createBitmap(bitmap, 0, 0, sourceData.dataWidth, sourceData.dataHeight, matrix, true)
        preview_iv.setImageBitmap(result)
    }

    interface Callback {
        fun getSourceData(): SourceData
    }
}