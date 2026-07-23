package com.example.particleuniverse

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Particle system
    private val particles = mutableListOf<Particle>()
    private val maxParticles = 2000
    private val touchPoints = mutableListOf<TouchPoint>()
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val trailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
    }

    // Physics
    private var gravityX = 0f
    private var gravityY = 9.8f
    private var gravityStrength = 1.2f

    // Time-based color palette
    private var timeOfDay = 12
    private val palette = Palette()

    // Animation
    private var isRunning = false
    private var lastFrameTime = 0L
    private val frameCallback = Runnable { updateAndDraw() }

    // Screen dimensions
    private var screenWidth = 0f
    private var screenHeight = 0f

    // Audio reactivity
    private var audioEnergy = 0f
    private var bassEnergy = 0f
    private var midEnergy = 0f
    private var highEnergy = 0f

    init {
        palette.updateForHour(timeOfDay)
        setWillNotDraw(false)
    }

    fun setTimeOfDay(hour: Int) {
        timeOfDay = hour.coerceIn(0, 23)
        palette.updateForHour(timeOfDay)
    }

    fun setAudioEnergy(energy: Float, bass: Float, mid: Float, high: Float) {
        audioEnergy = energy.coerceIn(0f, 10f)
        bassEnergy = bass.coerceIn(0f, 10f)
        midEnergy = mid.coerceIn(0f, 10f)
        highEnergy = high.coerceIn(0f, 10f)
    }

    fun addTouchPoint(x: Float, y: Float, pressure: Float) {
        touchPoints.add(TouchPoint(x, y, pressure))
        val count = (10 * pressure).toInt().coerceIn(5, 25)
        repeat(count) {
            spawnParticle(x, y, true)
        }
    }

    fun clearTouchPoints() {
        touchPoints.clear()
    }

    private fun spawnParticle(x: Float, y: Float, fromTouch: Boolean = false) {
        if (particles.size >= maxParticles) return

        val angle = Random.nextDouble(0.0, 2.0 * Math.PI)
        val speed = if (fromTouch) {
            Random.nextDouble(50.0, 200.0)
        } else {
            Random.nextDouble(10.0, 80.0)
        }
        val vx = (speed * cos(angle)).toFloat()
        val vy = (speed * sin(angle)).toFloat()

        val life = if (fromTouch) Random.nextDouble(2.0, 5.0) else Random.nextDouble(8.0, 20.0)
        val size = if (fromTouch) Random.nextDouble(3.0, 8.0) else Random.nextDouble(1.5, 5.0)
        val hue = palette.randomHue()
        val saturation = palette.saturation
        val brightness = palette.brightness

        particles.add(Particle(x, y, vx, vy, life.toFloat(), size.toFloat(), hue, saturation, brightness))
    }

    fun resume() {
        if (!isRunning) {
            isRunning = true
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            lastFrameTime = System.nanoTime()
            post(frameCallback)
        }
    }

    fun pause() {
        if (isRunning) {
            isRunning = false
            sensorManager.unregisterListener(this)
            removeCallbacks(frameCallback)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // Map accelerometer to gravity vector
                // In landscape, X is forward/back, Y is left/right
                gravityX = -it.values[1] * gravityStrength  // Y axis -> X gravity
                gravityY = it.values[0] * gravityStrength   // X axis -> Y gravity
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
    }

    private fun updateAndDraw() {
        val now = System.nanoTime()
        val dt = if (lastFrameTime > 0) (now - lastFrameTime) / 1_000_000_000.0 else 0.016
        lastFrameTime = now

        updateParticles(dt.toFloat())
        invalidate()

        if (isRunning) {
            post(frameCallback)
        }
    }

    private fun updateParticles(dt: Float) {
        // Spawn ambient particles
        if (particles.size < maxParticles * 0.7 && Random.nextFloat() < 0.3) {
            val x = Random.nextFloat() * screenWidth
            val y = screenHeight + 50f
            spawnParticle(x, y, false)
        }

        // Audio-reactive particle burst
        if (audioEnergy > 2f && Random.nextFloat() < audioEnergy * 0.1) {
            val x = Random.nextFloat() * screenWidth
            val y = Random.nextFloat() * screenHeight
            val count = (audioEnergy * 3).toInt().coerceIn(1, 10)
            repeat(count) { spawnParticle(x, y, true) }
        }

        // Update particles
        val iterator = particles.listIterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update(dt, gravityX, gravityY, screenWidth, screenHeight, touchPoints, audioEnergy, bassEnergy)
            if (p.isDead) {
                iterator.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Clear with background color based on time of day
        val bgColor = palette.backgroundColor
        canvas.drawColor(bgColor)

        // Draw trails first (behind particles)
        for (p in particles) {
            p.drawTrail(canvas, trailPaint)
        }

        // Draw particles
        for (p in particles) {
            p.draw(canvas, paint)
        }

        // Draw touch ripple effect
        for (tp in touchPoints) {
            drawTouchRipple(canvas, tp)
        }
    }

    private fun drawTouchRipple(canvas: Canvas, tp: TouchPoint) {
        val ripplePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.HSVToColor(floatArrayOf(palette.primaryHue, 0.6f, 1f))
            alpha = (200 * tp.pressure).toInt().coerceIn(50, 255)
        }
        val radius = 40f * tp.pressure + (System.currentTimeMillis() % 1000) / 1000f * 80f
        canvas.drawCircle(tp.x, tp.y, radius, ripplePaint)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle orientation changes gracefully
    }

    companion object {
        @JvmStatic
        fun create(context: Context): ParticleView {
            return ParticleView(context).apply {
                layoutParams = View.LayoutParams(View.LayoutParams.MATCH_PARENT, View.LayoutParams.MATCH_PARENT)
            }
        }
    }
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val hue: Float,
    val saturation: Float,
    val brightness: Float
) {
    var trailX = Array(8) { x }
    var trailY = Array(8) { y }
    var trailIndex = 0

    val isDead: Boolean get() = life <= 0f

    fun update(
        dt: Float,
        gravityX: Float,
        gravityY: Float,
        screenWidth: Float,
        screenHeight: Float,
        touchPoints: List<TouchPoint>,
        audioEnergy: Float,
        bassEnergy: Float
    ) {
        life -= dt

        // Store trail position
        trailX[trailIndex] = x
        trailY[trailIndex] = y
        trailIndex = (trailIndex + 1) % trailX.size

        // Apply gravity
        vx += gravityX * dt * 50f
        vy += gravityY * dt * 50f

        // Audio reactivity - pulse with bass
        if (bassEnergy > 1f) {
            val pulse = sin(life * 10f + bassEnergy) * bassEnergy * 5f
            vx += cos(life * 3f) * pulse
            vy += sin(life * 3f) * pulse
        }

        // Touch attraction
        for (tp in touchPoints) {
            val dx = tp.x - x
            val dy = tp.y - y
            val distSq = dx * dx + dy * dy
            val dist = sqrt(max(distSq, 1f))
            if (dist < 200f) {
                val force = (200f - dist) / 200f * tp.pressure * 500f
                vx += (dx / dist) * force * dt
                vy += (dy / dist) * force * dt
            }
        }

        // Audio energy adds randomness
        if (audioEnergy > 0.5f) {
            vx += (Random.nextFloat() - 0.5f) * audioEnergy * 20f * dt
            vy += (Random.nextFloat() - 0.5f) * audioEnergy * 20f * dt
        }

        // Velocity damping
        vx *= 0.99f
        vy *= 0.99f

        // Update position
        x += vx * dt
        y += vy * dt

        // Screen wrapping with fade
        if (x < -50f) x = screenWidth + 50f
        if (x > screenWidth + 50f) x = -50f
        if (y < -50f) y = screenHeight + 50f
        if (y > screenHeight + 50f) y = -50f
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val alpha = (255 * (life / maxLife)).toInt().coerceIn(0, 255)
        val currentSize = size * (life / maxLife).coerceIn(0.2f, 1f)
        val color = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, brightness))
        paint.color = color
        canvas.drawCircle(x, y, currentSize, paint)

        // Inner glow
        val glowPaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            alpha = (alpha * 0.5f).toInt()
        }
        canvas.drawCircle(x, y, currentSize * 1.5f, glowPaint)
    }

    fun drawTrail(canvas: Canvas, paint: Paint) {
        for (i in 0 until trailX.size) {
            val idx = (trailIndex + i) % trailX.size
            val nextIdx = (idx + 1) % trailX.size
            val alpha = (100 * (i.toFloat() / trailX.size) * (life / maxLife)).toInt().coerceIn(0, 100)
            if (alpha > 5) {
                paint.color = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, brightness))
                paint.strokeWidth = size * 0.5f * (i.toFloat() / trailX.size)
                canvas.drawLine(trailX[idx], trailY[idx], trailX[nextIdx], trailY[nextIdx], paint)
            }
        }
    }
}

data class TouchPoint(
    val x: Float,
    val y: Float,
    val pressure: Float
)

class Palette {
    var primaryHue = 200f
    var saturation = 0.7f
    var brightness = 0.9f
    var backgroundColor = Color.BLACK

    private val dawn = floatArrayOf(20f, 30f, 40f, 50f)      // Warm oranges/pinks
    private val day = floatArrayOf(190f, 200f, 210f, 220f)     // Cool blues
    private val dusk = floatArrayOf(20f, 30f, 350f, 10f)       // Purples/pinks
    private val night = floatArrayOf(240f, 260f, 280f, 300f)   // Deep blues/purples

    fun updateForHour(hour: Int) {
        when {
            hour in 5..9 -> { // Dawn/Morning
                primaryHue = dawn[Random.nextInt(dawn.size)]
                saturation = 0.8f
                brightness = 0.95f
                backgroundColor = Color.HSVToColor(floatArrayOf(primaryHue, 0.15f, 0.1f))
            }
            hour in 10..16 -> { // Day
                primaryHue = day[Random.nextInt(day.size)]
                saturation = 0.7f
                brightness = 0.9f
                backgroundColor = Color.HSVToColor(floatArrayOf(primaryHue, 0.05f, 0.05f))
            }
            hour in 17..20 -> { // Dusk/Evening
                primaryHue = dusk[Random.nextInt(dusk.size)]
                saturation = 0.85f
                brightness = 0.9f
                backgroundColor = Color.HSVToColor(floatArrayOf(primaryHue, 0.1f, 0.08f))
            }
            else -> { // Night
                primaryHue = night[Random.nextInt(night.size)]
                saturation = 0.6f
                brightness = 0.8f
                backgroundColor = Color.HSVToColor(floatArrayOf(primaryHue, 0.03f, 0.02f))
            }
        }
    }

    fun randomHue(): Float {
        // Slight variation around primary hue
        return (primaryHue + Random.nextFloat() * 30f - 15f).coerceIn(0f, 360f)
    }
}