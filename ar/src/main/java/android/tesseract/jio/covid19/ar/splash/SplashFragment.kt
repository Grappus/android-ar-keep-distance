package android.tesseract.jio.covid19.ar.splash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import android.tesseract.jio.covid19.ar.R
import android.tesseract.jio.covid19.ar.databinding.FragmentSplashBinding
import android.tesseract.jio.covid19.ar.utils.Prefs
import android.tesseract.jio.covid19.ar. utils.PrefsConstants.FINISHED_WALKTHROUGH
import androidx.annotation.Keep

/**
 * Created by Dipanshu Harbola on 26/5/20.
 */
@Keep
class SplashFragment : Fragment() {

    private val binding by lazy(LazyThreadSafetyMode.NONE) { FragmentSplashBinding.inflate(layoutInflater) }
    var handler: Handler? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        handler = Handler()
        initComponents()
    }

    override fun onPause() {
        super.onPause()
        handler = null
    }

    private fun initComponents() {
        handler?.postDelayed({
            if (!Prefs.getPrefsBoolean(FINISHED_WALKTHROUGH)) {
                findNavController().navigate(R.id.action_splashFragment_to_walkThroughFragment)
            }
            else if (!arePermissionsGranted())
                findNavController().navigate(R.id.action_splashFragment_to_permissionFragment)
            else {
                findNavController().navigate(R.id.action_splashFragment_to_sessionStartFragment)
            }
        }, 2000L)
    }

    // check if all the required permissions are granted by user.
    private fun arePermissionsGranted(): Boolean {
        val permissionCamera =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        val permissionLocation =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        return (permissionCamera == PackageManager.PERMISSION_GRANTED) && (permissionLocation == PackageManager.PERMISSION_GRANTED)
    }
}