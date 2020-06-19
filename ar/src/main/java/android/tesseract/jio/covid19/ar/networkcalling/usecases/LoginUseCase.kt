package android.tesseract.jio.covid19.ar.networkcalling.usecases

import android.tesseract.jio.covid19.ar.networkcalling.Callback
import android.tesseract.jio.covid19.ar.networkcalling.model.LoginRequest
import android.tesseract.jio.covid19.ar.networkcalling.model.LoginResponse
import android.tesseract.jio.covid19.ar.networkcalling.retrofit.NetworkUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Created by Dipanshu Harbola on 18/6/20.
 */
class LoginUseCase {
    fun userLogin(loginRequest: LoginRequest, callback: Callback<LoginResponse>) {
        NetworkUtil.getApiInstance()!!
            .userLogin(loginRequest)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                callback.loading()
            }
            .subscribe(
                {
                    callback.onSuccessCall(it)
                }, {
                    callback.onFailureCall(it.message)
                })
    }
}