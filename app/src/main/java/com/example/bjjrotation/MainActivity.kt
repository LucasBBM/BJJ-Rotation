package com.example.bjjrotation

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

@UnstableApi
class MainActivity : ComponentActivity() {
    private data class RotationPoint(val timeUs: Long, val degrees: Float)
    private data class Clip(val uri: Uri, val points: MutableList<RotationPoint> = java.util.concurrent.CopyOnWriteArrayList())

    private val clips = mutableListOf<Clip>()
    private var index = 0
    private var player: androidx.media3.exoplayer.ExoPlayer? = null
    private lateinit var root: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var dial: RotationDial
    private lateinit var status: TextView
    private lateinit var primary: Button
    private var countdown: CountDownTimer? = null
    private var totalAngle = 0f
    private var recording = false
    private var exportedUri: Uri? = null
    private var transformer: Transformer? = null

    private val pickVideos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            clips.clear()
            uris.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                clips.add(Clip(uri))
            }
            index = 0
            showRotationScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && recording) {
                    recording = false
                    val p = player ?: return
                    clips[index].points.add(RotationPoint(p.duration.coerceAtLeast(0) * 1000, totalAngle))
                    p.setVideoEffects(emptyList())
                    showNextScreen()
                }
            }
        })
        showImportScreen()
    }

    private fun baseScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)
    }

    private fun text(value: String, size: Float = 16f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
    }

    private fun button(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { action() }
    }

    private fun showImportScreen() {
        stopPlayback()
        baseScreen()
        root.gravity = Gravity.CENTER
        root.addView(text("BJJ Rotation", 26f), LinearLayout.LayoutParams(-1, dp(72)))
        root.addView(button("Importar vídeos") { pickVideos.launch(arrayOf("video/*")) }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(text("Em cada vídeo, mantenha a linha dos ombros do lutador desejado alinhada com a horizontal, e o ombro direito à direita, e o ombro esquerdo à esquerda.", 15f), LinearLayout.LayoutParams(-1, dp(120)))
        root.addView(text("Você poderá importar vários vídeos. Eles serão processados na ordem selecionada.", 13f))
    }

    private fun showRotationScreen() {
        stopPlayback()
        if (index >= clips.size) { startExport(); return }
        totalAngle = clips[index].points.lastOrNull()?.degrees ?: 0f
        recording = false
        baseScreen()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        header.addView(button("←") { showImportScreen() }, LinearLayout.LayoutParams(dp(64), dp(48)))
        header.addView(text("Vídeo ${index + 1} de ${clips.size}"), LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(header)
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            player = this@MainActivity.player
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(14) })
        dial = RotationDial().apply { setAngle(totalAngle) }
        root.addView(dial, LinearLayout.LayoutParams(dp(180), dp(180)).apply { gravity = Gravity.CENTER; bottomMargin = dp(18) })
        status = text("Arraste o ponto branco para girar durante a reprodução", 13f)
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)))
        primary = button("Iniciar · 3 segundos") { beginCountdown() }
        root.addView(primary, LinearLayout.LayoutParams(-1, dp(52)))
        val p = player ?: return
        p.setMediaItem(MediaItem.fromUri(clips[index].uri)); p.prepare()
    }

    private fun beginCountdown() {
        countdown?.cancel()
        val p = player ?: return
        p.pause(); p.seekTo(0)
        clips[index].points.clear()
        totalAngle = 0f
        dial.setAngle(0f)
        status.text = "3"
        primary.isEnabled = false
        countdown = object : CountDownTimer(3000, 1000) {
            override fun onTick(ms: Long) { status.text = ((ms + 999) / 1000).toString() }
            override fun onFinish() {
                status.text = "Girando: ${"%.1f".format(totalAngle)}°"
                primary.isEnabled = true
                primary.text = "Reiniciar"
                recording = true
                clips[index].points.add(RotationPoint(0L, totalAngle))
                p.setVideoEffects(listOf(TimelineRotation(clips[index].points, false)))
                p.seekTo(0); p.play()
            }
        }.start()
    }

    private fun showNextScreen() {
        stopPlayback(); baseScreen(); root.gravity = Gravity.CENTER
        root.addView(text("Vídeo ${index + 1} concluído", 20f), LinearLayout.LayoutParams(-1, dp(90)))
        root.addView(button(if (index + 1 < clips.size) "Próximo vídeo" else "Produzir compilação") {
            index++
            if (index < clips.size) showRotationScreen() else startExport()
        }, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun startExport() {
        stopPlayback(); baseScreen(); root.gravity = Gravity.CENTER
        root.addView(text("Produzindo vídeos…", 22f))
        root.addView(text("Aplicando a rotação registrada e criando, em seguida, a versão espelhada de cada vídeo.", 14f), LinearLayout.LayoutParams(-1, dp(100)))
        val outputName = "BJJ Rotation_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BJJ Rotation")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { toast("Não foi possível criar o arquivo de saída."); showImportScreen(); return }
        exportedUri = uri
        val tempOutput = java.io.File(cacheDir, "bjjrotation_${System.currentTimeMillis()}.mp4")
        val edited = mutableListOf<EditedMediaItem>()
        clips.forEach { clip ->
            val normal = EditedMediaItem.Builder(MediaItem.fromUri(clip.uri))
                .setEffects(Effects(emptyList(), listOf(TimelineRotation(clip.points, false))))
                .build()
            val mirrored = EditedMediaItem.Builder(MediaItem.fromUri(clip.uri))
                .setEffects(Effects(emptyList(), listOf(TimelineRotation(clip.points, true))))
                .build()
            edited.add(normal); edited.add(mirrored)
        }
        val sequence = EditedMediaItemSequence.Builder(edited).build()
        val composition = Composition.Builder(sequence).build()
        transformer = Transformer.Builder(this)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    try {
                        contentResolver.openOutputStream(uri, "w")!!.use { out -> tempOutput.inputStream().use { it.copyTo(out) } }
                        tempOutput.delete()
                        showPreview(uri)
                    } catch (e: Exception) {
                        contentResolver.delete(uri, null, null)
                        toast("Falha ao salvar: ${e.localizedMessage}"); showImportScreen()
                    }
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    tempOutput.delete(); contentResolver.delete(uri, null, null)
                    toast("Erro na exportação: ${exportException.localizedMessage}")
                    showImportScreen()
                }
            }).build()
        transformer?.start(composition, tempOutput.absolutePath)
    }

    private fun showPreview(uri: Uri) {
        baseScreen(); root.gravity = Gravity.CENTER
        root.addView(text("Prévia do resultado final", 20f), LinearLayout.LayoutParams(-1, dp(54)))
        val preview = PlayerView(this).apply { useController = true; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; player = this@MainActivity.player }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        player?.setMediaItem(MediaItem.fromUri(uri)); player?.prepare(); player?.playWhenReady = false
        root.addView(button("Exportado em Filmes / BJJ Rotation") { toast("O vídeo foi salvo na galeria, na pasta Movies/BJJ Rotation.") }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(button("Voltar ao início") { clips.clear(); showImportScreen() }, LinearLayout.LayoutParams(-1, dp(50)))
    }

    private fun stopPlayback() {
        countdown?.cancel(); countdown = null
        recording = false
        player?.pause(); player?.clearMediaItems()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() { stopPlayback(); player?.release(); super.onDestroy() }

    private inner class RotationDial : View(this@MainActivity) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var angle = 0f
        private var centerX = 0f; private var centerY = 0f; private var radius = 0f
        fun setAngle(value: Float) { angle = value; invalidate() }
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas); centerX = width / 2f; centerY = height / 2f; radius = minOf(width, height) * .41f
            paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = dp(7).toFloat(); paint.color = 0xFF777780.toInt()
            canvas.drawCircle(centerX, centerY, radius, paint)
            val radians = Math.toRadians((angle - 90).toDouble())
            val x = centerX + radius * kotlin.math.cos(radians).toFloat(); val y = centerY + radius * kotlin.math.sin(radians).toFloat()
            paint.style = android.graphics.Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(x, y, dp(13).toFloat(), paint)
            paint.color = Color.WHITE; paint.textSize = dp(23).toFloat(); paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("↻", centerX, centerY + dp(8), paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val raw = Math.toDegrees(atan2((event.y - centerY).toDouble(), (event.x - centerX).toDouble())).toFloat() + 90f
                val normalized = ((raw % 360f) + 360f) % 360f
                val previous = angle
                var delta = normalized - (((previous % 360f) + 360f) % 360f)
                if (delta > 180) delta -= 360f else if (delta < -180) delta += 360f
                angle += delta
                if (recording) {
                    totalAngle = angle
                    val timeUs = (player?.currentPosition ?: 0L) * 1000L
                    val list = clips[index].points
                    if (list.isEmpty() || timeUs - list.last().timeUs >= 33000L) list.add(RotationPoint(timeUs, angle))
                    else list[list.lastIndex] = RotationPoint(timeUs, angle)
                    status.text = "Rotação: ${"%.1f".format(angle)}°"
                }
                invalidate(); return true
            }
            return true
        }
    }

    private class TimelineRotation(private val points: List<RotationPoint>, private val mirror: Boolean) : MatrixTransformation {
        override fun configure(inputWidth: Int, inputHeight: Int): android.util.Size = android.util.Size(inputWidth, inputHeight)
        override fun getMatrix(presentationTimeUs: Long): Matrix {
            val angle = interpolate(points, presentationTimeUs)
            return Matrix().apply {
                if (mirror) postScale(-1f, 1f)
                postRotate(angle)
            }
        }
        companion object {
            private fun interpolate(points: List<RotationPoint>, time: Long): Float {
                if (points.isEmpty()) return 0f
                if (time <= points.first().timeUs) return points.first().degrees
                for (i in 0 until points.lastIndex) {
                    val a = points[i]; val b = points[i + 1]
                    if (time <= b.timeUs) {
                        val span = (b.timeUs - a.timeUs).coerceAtLeast(1L)
                        val f = (time - a.timeUs).toFloat() / span
                        return a.degrees + (b.degrees - a.degrees) * f
                    }
                }
                return points.last().degrees
            }
        }
    }
}
