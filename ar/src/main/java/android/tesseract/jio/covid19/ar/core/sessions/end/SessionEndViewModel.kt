package android.tesseract.jio.covid19.ar.core.sessions.end

import android.tesseract.jio.covid19.ar.networkcalling.Callback
import android.tesseract.jio.covid19.ar.networkcalling.UseCases
import android.tesseract.jio.covid19.ar.networkcalling.model.SessionEndRequest
import android.tesseract.jio.covid19.ar.networkcalling.model.SessionEndResponse
import android.tesseract.jio.covid19.ar.networkcalling.model.SessionInfo
import android.tesseract.jio.covid19.ar.networkcalling.model.UserLocation
import android.tesseract.jio.covid19.ar.networkcalling.retrofit.NetworkUtil
import android.tesseract.jio.covid19.ar.utils.TimeUtils
import android.text.format.Time
import android.util.Log
import androidx.lifecycle.ViewModel
import java.util.concurrent.TimeUnit

/**
 * Created by Dipanshu Harbola on 18/6/20.
 */
class SessionEndViewModel: ViewModel() {

    fun sendSessionEndInfo(sessionInfo: SessionInfo) {
        val sessionStartTime = TimeUtils.getTime(sessionInfo.sessionStartTime, TimeUtils.TIME_SERVER)
        val sessionTimeGap = sessionInfo.sessionEndTime - sessionInfo.sessionStartTime
        val mins = ((sessionTimeGap/(1000*60)) % 60)
        val totalDuration = TimeUnit.MINUTES.toSeconds(mins)
        val violationCount = sessionInfo.violationCount
        val safetyRate =  sessionInfo.safetyPercent
        val location = UserLocation(
            latitude = "0.0", longitude = "0.0"
        )
        val sessionEndRequest = SessionEndRequest(
            startTime = sessionStartTime, totalDuration = totalDuration,
            violationCount = violationCount.toInt(), safetyRate = safetyRate, location = location
        )
        NetworkUtil.useCase.sessionActivityUseCase.postSessionActivity(sessionEndRequest, object: Callback<SessionEndResponse>() {
            override fun loading() {

            }

            override fun onSuccessCall(value: SessionEndResponse) {
                if (value.statusCode == 200) {
                    Log.d("TAG", "Session Info Success")
                }
            }

            override fun onFailureCall(message: String?) {
                Log.e("TAG", "Error to send session Info")
            }

        })

    }

}