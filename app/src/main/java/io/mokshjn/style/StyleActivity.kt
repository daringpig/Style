package io.mokshjn.style

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.ImageView
import android.widget.SeekBar
import butterknife.bindView
import io.mokshjn.style.adapter.StylesAdapter
import io.mokshjn.style.models.Style
import org.jetbrains.anko.*
import org.tensorflow.contrib.android.TensorFlowInferenceInterface

class StyleActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {

    val imageView: ImageView by bindView(R.id.styledImage)
    val seekbar: SeekBar by bindView(R.id.styleSeek)
    val recyclerView: RecyclerView by bindView(R.id.styles)

    val NUM_STYLES = 26
    val styles = ArrayList<Style>()

    private val MODEL_FILE = "file:///android_asset/stylize_quantized.pb"
    private val INPUT_NODE = "input"
    private val STYLE_NODE = "style_num"
    private val OUTPUT_NODE = "transformer/expand/conv3/conv/Sigmoid"
    var inferenceInterface: TensorFlowInferenceInterface? = null

    var ogImage: ByteArray? = null
    var selectedStyle: Int = -1
    var ogImageBmp: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style)

        loadTF()
        processInput()
        loadStyles()
        loadViews()
    }

    fun loadTF() {
        inferenceInterface = TensorFlowInferenceInterface(assets, MODEL_FILE)
    }

    fun processInput() {
        ogImage = intent?.extras?.getByteArray("image")
        val bmp: Bitmap = BitmapFactory.decodeByteArray(ogImage, 0, ogImage!!.size)
        ogImageBmp = bmp
        imageView.setImageBitmap(bmp)
    }

    fun loadViews() {
        val adapter = StylesAdapter(styles) { i: Int ->
            selectedStyle = i
            val dialog = indeterminateProgressDialog(message = "Performing Style Transfer...", title = "Processing")
            doAsync {
                val bmp = stylizeImage(i, seekbar.progress)
                uiThread {
                    setBitmap(bmp)
                    dialog.dismiss()
                }
            }

        }
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter
        seekbar.setOnSeekBarChangeListener(this)
    }

    fun loadStyles() {
        val path = "thumbnails/style"
        for (i in 0..NUM_STYLES-1) {
            val inputstr = assets.open(path+i+".jpg")
            val bmp = BitmapFactory.decodeStream(inputstr)
            styles.add(Style("Style"+i, bmp))
        }
    }

    fun setBitmap(bmp: Bitmap) {
        imageView.setImageBitmap(bmp)
    }

    fun stylizeImage(styleIndex: Int, strength: Int): Bitmap {
        val styleVals = FloatArray(NUM_STYLES, {0f})
        styleVals[styleIndex] = (strength / 100f)

        val intValues = IntArray(720 * 720)
        val floatValues = FloatArray(720 * 720 * 3)

        val croppedBmp = Bitmap.createScaledBitmap(ogImageBmp, 720, 720, false)
        croppedBmp.getPixels(intValues, 0, croppedBmp.width, 0, 0, croppedBmp.width, croppedBmp.height)

        for (i in 0..intValues.size-1) {
            val int = intValues[i]
            floatValues[i * 3] = ((int shr 16) and 0xFF) / 255.0f
            floatValues[i*3 + 1] = ((int shr 8) and 0xFF) / 255.0f
            floatValues[i*3 + 2] = (int and 0xFF) / 255.0f
        }

        inferenceInterface?.feed(INPUT_NODE, floatValues, 1 , 720, 720, 3)
        inferenceInterface?.feed(STYLE_NODE, styleVals, NUM_STYLES.toLong())

        inferenceInterface?.run(arrayOf(OUTPUT_NODE), false)

        inferenceInterface?.fetch(OUTPUT_NODE, floatValues)

        for (i in 0..intValues.size-1) {
            intValues[i] = 0xFF000000.toInt() or ((floatValues[i * 3] * 255).toInt() shl 16) or ((floatValues[i * 3 + 1] * 255).toInt() shl 8) or (floatValues[i * 3 + 2] * 255).toInt()
        }

        croppedBmp.setPixels(intValues, 0, 720, 0, 0, 720, 720)

        return croppedBmp
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        val dialog = indeterminateProgressDialog(message = "Performing Style Transfer...", title = "Processing")
        doAsync {
            val bmp = stylizeImage(selectedStyle, progress)
            uiThread {
                setBitmap(bmp)
                dialog.dismiss()
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}
