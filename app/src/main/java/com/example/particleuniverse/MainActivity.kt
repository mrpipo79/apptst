package com.example.particleuniverse

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.particleuniverse.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var particleView: ParticleView
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())
    private val audioRunnable = Runnable { updateAudioLevel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide system UI for immersive experience
        hideSystemUI()

        // Create and add ParticleView
        particleView = ParticleView(this)
        binding.root.addView(particleView, 0)

        // Request microphone permission for audio visualization
        requestMicrophonePermission()
    }

    override fun onResume() {
        super.onResume()
        particleView.onResume()
        if (isRecording) {
            startAudioVisualization()
        }
    }

    override fun onPause() {
        super.onPause()
        particleView.onPause()
        stopAudioVisualization()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioVisualization()
        particleView.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } else {
            startAudioVisualization()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioVisualization()
        }
    }

    private fun startAudioVisualization() {
        if (isRecording) return
        
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile("/dev/null")
        }

        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isRecording = true
            handler.post(audioRunnable)
        } catch (e: IOException) {
            Log.e("AudioViz", "Failed to start audio recording", e)
        }
    }

    private fun stopAudioVisualization() {
        handler.removeCallbacks(audioRunnable)
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("AudioViz", "Error stopping recorder", e)
        }
        mediaRecorder = null
        isRecording = false
        particleView.setAudioEnergy(0f)
    }

    private fun updateAudioLevel() {
        if (isRecording && mediaRecorder != null) {
            try {
                val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                // Normalize amplitude (max is 32767 for 16-bit audio)
                val normalized = min(maxAmplitude.toFloat() / 32767f * 10f, 10f)
                particleView.setAudioEnergy(normalized)
            } catch (e: Exception) {
                // Ignore
            }
        }
        handler.postDelayed(audioRunnable, 16) // ~60fps
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION = 100
    }
}