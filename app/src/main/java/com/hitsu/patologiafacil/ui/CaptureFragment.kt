package com.hitsu.patologiafacil.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import com.hitsu.patologiafacil.BuildConfig
import com.hitsu.patologiafacil.R
import com.hitsu.patologiafacil.databinding.FragmentCaptureBinding
import java.io.File

class CaptureFragment : Fragment() {

    private var _binding: FragmentCaptureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    private var pendingPhotoUri: Uri? = null

    // PermissÃ£o CAMERA
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
    }

    // FOTO EM ALTA com FileProvider (sem MediaStore)
    private val takeFullPicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingPhotoUri
            pendingPhotoUri = null
            if (success && uri != null) {
                showSelectedUri(uri)
                setImagePathCompat(uri.toString())
            } else {
                showPlaceholder()
            }
        }

    // Preview opcional (mantido)
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                showSelectedBitmap(bitmap)
                // Se quiser persistir o preview como arquivo no MediaStore:
                saveBitmapToMediaStore(bitmap)?.let { uri ->
                    setImagePathCompat(uri.toString())
                }
            } else showPlaceholder()
        }

    // Galeria com OpenDocument + permissÃ£o persistente
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                showSelectedUri(uri)
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                setImagePathCompat(uri.toString())
            } else showPlaceholder()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val paddingVertical = resources.getDimensionPixelSize(com.hitsu.patologiafacil.R.dimen.space_12)
        listOf(binding.inputLocal, binding.inputTempo).forEach { editText ->
            editText.setPadding(
                editText.paddingLeft,
                paddingVertical,
                editText.paddingRight,
                paddingVertical
            )
            editText.setIncludeFontPadding(true)
            editText.typeface = Typeface.SANS_SERIF
        }

        binding.btnTakePhoto.setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        binding.btnPickGallery.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }

        binding.btnAnalyze.setOnClickListener {
            val path = viewModel.imagePath.value
            if (path.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.no_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val localText = binding.inputLocal.text?.toString()
            val tempoText = binding.inputTempo.text?.toString()
            viewModel.runAnalysis(requireContext().contentResolver, localText, tempoText)
            findNavController().navigate(R.id.diagnosisFragment)
        }

        showPlaceholder()
    }

    /** Abre cÃ¢mera com URI de FileProvider (seguro e com grants automÃ¡ticos) */
    private fun launchCamera() {
        val uri = createFilePhotoUri() ?: run { showPlaceholder(); return }
        pendingPhotoUri = uri
        takeFullPicture.launch(uri)
    }

    private fun showPlaceholder() {
        binding.imageSlot.isVisible = true
        binding.placeholderImage.apply {
            setImageResource(R.drawable.capture_placeholder)
            alpha = 0.25f
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isVisible = true
            contentDescription = "Nenhuma imagem selecionada"
        }
        binding.emptyState.isVisible = true
        binding.previewView.isVisible = false
    }

    private fun showSelectedBitmap(bitmap: Bitmap) {
        binding.imageSlot.isVisible = true
        binding.placeholderImage.apply {
            setImageBitmap(bitmap)
            alpha = 1f
            scaleType = ImageView.ScaleType.CENTER_CROP
            isVisible = true
            contentDescription = "PrÃ©-visualizaÃ§Ã£o da imagem capturada"
        }
        binding.emptyState.isVisible = false
        binding.previewView.isVisible = false
    }

    private fun showSelectedUri(uri: Uri) {
        binding.imageSlot.isVisible = true
        binding.placeholderImage.apply {
            setImageURI(uri)
            alpha = 1f
            scaleType = ImageView.ScaleType.CENTER_CROP
            isVisible = true
            contentDescription = "PrÃ©-visualizaÃ§Ã£o da imagem selecionada"
        }
        binding.emptyState.isVisible = false
        binding.previewView.isVisible = false
    }

    /**
     * Atualiza diretamente o imagePath do ViewModel sem reflexão.
     * Evita quebra em release quando o R8/ProGuard ofusca nomes.
     */
    private fun setImagePathCompat(value: String) {
        viewModel.setSelectedImage(value)
    }

    /** Cria URI via FileProvider em cacheDir/images (sem MediaStore) */
    private fun createFilePhotoUri(): Uri? {
        val dir = File(requireContext().cacheDir, "images")
        if (!dir.exists()) dir.mkdirs()
        val file = File.createTempFile("PF_${System.currentTimeMillis()}_", ".jpg", dir)
        val authority = requireContext().packageName + ".provider"
        return FileProvider.getUriForFile(requireContext(), authority, file)
    }

    /** Apenas para o modo preview -> persistir bitmap no MediaStore (opcional) */
    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri? {
        val resolver = requireContext().contentResolver
        val fileName = "PF_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PatologiaFacil")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            return null
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                try { resolver.update(uri, cv, null, null) } catch (_: Exception) {}
            }
        }
        return uri
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        pendingPhotoUri = null
    }
}


