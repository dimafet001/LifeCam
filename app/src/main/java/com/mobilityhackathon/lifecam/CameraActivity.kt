package com.mobilityhackathon.lifecam

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import android.graphics.Matrix
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraX
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.TimeUnit
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageAnalysisConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.*

import com.ibm.watson.developer_cloud.service.security.IamOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifiedImages
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyOptions
import java.io.*

// This is an arbitrary number we are using to keep tab of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)


class CameraActivity : AppCompatActivity(), LifecycleOwner {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Add this at the end of onCreate function

        viewFinder = findViewById(R.id.view_finder)

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    // Add this after onCreate

    private lateinit var viewFinder: TextureView

    private fun startCamera() {
        // TODO: Implement CameraX operations
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(1, 1))
            setTargetResolution(Size(640, 640))
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.

        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val config = ImageAnalysisConfig.Builder()
                .setTargetResolution(Size(1280, 720))
                .setTargetAspectRatio(Rational(1280, 720))
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .build()

        var imageAnalysis = ImageAnalysis(config)

        var staller = 0
        var imageId = 0

        imageAnalysis.analyzer = Analyzer { image, rotationDegrees ->
            // insert your code here.

            val bitmap = viewFinder.getBitmap();

            staller++
            if (staller == 10) {
                staller = 0
            } else return@Analyzer
//
            if(bitmap==null)
                return@Analyzer

            // send bitmap

            val size     = bitmap.getRowBytes() * bitmap.getHeight();
            val b = ByteBuffer.allocate(size);

            bitmap.copyPixelsToBuffer(b);

            val bytes = ByteArray(size);

            try {
                b.get(bytes, 0, bytes.size);
            } catch (e: BufferUnderflowException) {
            // always happens
            }
            // do something with byte[]

//            uploadImage(bytes)

            val options = IamOptions.Builder()
                .apiKey("SuVIvim1EWxK13KpeTO4pgDP4-4vU0WIYuvDu9Iti6Rz")
                .build();

            val service = VisualRecognition("2018-03-19", options);
//            var fileName =Environment.getExternalStorageDirectory().getAbsolutePath() +
//                    "/LifeCam" + "/camera"+ imageId
//            try {
//
//                FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() +
//                        "/LifeCam" + "/camera"+ imageId).use({ out ->
//                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
//                    // PNG is a lossless format, the compression factor (100) is ignored
//                })
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }

            bitmapToFile(bitmap, "camera" + imageId)
            var fileName = "/data/user/0/com.mobilityhackathon.lifecam/files/"+ "camera" + imageId + ".png"

            imageId++

//            Log.d("mytag", "what" + fileName)


            val imagesStream = FileInputStream(fileName);
            val classifyOptions = ClassifyOptions.Builder()
                .imagesFile(imagesStream)
                .imagesFilename(fileName)
                .threshold(0.5f)
                .owners(Arrays.asList("me"))
                .build();

            val thread = Thread(Runnable {
                try {


                    val result = service.classify(classifyOptions).execute();
                    Log.d("mytag", result.toString())


                } catch (e: Exception) {
                    e.printStackTrace()
                }
            })

            thread.start()


        }

        CameraX.bindToLifecycle(this as LifecycleOwner, imageAnalysis, preview, imageAnalysis)

    }

    private fun updateTransform() {
        // TODO: Implement camera viewfinder transformations
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    fun bitmapToFile(bmp: Bitmap, name: String) : File?
    {
        try
        {
            var size : Int = bmp.height * bmp.width
            var bos = ByteArrayOutputStream(size);
            bmp.compress(Bitmap.CompressFormat.PNG, 80, bos);
            var bArr = bos.toByteArray();
            bos.flush();
            bos.close();

            var fos = openFileOutput(name+".png", Context.MODE_PRIVATE);
            fos.write(bArr);
            fos.flush();
            fos.close();

            var mFile= File(getFilesDir().getAbsolutePath(), name+".png");
            Log.d("mytag", mFile.absolutePath)
            return mFile;
        }
        catch (e: FileNotFoundException)
        {
            e.printStackTrace();
            return null;
        }
        catch (e: IOException)
        {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun uploadImage(imageBytes: ByteArray) {
        ServiceApi.Factory.getInstance(this)?.uploadImage(imageBytes)?.enqueue(object : Callback<String> {
            override fun onFailure(call: Call<String>?, t: Throwable?) {
                Toast.makeText(getApplicationContext(),"Image uploaded",Toast.LENGTH_SHORT).show()
                // Image uploaded successfully
            }

            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                // Error Occurred during uploading
            }
        })

    }



    interface ServiceApi {
        companion object {
            private const val BASE_URL = ""  // Base url of your hosting
        }
        @FormUrlEncoded
        @POST("")   // end_point url
        fun uploadImage(@Field("image_bytes") imageBytes: ByteArray): Call<String>
        class Factory {
            companion object {
                private var service: ServiceApi? = null
                fun getInstance(context: Context): ServiceApi? {
                    if (service == null) {
                        val builder = OkHttpClient().newBuilder()
                        builder.readTimeout(15, TimeUnit.SECONDS)
                        builder.connectTimeout(15, TimeUnit.SECONDS)
                        builder.writeTimeout(15, TimeUnit.SECONDS)
                        if (BuildConfig.DEBUG) {
                            val interceptor = HttpLoggingInterceptor()
                            interceptor.level = HttpLoggingInterceptor.Level.BODY
                            builder.addInterceptor(interceptor)
                        }
                        val file = File(context.cacheDir, "cache_dir")
                        if (!file.exists())
                            file.mkdirs()
                        val cacheSize: Long = 10 * 1024 * 1024 // 10 MiB
                        val cache = okhttp3.Cache(file, cacheSize)
                        builder.cache(cache)
                        val retrofit: Retrofit
                        retrofit = Retrofit.Builder()
                                .client(builder.build())
                                .addConverterFactory(GsonConverterFactory.create())
                                .baseUrl(BASE_URL)
                                .build()
                        service = retrofit.create(ServiceApi::class.java)
                        return service
                    } else {
                        return service
                    }
                }
            }
        }
    }

}