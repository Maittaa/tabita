package org.maittaa.tabita

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Журнал работы приложения в файл Документы/Tabita/logging/tabita_ГГГГ-ММ-ДД.log.
 * Уровни: 0 — выкл, 1 — краткий (ошибки, вылеты, важные события), 2 — полный (все действия).
 */
object Log {
    const val OFF = 0
    const val SHORT = 1
    const val FULL = 2
    const val FOLDER = "Tabita/logging"

    @Volatile var level = SHORT
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()
    private val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private var uriDay: String? = null
    private var uri: Uri? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        level = Prefs(app).logLevel
    }

    /** Важное событие (пишется в кратком и полном режиме). */
    fun i(tag: String, msg: String) = write(SHORT, "I", tag, msg, null)
    /** Подробность (только полный режим). */
    fun d(tag: String, msg: String) = write(FULL, "D", tag, msg, null)
    fun e(tag: String, msg: String, t: Throwable? = null) = write(SHORT, "E", tag, msg, t)

    private fun line(kind: String, tag: String, msg: String, t: Throwable?): String {
        val sb = StringBuilder("${LocalDateTime.now().format(ts)} $kind/$tag: $msg\n")
        if (t != null) {
            val sw = StringWriter(); t.printStackTrace(PrintWriter(sw)); sb.append(sw).append('\n')
        }
        return sb.toString()
    }

    private fun write(min: Int, kind: String, tag: String, msg: String, t: Throwable?) {
        if (level < min || !::app.isInitialized) return
        val text = line(kind, tag, msg, t)
        android.util.Log.println(if (kind == "E") android.util.Log.ERROR else android.util.Log.INFO, "Tabita/$tag", msg)
        io.execute { append(text) }
    }

    /** Синхронная запись (при вылете процесс сразу завершится). */
    fun crash(t: Throwable) {
        if (level < SHORT || !::app.isInitialized) return
        runCatching { append(line("E", "CRASH", "Вылет приложения в потоке ${Thread.currentThread().name}", t)) }
    }

    @Synchronized
    private fun append(text: String) {
        runCatching {
            val bytes = text.toByteArray()
            val u = currentUri()
            if (u != null) {
                app.contentResolver.openOutputStream(u, "wa")!!.use { it.write(bytes) }
            } else {
                legacyFile().appendBytes(bytes)
            }
        }.onFailure {
            // файл удалили или нет доступа — создадим заново при следующей записи
            uri = null; uriDay = null
            runCatching { File(app.filesDir, "fallback.log").appendText(text) }
        }
    }

    private fun fileName() = "tabita_${LocalDate.now()}.log"

    /** Android 10+: файл в общей папке «Документы» через MediaStore, без разрешений. */
    private fun currentUri(): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val day = LocalDate.now().toString()
        if (uri != null && uriDay == day) return uri
        val rel = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/"
        val name = fileName()
        val coll = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val found = app.contentResolver.query(
            coll, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(rel, name), null,
        )?.use { c -> if (c.moveToFirst()) android.content.ContentUris.withAppendedId(coll, c.getLong(0)) else null }
        val u = found ?: app.contentResolver.insert(coll, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
        })
        uri = u; uriDay = day
        return u
    }

    private fun legacyFile(): File {
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "logging").apply { mkdirs() }
        return File(dir, fileName())
    }

    /** Uri сегодняшнего файла для кнопки «Поделиться». */
    fun shareUri(): Uri? = runCatching {
        io.submit { }.get()  // дождаться записи
        (if (Build.VERSION.SDK_INT >= 29) runCatching { currentUri() }.getOrNull() else null)
            ?: legacyFile().takeIf { it.exists() }?.let {
                androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", it)
            }
    }.getOrNull()

    val folderTitle get() = (if (I18n.isEn) "Documents/" else "Документы/") + FOLDER
}

/** Приложение: включает журнал и перехват вылетов до запуска любых экранов. */
class TabitaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.init(this)
        AppNames.init(this)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            Log.crash(ex)
            prev?.uncaughtException(th, ex)
        }
        Log.i("App", "Запуск ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}); Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); ${Build.MANUFACTURER} ${Build.MODEL}")
    }
}
