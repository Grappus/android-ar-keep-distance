package android.tesseract.jio.covid19.ar.core.sessions.end

import android.os.Bundle
import android.tesseract.jio.covid19.ar.ARActivity
import android.tesseract.jio.covid19.ar.R
import android.tesseract.jio.covid19.ar.databinding.FragmentEndSessionBinding
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

/**
 * Created by Dipanshu Harbola on 5/6/20.
 *
 */
class SessionEndFragment : Fragment() {

    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        FragmentEndSessionBinding.inflate(
            layoutInflater
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireContext() as ARActivity).setupActionButtons()
        binding.fabCloseSession.setOnClickListener { navigateBack() }
    }

    fun navigateBack() {
        findNavController().navigate(R.id.action_sessionEndFragment_to_sessionStartFragment)
    }
}