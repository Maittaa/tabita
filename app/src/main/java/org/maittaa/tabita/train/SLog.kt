package org.maittaa.tabita.train

import org.maittaa.tabita.Log

/** 5.23: журнал тренировок пишется в общий журнал Tabita (метка Train/…). */
object SLog {
    fun i(tag: String, msg: String) = Log.i("Train/$tag", msg)
    fun d(tag: String, msg: String) = Log.d("Train/$tag", msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = Log.e("Train/$tag", msg, t)
}
