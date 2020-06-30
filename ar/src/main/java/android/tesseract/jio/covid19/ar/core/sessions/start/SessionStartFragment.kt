package android.tesseract.jio.covid19.ar.core.sessions.start

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.tesseract.jio.covid19.ar.ARActivity
import android.tesseract.jio.covid19.ar.R
import android.tesseract.jio.covid19.ar.databinding.FragmentStartSessionBinding
import android.tesseract.jio.covid19.ar.networkcalling.model.RankResult
import android.tesseract.jio.covid19.ar.networkcalling.model.SessionInfo
import android.tesseract.jio.covid19.ar.tflite.Classifier
import android.tesseract.jio.covid19.ar.tflite.TFLiteObjectDetectionAPIModel
import android.tesseract.jio.covid19.ar.utils.*
import android.tesseract.jio.covid19.ar.utils.PrefsConstants.USER_SOUND_ON
import android.tesseract.jio.covid19.ar.utils.PrefsConstants.VIOLATION_SOUND_EFFECT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_ar.*
import kotlinx.android.synthetic.main.layout_bottom_action_buttons.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*


/**
 * Created by Dipanshu Harbola on 6/6/20.
 */
class SessionStartFragment : Fragment(), SessionStartViewModel.Navigator {

    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        FragmentStartSessionBinding.inflate(
            layoutInflater
        )
    }

    lateinit var startSessionViewModel: SessionStartViewModel

    private val modelColored = "https://jio-ar-dev.s3.ap-south-1.amazonaws.com/app-assets/colored.glb"
    private val modelRed = "https://jio-ar-dev.s3.ap-south-1.amazonaws.com/app-assets/colored.glb"

    // coroutine scope for background/main thread operations
    private val bgScope = CoroutineScope(Dispatchers.IO)
    private val uiRenderingScope = CoroutineScope(Dispatchers.Main)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private var currentTransformableNode: TransformableNode? = null

    private lateinit var arFragment: JioARFragment

    private lateinit var renderableColored: ModelRenderable
    private lateinit var renderableRed: ModelRenderable
    private var renderable: ModelRenderable? = null

    // Camera world position points
    private val mCameraRelativePose = Pose.makeTranslation(0.0f, -0.36f, -0.55f)

    private var oldAnchor: Anchor? = null

    private var oldAnchorNode: AnchorNode? = null

    // AR session
    private var session: Session? = null

    private var sceneView: SceneView? = null

    // classifier to recognise object
    private var detector: Classifier? = null

    private var readyToProcessFrame = false
    private lateinit var currentFrameBmp: Bitmap
    private var computingDetection = false
    private var timestamp: Long = 0

    // Handler to detect violation
    private val violationHandler: Handler = Handler()
    private var isDetectingViolation = false
    private var sessionViolationCount = 0
    private var currentViolation = 0
    private val violationThreshold = 2

    // Safety
    private var currentSafety = 100

    // Session Time
    private var sessionStartTime = 0L
    private var sessionEndTime = 0L

    // Leaderboard adapter
    val lbAdapter = LeaderBoardAdapter()
    var isExpended = false

    // Media player
    var mdPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        requireActivity().window.navigationBarColor =
            requireActivity().getColor(R.color.baseBgColor)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard(requireActivity())
        placeObjectColored()
        placeObjectRed()
        initComponents()
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.getPrefsInt(VIOLATION_SOUND_EFFECT) == 0) {
            Prefs.setPrefs(VIOLATION_SOUND_EFFECT, R.raw.space_drop)
        }
        mdPlayer = MediaPlayer.create(requireContext(), Prefs.getPrefsInt(VIOLATION_SOUND_EFFECT))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sceneView = null
        detector?.close()
        arFragment.onDetach()
        bgScope.cancel()
        uiRenderingScope.cancel()
        uiScope.cancel()
    }

    override fun globalLeaderBoardList(result: MutableList<RankResult>) {
        binding.layoutLeaderBoard.rvLeadboard.adapter = lbAdapter
        lbAdapter.setData(result)
    }

    override fun navigateToEndSession(sessionInfo: SessionInfo) {
        val action = SessionStartFragmentDirections.actionSessionStartFragmentToSessionEndFragment(
            sessionInfo
        )
        findNavController().navigate(action)
    }

    override fun showLoading(isLoading: Boolean) {
        if (isLoading)
            ProgressLoader.showLoader(requireContext())
        else
            ProgressLoader.hideLoader()
    }

    override fun showError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun initComponents() {

        startSessionViewModel = ViewModelProvider(this).get(SessionStartViewModel::class.java)
        startSessionViewModel.navigator = this

        startSessionViewModel.getMyGlobalRank()

        arFragment = childFragmentManager.findFragmentById(R.id.sceneformFragment) as JioARFragment

        handleActionButtons()
        setLeaderBoardExpendButtonListener()

        // used to remove the plan detection view
        arFragment.planeDiscoveryController.hide()
        arFragment.arSceneView.planeRenderer.isVisible = false
        arFragment.planeDiscoveryController.setInstructionView(null)

        // Adds a listener to the ARSceneView
        // Called before processing each frame
        arFragment.arSceneView.scene.addOnUpdateListener {
            arFragment.onUpdate(it)
            onSceneUpdate()
            renderable?.let {
                uiRenderingScope.launch { addNodeToScene() } }
        }

        mdPlayer?.setOnCompletionListener {
            mdPlayer?.release()
        }

        binding.btnStartSession.setOnClickListener {
            sceneView = arFragment.arSceneView as ArSceneView
            session = arFragment.arSceneView.session
            setupCamConfig(session)
            showStartSessionView()
            readyToProcessFrame = true

            sessionStartTime = System.currentTimeMillis()
        }

        binding.layoutSessionInfo.btnEndSession.setOnClickListener {
            clearAnchor()
        }
    }

    private fun handleActionButtons() {
        (requireContext() as ARActivity).setupActionButtons()

        try {
            (requireContext() as ARActivity).layoutActionButtons.fabJourneyStats.setOnClickListener {
                val action =
                    SessionStartFragmentDirections.actionSessionStartFragmentToMyJournalFragment()
                findNavController().navigate(action)
            }

            (requireContext() as ARActivity).layoutActionButtons.fabSettings.setOnClickListener {
                val action =
                    SessionStartFragmentDirections.actionSessionStartFragmentToMyPreferencesFragment()
                findNavController().navigate(action)
            }

            (requireContext() as ARActivity).layoutActionButtons.fabStartSession.setOnClickListener {
                return@setOnClickListener
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    // SetUp camera configurations
    private fun setupCamConfig(session: Session?) {
        // Create a camera config filter for the session.
        val filter = CameraConfigFilter(session)

        // Return only camera configs that target 30 fps camera capture frame rate.
        filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))

        // Return only camera configs that will not use the depth sensor.
        filter.setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))

        // Get list of configs that match filter settings.
        // In this case, this list is guaranteed to contain at least one element,
        // because both TargetFps.TARGET_FPS_30 and DepthSensorUsage.DO_NOT_USE
        // are supported on all ARCore supported devices.
        val cameraConfigList = session!!.getSupportedCameraConfigs(filter)

        // Use element 0 from the list of returned camera configs. This is because
        // it contains the camera config that best matches the specified filter settings.
        session.cameraConfig = cameraConfigList[0]
    }

    /**
     * @placeObject() is responsible to render the initial 3D object.
     */
    private fun placeObjectColored() {
        ModelRenderable.builder()
            .setSource(requireContext(), RenderableSource.builder().setSource(
                requireContext(), Uri.parse(modelColored),
                RenderableSource.SourceType.GLB).build())
            .setRegistryId(modelColored)
            .build()
            .thenAccept {
                renderableColored = it.apply {
                    isShadowReceiver = false
                    isShadowCaster = false
                }
                renderable = renderableColored
            }
            .exceptionally {
                Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show()
                return@exceptionally null
            }
    }

    /**
     * @placeObject() is responsible to render the initial 3D object.
     */
    private fun placeObjectRed() {
        ModelRenderable.builder()
            .setSource(requireContext(), RenderableSource.builder().setSource(
                requireContext(), Uri.parse(modelColored),
                RenderableSource.SourceType.GLB).build())
            .setRegistryId(modelColored)
            .build()
            .thenAccept {
                renderableRed = it.apply {
                    isShadowReceiver = false
                    isShadowCaster = false
                }
            }
            .exceptionally {
                Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show()
                return@exceptionally null
            }
    }

    /**
     * @addNodeToScene() as name suggest added the rendered 3D object node to the plane
     */
    private fun addNodeToScene() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val pose =
            frame.camera?.displayOrientedPose?.compose(mCameraRelativePose) // need to change it
        if (frame.camera?.trackingState == TrackingState.TRACKING) {
            val anchor = arFragment.arSceneView.session?.createAnchor(pose)
            anchor?.pose?.toMatrix(FloatArray(32), 0)
            // checking and removing the old anchor so that we always have the latest anchor
            // point and anchor node, to render the object.
            if (oldAnchor != null) {
                oldAnchor?.detach()
            }
            oldAnchor = anchor
            val anchorNode = AnchorNode(anchor)
            oldAnchorNode = anchorNode
            anchorNode.setParent(sceneView?.scene)
            // TransformableNode means the user to move, scale and rotate the model
            val transformableNode = TransformableNode(arFragment.transformationSystem)
            transformableNode.renderable = renderable
            transformableNode.setParent(anchorNode)
            transformableNode.select()
            currentTransformableNode = transformableNode
        }
    }

    // Simple function to show/hide our start-session
    private fun showStartSessionView() {
        binding.run {
            bottomView.visibility = View.GONE
            btnStartSession.visibility = View.GONE
            layoutLeaderBoard.clLeaderBoard.visibility = View.GONE
            layoutLeaderBoard.fabLeadBoardDownSlide.visibility = View.GONE
            (requireContext() as ARActivity).findViewById<ConstraintLayout>(R.id.layoutActionButtons).visibility =
                View.GONE
            layoutSessionInfo.llSessionInfo.visibility = View.VISIBLE
            layoutSessionInfo.btnEndSession.visibility = View.VISIBLE
        }
        onPreviewSizeSelect()
        initSessionInfo()
    }

    private fun setLeaderBoardExpendButtonListener() {
        binding.layoutLeaderBoard.fabLeadBoardDownSlide.setOnClickListener {
            if (!isExpended) {
                isExpended = true
                slideView(
                    binding.layoutLeaderBoard.clLeaderBoard,
                    binding.layoutLeaderBoard.clLeaderBoard.height,
                    resources.getDimension(R.dimen._450DP).toInt()
                )
                slideView(binding.bottomView, binding.bottomView.height, 1)
                binding.btnStartSession.animate()
                    .translationYBy(0f)
                    .translationY(resources.getDimension(R.dimen._75DP))
                    .alpha(0.0f)
                    .setDuration(300)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            binding.btnStartSession.visibility = View.GONE
                        }
                    })
                (requireContext() as ARActivity).findViewById<ConstraintLayout>(R.id.layoutActionButtons)
                    .animate()
                    .translationYBy(0f)
                    .translationY(resources.getDimension(R.dimen._75DP))
                    .alpha(0.0f)
                    .setDuration(200)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            binding.btnStartSession.visibility = View.GONE
                        }
                    })
                binding.layoutLeaderBoard.fabLeadBoardDownSlide.setImageResource(R.drawable.ic_up_arrow_small)
            } else {
                isExpended = false
                slideView(
                    binding.layoutLeaderBoard.clLeaderBoard,
                    binding.layoutLeaderBoard.clLeaderBoard.height,
                    resources.getDimension(R.dimen._201DP).toInt()
                )
                slideView(
                    binding.bottomView,
                    binding.bottomView.height,
                    resources.getDimension(R.dimen._95DP).toInt()
                )
                binding.btnStartSession.visibility = View.VISIBLE
                binding.btnStartSession.animate()
                    .translationYBy(resources.getDimension(R.dimen._75DP))
                    .translationY(0f)
                    .alpha(1.0f)
                    .setDuration(400)
                    .setListener(null)
                (requireContext() as ARActivity).findViewById<ConstraintLayout>(R.id.layoutActionButtons)
                    .animate()
                    .translationYBy(resources.getDimension(R.dimen._75DP))
                    .translationY(0f)
                    .alpha(1.0f)
                    .setDuration(300)
                    .setListener(null)
                binding.layoutLeaderBoard.fabLeadBoardDownSlide.setImageResource(R.drawable.ic_down_arrow_small)
            }
        }
    }

    private fun initSessionInfo() {
        binding.layoutSessionInfo.run {
            safetyPercent = "100%"
            violationCount = "0 violation"
            sessionTime = "0 m 0 s"
        }
        startSessionTimer()
    }

    /**
     * @onSceneUpdate() Function invoke every time when scene update
     */
    var imgTimestamp = 0L
    private fun onSceneUpdate() {
        if (session == null) {
            return
        }
        try {
            val frame: Frame = arFragment.arSceneView.arFrame ?: return
            val image = frame.acquireCameraImage()

            // Copy the camera stream to a bitmap
            try {
                if (image == null) return
                if (image.timestamp > imgTimestamp)
                    imgTimestamp = image.timestamp
                else {
                    image.close()
                    return
                }
                if (image.planes == null) {
                    image.close()
                    return
                }
                if ((image.planes[0].buffer == null) || (image.planes[1].buffer == null) || (image.planes[2].buffer == null)) {
                    image.close()
                    return
                }
                val bytes = ImageUtils.NV21ToByteArray(
                    ImageUtils.YUV420_888ToNV21(image),
                    image.width, image.height
                )
                val bitmap = bytes?.size?.let { BitmapFactory.decodeByteArray(bytes, 0, it).rotate(90f) }
                if (bitmap != null) {
                    currentFrameBmp = bitmap
                    executeKernelTask()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                image?.close()
            }
            image?.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @onPreviewSizeSelect() Function invoke every time when when we get a frame from ar-camera
     * to setup the default preview size for detection
     */
    private fun onPreviewSizeSelect() {
        try {
            detector = TFLiteObjectDetectionAPIModel.create(
                requireActivity().assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED
            )
        } catch (e: IOException) {
            e.printStackTrace()
            val toast = Toast.makeText(
                requireContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            requireActivity().finish()
        }
        // width = 300, height = 300 cause SSD model support 300 x300 image to detect object
        binding.graphicOverlay.setConfiguration(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE)
    }

    /**
     * @executeKernelTask() Function is responsible to execute the detection process a short delay,
     * so that our kernel accept new image process request without any inference.
     */
    private fun executeKernelTask() {
        val handlerKernel = Handler()
        val r: Runnable = object : Runnable {
            override fun run() {
                handlerKernel.postDelayed(
                    this,
                    100L
                ) //executing object detection in every 100 millisecond
                if (readyToProcessFrame && !computingDetection) {
                    // scale bitmap to 300x300, cause SSD model support 300 x300 image to detect object
                    processImage(
                        currentFrameBmp.scaleBitmap(
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_INPUT_SIZE
                        )
                    )
                }
            }
        }
        handlerKernel.postDelayed(r, 100L)
    }

    /**
     * @processImage() Function is responsible for detected result and overlay on detected object.
     * and in background process we calculate the onscreen location points on our 3D object and
     * get the intersect point between detected object and 3D object.
     */
    private fun processImage(newBitmap: Bitmap) {
        ++timestamp
        binding.graphicOverlay.postInvalidate()
        if (computingDetection) {
            return
        }
        computingDetection = true
        bgScope.launch {
            val results: List<Classifier.Recognition> =
                detector!!.recognizeImage(newBitmap)
            val mappedRecognitions: MutableList<Classifier.Recognition> =
                LinkedList<Classifier.Recognition>()
            for (result in results) {
                val location: RectF = result.location
                val location2 = RectF()
                if (result.confidence >= MINIMUM_CONFIDENCE_TF_OD_API) {
                    /**
                     * COCO SSD MobileNet-v1 classified 80 objects labeled in @labelmap.txt file.
                     * Here we are taking only the @Person object to classified by the model.
                     */
                    if (result.title.equals("person", ignoreCase = true)) {
                        val worldPosition = currentTransformableNode?.worldPosition
                        val sceneScreenPoint =
                            arFragment.arSceneView.scene.camera.worldToScreenPoint(worldPosition) // get the screen points(x,y) of the current scene
                        val scale = 7.113333f // scale image to screen visible size
                        val xOffset = 527.0f // xOffset to scale image
                        val yOffset = 0.0f // yOffset to scale image
                        location2.set(
                            location.left * scale,
                            location.top * scale,
                            location.right * scale,
                            location.bottom * scale
                        )
                        location2.offset(-xOffset, -yOffset)
                        if ((sceneScreenPoint.y - xOffset) <= (location2.bottom)) {
                            //updateUi(true)
                            result.location = location
                            result.paint = Paint().apply {
                                color = Color.RED
                                style = Paint.Style.STROKE
                                strokeWidth = 2 * resources.displayMetrics.density
                            }
                            mappedRecognitions.add(result)
                        } else {
                            //updateUi(false)
                            result.location = location
                            result.paint = Paint().apply {
                                color = Color.GREEN
                                style = Paint.Style.STROKE
                                strokeWidth = 2 * resources.displayMetrics.density
                            }
                            mappedRecognitions.add(result)
                        }
                    }
                }
            }
            updateUi(mappedRecognitions)
            binding.graphicOverlay.set(mappedRecognitions)
            computingDetection = false
        }
    }

    private fun updateUi(
        mappedRecognitions: MutableList<Classifier.Recognition>
    ) {
        uiScope.launch {
            if (mappedRecognitions.any { it.paint.color == Color.RED }) {
                renderable = renderableRed
                checkViolationDetection(mappedRecognitions)
            } else {
                renderable = renderableColored
                showViolationAlert(false)
            }
        }
    }

    private fun checkViolationDetection(mappedRecognitions: MutableList<Classifier.Recognition>) {
        if (!isDetectingViolation) {
            isDetectingViolation = true
            violationHandler.postDelayed({
                if (mappedRecognitions.any { it.paint.color == Color.RED }) {
                    sessionViolationCount++
                    currentViolation++
                    showViolationAlert(true)
                    if (Prefs.getPrefsBoolean(USER_SOUND_ON))
                        mdPlayer?.start()
                    binding.layoutSessionInfo.violationCount = "$sessionViolationCount violation"
                }
                isDetectingViolation = false
                if (currentViolation > violationThreshold)
                    calculateSafety()
            }, 3000L)
        } else return
    }

    private fun startSessionTimer() {
        val chronometer = binding.layoutSessionInfo.sessionTimer
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.setOnChronometerTickListener {
            val time: Long = SystemClock.elapsedRealtime() - chronometer.base
            val h = (time / 3600000).toInt()
            val m = (time - h * 3600000).toInt() / 60000
            val s = (time - h * 3600000 - m * 60000).toInt() / 1000
            binding.layoutSessionInfo.sessionTime = "$m m $s s"
        }
        chronometer.start()
    }

    private fun calculateSafety() {
        currentSafety -= (currentViolation - violationThreshold)
        currentViolation = 0
        binding.layoutSessionInfo.safetyPercent = "$currentSafety%"
    }


    private fun showViolationAlert(isViolated: Boolean) {
        if (isViolated) {
            binding.layoutSessionInfo.run {
                sessionViolatedLayout.visibility = View.VISIBLE
                sessionWatchLayout.visibility = View.GONE
                btnEndSession.setBackgroundResource(R.drawable.bg_btn_alrt_end_session)
                llSessionInfo.setBackgroundResource(R.drawable.bg_card_alrt_session_info)
            }
        } else {
            binding.layoutSessionInfo.run {
                sessionViolatedLayout.visibility = View.GONE
                sessionWatchLayout.visibility = View.VISIBLE
                btnEndSession.setBackgroundResource(R.drawable.bg_btn_end_session)
                llSessionInfo.setBackgroundResource(R.drawable.bg_card_session_info)
            }
        }

        Handler().postDelayed({
            if (isDetectingViolation) {
                showViolationAlert(false)
            }
        }, 1500)
    }

    // Remove the existing scene and anchor
    private fun clearAnchor() {
        readyToProcessFrame = false
        computingDetection = true
        mdPlayer?.stop()
        mdPlayer?.release()
        mdPlayer = null
        if (sceneView != null) {
            oldAnchorNode?.renderable = null
            oldAnchor?.detach()
            session = null
        }
        binding.layoutSessionInfo.sessionTimer.stop()
        arFragment.onDestroyView()
        sessionEndTime = System.currentTimeMillis()
        val sessionInfo =
            SessionInfo(
                safetyPercent = "$currentSafety%",
                sessionTime = binding.layoutSessionInfo.sessionTime!!,
                violationCount = "$sessionViolationCount",
                sessionStartTime = sessionStartTime, sessionEndTime = sessionEndTime
            )
        startSessionViewModel.sendSessionEndInfo(sessionInfo)
    }

    companion object {
        // Configuration values for the prepackaged SSD model.
        private const val TF_OD_API_INPUT_SIZE = 300
        private const val TF_OD_API_IS_QUANTIZED = true
        private const val TF_OD_API_MODEL_FILE = "detect.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"

        // Minimum detection confidence to track a detection.
        private const val MINIMUM_CONFIDENCE_TF_OD_API = 0.6f
    }
}