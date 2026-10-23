package com.contoh.soundboxnotifikasi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.util.Locale

/**
 * Urutan setiap pembayaran:
 * 1. Volume notifikasi dinaikkan ke maksimum
 * 2. Suara dibunyikan: mp3 pilihan, atau nada notifikasi HP jika belum memilih
 * 3. TTS membacakan nominal
 * 4. Setelah semua selesai, volume dikembalikan ke semula
 */
class AmountSpeaker(private val context: Context) {

    companion object {
        private const val TAG = "SoundboxQRIS"
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val atribut = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var tts: TextToSpeech? = null
    private var siap = false
    private val antrean = mutableListOf<String>()
    private var prosesAktif = 0
    private var volumeAsli = -1
    private var urutan = 0

    init {
        tts = TextToSpeech(context) { status ->
            val bahasa = if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("id", "ID")) else null
            siap = status == TextToSpeech.SUCCESS &&
                   bahasa != TextToSpeech.LANG_MISSING_DATA &&
                   bahasa != TextToSpeech.LANG_NOT_SUPPORTED

            if (siap) {
                tts?.setAudioAttributes(atribut)
                tts?.setSpeechRate(0.9f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        handler.post { selesai() }
                    }

                    override fun onError(utteranceId: String?) {
                        handler.post { selesai() }
                    }
                })
                antrean.forEach { bicara(it) }
                antrean.clear()
            } else {
                handler.post {
                    antrean.clear()
                    prosesAktif = 0
                    pulihkanVolume()
                }
            }
        }
    }

    fun ucapkan(kata: String) {
        handler.post {
            prosesAktif++
            if (volumeAsli < 0) {
                volumeAsli = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            }
            audio.setStreamVolume(
                AudioManager.STREAM_NOTIFICATION,
                audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
                0
            )
            mainkanSuara { bicara(kata) }
        }
    }

    private fun mainkanSuara(lanjut: () -> Unit) {
        val berkas = Pengaturan.fileSuara(context)

        if (Pengaturan.adaSuaraKustom(context)) {
            if (putar(lanjut) { it.setDataSource(berkas.absolutePath) }) return
            handler.post {
                Toast.makeText(context, "MP3 gagal diputar, memakai nada HP", Toast.LENGTH_SHORT).show()
            }
        }

        val bawaan = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (bawaan == null || !putar(lanjut) { it.setDataSource(context, bawaan) }) {
            lanjut()
        }
    }

    /** Mengembalikan false jika sumber tidak bisa disiapkan. Dalam kasus itu, lanjut tidak dipanggil. */
    private fun putar(lanjut: () -> Unit, siapkan: (MediaPlayer) -> Unit): Boolean {
        val pemutar = MediaPlayer()
        return try {
            pemutar.setAudioAttributes(atribut)
            siapkan(pemutar)
            pemutar.setOnCompletionListener {
                it.release()
                lanjut()
            }
            pemutar.setOnErrorListener { p, what, extra ->
                Log.e(TAG, "MediaPlayer error: $what, $extra")
                p.release()
                lanjut()
                true
            }
            pemutar.prepare()
            pemutar.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar suara", e)
            pemutar.release()
            false
        }
    }

    private fun bicara(kata: String) {
        if (siap) {
            tts?.speak(kata, TextToSpeech.QUEUE_ADD, null, "pembayaran-${urutan++}")
        } else {
            antrean.add(kata)
        }
    }

    private fun selesai() {
        prosesAktif = (prosesAktif - 1).coerceAtLeast(0)
        if (prosesAktif == 0) pulihkanVolume()
    }

    private fun pulihkanVolume() {
        if (volumeAsli >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, volumeAsli, 0)
            volumeAsli = -1
        }
    }

    fun berhenti() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        siap = false
        pulihkanVolume()
    }
}
