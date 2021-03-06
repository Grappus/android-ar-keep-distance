package android.tesseract.jio.covid19.ar.journal

import android.os.Bundle
import android.tesseract.jio.covid19.ar.ARActivity
import android.tesseract.jio.covid19.ar.R
import android.tesseract.jio.covid19.ar.core.ARViewModel
import android.tesseract.jio.covid19.ar.databinding.FragmentJournalBinding
import android.tesseract.jio.covid19.ar.networkcalling.model.GraphPlotData
import android.tesseract.jio.covid19.ar.networkcalling.model.JournalData
import android.tesseract.jio.covid19.ar.utils.LineGraph
import android.tesseract.jio.covid19.ar.utils.ProgressLoader
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.activity_ar.*
import kotlinx.android.synthetic.main.fragment_journal.*
import kotlinx.android.synthetic.main.layout_bottom_action_buttons.view.*
import java.lang.Exception

/**
 * Created by Dipanshu Harbola on 11/6/20.
 */
class MyJournalFragment : Fragment(), ARViewModel.Navigator {

    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        FragmentJournalBinding.inflate(
            layoutInflater
        )
    }

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
        initComponents()
    }

    override fun showJournalData(data: JournalData) {
        val duration = when {
            data.totalDuration <= 60L -> {
                "${data.totalDuration} sec"
            }
            data.totalDuration <= 3600L -> {
                "${(data.totalDuration / 60) % 60} min"
            }
            else -> "${(data.totalDuration / 60) / 60} hrs"
        }
        binding.run {
            tvTotalTrackedTime.text = duration
            tvTotalSafetyPercent.text =
                if (data.safetyRate > 100f) "100%" else "${data.safetyRate.toInt()}%"
            tvTotalViolation.text = data.totalViolations.toString()
        }
    }

    override fun showGraphPlots(graphPlotData: MutableList<GraphPlotData>) {
        try {
            val graphDataList = mutableListOf<LineGraph.GraphData>()
            graphDataList.add(
                LineGraph.GraphData(
                    if (graphPlotData[0].plotdata.sumBy { it.violationCount }.toFloat() > 40f) {
                        40f
                    } else graphPlotData[0].plotdata.sumBy { it.violationCount }.toFloat()
                )
            )
            graphDataList.add(
                LineGraph.GraphData(
                    if (graphPlotData[1].plotdata.sumBy { it.violationCount }.toFloat() > 40f) {
                        40f
                    } else graphPlotData[1].plotdata.sumBy { it.violationCount }.toFloat()
                )
            )
            graphDataList.add(
                LineGraph.GraphData(
                    if (graphPlotData[2].plotdata.sumBy { it.violationCount }.toFloat() > 40f) {
                        40f
                    } else graphPlotData[2].plotdata.sumBy { it.violationCount }.toFloat()
                )
            )
            graphDataList.add(
                LineGraph.GraphData(
                    if (graphPlotData[3].plotdata.sumBy { it.violationCount }.toFloat() > 40f) {
                        40f
                    } else graphPlotData[3].plotdata.sumBy { it.violationCount }.toFloat()
                )
            )
            gvSafety.setGraphData(graphDataList.toList())
        } catch (e: Exception) {
        }
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
        val myJournalViewModel = ViewModelProvider(this).get(ARViewModel::class.java)
        myJournalViewModel.navigator = this
        myJournalViewModel.getMyJournal()
        myJournalViewModel.getGraphPlots()
        handleActionButtons()
    }

    private fun handleActionButtons() {
        (requireContext() as ARActivity).setupActionButtons()

        try {
            (requireContext() as ARActivity).layoutActionButtons.fabStartSession.setOnClickListener {
                val action =
                    MyJournalFragmentDirections.actionMyJournalFragmentToSessionStartFragment()
                findNavController().navigate(action)
            }

            (requireContext() as ARActivity).layoutActionButtons.fabSettings.setOnClickListener {
                val action =
                    MyJournalFragmentDirections.actionMyJournalFragmentToMyPreferencesFragment()
                findNavController().navigate(action)
            }

            (requireContext() as ARActivity).layoutActionButtons.fabJourneyStats.setOnClickListener {
                return@setOnClickListener
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}