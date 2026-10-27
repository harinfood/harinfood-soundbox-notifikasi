package com.contoh.soundboxnotifikasi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.Locale

/**
 * Urutan setiap notifikasi (berurutan, satu per satu):
 * 1. Volume notifikasi dinaikkan ke maksimum (jika diaktifkan di pengaturan)
 * 2. MP3 khusus aplikasi / MP3 umum / nada HP
 * 3. TTS dibuat menjadi berkas audio, lalu diputar dengan booster
 * 4. Setelah antrean habis, volume dikembalikan ke semula
 */
class AmountSpeaker(private val context: Context) {

    companion object {
        private const val TAG = "SoundboxQRIS"
        private const val MB_PER_PERSEN = 15 // booster 100% = +15 dB
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val atribut = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private class Job(val kata: String, val berkasKhusus: File?, val lewatMp3: Boolean)

    private var tts: TextToSpeech? = null
    private var siap = false
    private val antrean = ArrayDeque<Job>()
    private var sedangJalan = false
    private var volumeAsli = -1
    private var urutan = 0
    private var pemutar: MediaPlayer? = null
    private var penambah: LoudnessEnhancer? = null
    private var ttsId: String? = null
    private var ttsBerkas: File? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            handler.post { ttsSelesai(utteranceId) }
        }

        override fun onError(utteranceId: String?) {
            handler.post { ttsSelesai(utteranceId) }
        }
    }

    init {
        tts = TextToSpeech(context) { status ->
            handler.post { onTtsSiap(status) }
        }
    }

    private fun onTtsSiap(status: Int) {
        val t = tts ?: return
        val bahasa = if (status == TextToSpeech.SUCCESS) t.setLanguage(Locale("id", "ID")) else null
        siap = status == TextToSpeech.SUCCESS &&
               bahasa != TextToSpeech.LANG_MISSING_DATA &&
               bahasa != TextToSpeech.LANG_NOT_SUPPORTED
        if (siap) {
            t.setAudioAttributes(atribut)
            t.setSpeechRate(0.9f)
            t.setOnUtteranceProgressListener(listener)
            lanjutkan()
        } else {
            antrean.clear()
            sedangJalan = false
            pulihkanVolume()
        }
    }

    fun ucapkan(kata: String) = ucapkan(kata, null)

    /** berkasKhusus = MP3 milik aplikasi pengirim (jika ada). lewatMp3 = true hanya membacakan TTS. */
    fun ucapkan(kata: String, berkasKhusus: File?, lewatMp3: Boolean = false) {
        handler.post {
            antrean.addLast(Job(kata, berkasKhusus, lewatMp3))
            if (!sedangJalan) lanjutkan()
        }
    }

    private fun lanjutkan() {
        if (sedangJalan || !siap) return
        val job = antrean.removeFirstOrNull()
        if (job == null) {
            pulihkanVolume()
            return
        }
        sedangJalan = true
        aturVolumeMaks()
        mainkanMp3(job.berkasKhusus, job.lewatMp3) { bacakan(job.kata) }
    }

    private fun jobSelesai() {
        sedangJalan = false
        lanjutkan()
    }

    private fun mainkanMp3(berkasKhusus: File?, lewatMp3: Boolean, lanjut: () -> Unit) {
        if (lewatMp3) {
            lanjut()
            return
        }
        val berkas = when {
            berkasKhusus != null && berkasKhusus.exists() && berkasKhusus.length() > 0 -> berkasKhusus
            Pengaturan.adaSuaraKustom(context) -> Pengaturan.fileSuara(context)
            else -> null
        }
        if (berkas != null) {
            if (putar(lanjut) { it.setDataSource(berkas.absolutePath) }) return
            handler.post { Toast.makeText(context, "MP3 gagal diputar, memakai nada HP", Toast.LENGTH_SHORT).show() }
        }

        val bawaan = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (bawaan == null || !putar(lanjut) { it.setDataSource(context, bawaan) }) {
            lanjut()
        }
    }

    /** Memutar satu sumber audio. Mengembalikan false jika sumber tidak bisa disiapkan (lanjut tidak dipanggil). */
    private fun putar(lanjut: () -> Unit, siapkan: (MediaPlayer) -> Unit): Boolean {
        val p = MediaPlayer()
        return try {
            p.setAudioAttributes(atribut)
            siapkan(p)
            p.setOnCompletionListener { m ->
                lepasPemutar(m)
                lanjut()
            }
            p.setOnErrorListener { m, what, extra ->
                Log.e(TAG, "MediaPlayer error: $what, $extra")
                lepasPemutar(m)
                lanjut()
                true
            }
            p.prepare()
            pemutar = p
            pasangBooster(p.audioSessionId)
            p.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar suara", e)
            p.release()
            false
        }
    }

    /** Booster: menaikkan suara di atas batas volume HP sesuai pengaturan. */
    private fun pasangBooster(sesi: Int) {
        try {
            val persen = Pengaturan.boosterPersen(context)
            if (persen <= 0) return
            penambah = LoudnessEnhancer(sesi).apply {
                setTargetGain(persen * MB_PER_PERSEN)
                enabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Booster tidak didukung di HP ini", e)
            penambah = null
        }
    }

    private fun lepasPemutar(m: MediaPlayer) {
        try {
            penambah?.release()
        } catch (e: Exception) {
            // abaikan
        }
        penambah = null
        if (pemutar === m) pemutar = null
        try {
            m.release()
        } catch (e: Exception) {
            // abaikan
        }
    }

    /** Teks dibuat menjadi berkas audio dulu agar bisa diberi booster; jika tidak bisa, langsung diucapkan. */
    private fun bacakan(kata: String) {
        val t = tts
        if (t == null || !siap) {
            jobSelesai()
            return
        }
        val id = "tts-${urutan++}"
        val berkas = File(context.cacheDir, "$id.wav")
        ttsId = id
        ttsBerkas = berkas
        val hasil = t.synthesizeToFile(kata, null, berkas, id)
        if (hasil != TextToSpeech.SUCCESS) {
            ttsBerkas = null
            t.speak(kata, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    private fun ttsSelesai(id: String?) {
        if (id == null || id != ttsId) return
        val berkas = ttsBerkas
        ttsId = null
        ttsBerkas = null
        if (berkas == null) {
            jobSelesai() // sudah diucapkan langsung
            return
        }
        if (!berkas.exists() || berkas.length() == 0L) {
            jobSelesai()
            return
        }
        val diputar = putar({
            berkas.delete()
            jobSelesai()
        }) { it.setDataSource(berkas.absolutePath) }
        if (!diputar) {
            berkas.delete()
            jobSelesai()
        }
    }

    /** Volume notifikasi dinaikkan ke maksimum selama antrean berjalan. */
    private fun aturVolumeMaks() {
        if (!Pengaturan.volumeMaksOtomatis(context)) return
        if (volumeAsli < 0) volumeAsli = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        audio.setStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
            0
        )
    }

    private fun pulihkanVolume() {
        if (volumeAsli >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, volumeAsli, 0)
            volumeAsli = -1
        }
    }

    fun berhenti() {
        antrean.clear()
        sedangJalan = false
        pemutar?.let { m ->
            try {
                m.stop()
            } catch (e: Exception) {
                // sudah berhenti
            }
            lepasPemutar(m)
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        siap = false
        pulihkanVolume()
    }
}
