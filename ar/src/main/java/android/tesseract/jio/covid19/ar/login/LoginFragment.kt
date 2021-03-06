package android.tesseract.jio.covid19.ar.login

import android.os.Bundle
import android.os.Handler
import android.tesseract.jio.covid19.ar.ARActivity
import android.tesseract.jio.covid19.ar.R
import android.tesseract.jio.covid19.ar.core.ARViewModel
import android.tesseract.jio.covid19.ar.databinding.FragmentLoginBinding
import android.tesseract.jio.covid19.ar.utils.Prefs
import android.tesseract.jio.covid19.ar.utils.PrefsConstants.USER_UID
import android.tesseract.jio.covid19.ar.utils.ProgressLoader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController

/**
 * Created by Dipanshu Harbola on 26/5/20.
 */
class LoginFragment : Fragment(), ARViewModel.Navigator {

    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        FragmentLoginBinding.inflate(
            layoutInflater
        )
    }
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

    override fun navigateToNextScreen() {
        findNavController().navigate(R.id.action_loginFragment_to_sessionStartFragment)
    }

    override fun showLoading(isLoading: Boolean) {
        if (isLoading)
            ProgressLoader.showLoader(requireContext())
        else
            ProgressLoader.hideLoader()
    }

    override fun showNetworkError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT)
            .show()
    }

    private fun initComponents() {
        val splashViewModel = ViewModelProvider(this).get(ARViewModel::class.java)
        splashViewModel.navigator = this
        (requireContext() as ARActivity).setupActionButtons()
        binding.btnSubmit.setOnClickListener {
            if (binding.etUserName.text.toString()
                    .isNotBlank() && Prefs.getPrefsString(USER_UID).isNotBlank()
            ) {
                splashViewModel.authenticateUser(
                    Prefs.getPrefsString(USER_UID),
                    binding.etUserName.text.toString()
                )
            } else Toast.makeText(requireContext(), "All Details required", Toast.LENGTH_SHORT)
                .show()
        }
    }
}