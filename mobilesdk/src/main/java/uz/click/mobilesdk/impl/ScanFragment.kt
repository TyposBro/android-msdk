package uz.click.mobilesdk.impl

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import uz.click.mobilesdk.R
import uz.click.mobilesdk.core.errors.ArgumentEmptyException
import uz.click.mobilesdk.databinding.FragmentScanBinding
import uz.click.mobilesdk.impl.MainDialogFragment.Companion.THEME_MODE
import uz.click.mobilesdk.impl.paymentoptions.ThemeOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

class ScanFragment : AppCompatDialogFragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var themeMode: ThemeOptions

    companion object {
        const val TAG = "DETECTOR"
        const val REQUEST_CAMERA = 10001
        const val REGEX_FOR_CARD_VALIDATION = "[0-9bs]{4} [0-9bs]{4} [0-9bs]{4} [0-9bs]{4}"
        const val REGEX_EXPIRED_DATE = "[0-9bsS]{2}/[0-9bs]{2}"
    }

    var number = ""
    var date = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments == null) throw ArgumentEmptyException()

        themeMode = requireArguments().getSerializable(THEME_MODE) as ThemeOptions
        when (themeMode) {
            ThemeOptions.LIGHT -> {
                setStyle(STYLE_NO_FRAME, R.style.cl_FullscreenDialogTheme)
            }
            ThemeOptions.NIGHT -> {
                setStyle(STYLE_NO_FRAME, R.style.cl_FullscreenDialogThemeDark)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            recognizer.process(image)
                                .addOnSuccessListener { visionText ->
                                    for (block in visionText.textBlocks) {
                                        val patternCardNumber = Pattern.compile(REGEX_FOR_CARD_VALIDATION)
                                        val matcherCardNumber = patternCardNumber.matcher(block.text)
                                        while (matcherCardNumber.find()) {
                                            number = matcherCardNumber.group().replace('b', '6').replace('s', '5')
                                        }

                                        val patternCardExpiredDate = Pattern.compile(REGEX_EXPIRED_DATE)
                                        val matcherCardExpired = patternCardExpiredDate.matcher(block.text)
                                        while (matcherCardExpired.find()) {
                                            date = matcherCardExpired.group().replace('b', '6').replace('s', '5')
                                        }
                                    }
                                    if (number.isNotEmpty() && date.isNotEmpty()) {
                                        parentFragment?.let {
                                            val parent = parentFragment as MainDialogFragment
                                            parent.setScannedData(number, date)
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Text recognition failed", e)
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}