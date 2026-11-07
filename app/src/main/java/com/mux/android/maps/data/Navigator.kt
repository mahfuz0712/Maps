package com.mux.android.maps.data

import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mux.android.maps.NavService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Turn-by-turn guidance: follows the live location along the route, speaks the next maneuver
 * (Android's built-in text-to-speech), notices when you leave the route and asks for a new one.
 */
object Navigator {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var active by mutableStateOf(false)
        private set
    var route by mutableStateOf<Route?>(null)
        private set
    var destination by mutableStateOf<Place?>(null)
        private set
    var mode = "car"
        private set

    var progress by mutableDoubleStateOf(0.0)
        private set
    var stepIndex by mutableIntStateOf(0)
        private set
    var distToNext by mutableDoubleStateOf(0.0)
        private set
    var remaining by mutableDoubleStateOf(0.0)
        private set
    var remainingTime by mutableDoubleStateOf(0.0)
        private set
    var speed by mutableFloatStateOf(0f)
        private set
    var rerouting by mutableStateOf(false)
        private set
    var arrived by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
    var offRoute by mutableStateOf(false)
        private set
    var note by mutableStateOf("")
        private set

    private var lastIdx = 0
    private var offCount = 0
    private var lastReroute = 0L
    private val said = HashSet<Int>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    val next: Step? get() = route?.steps?.getOrNull(stepIndex + 1)

    private val listener: (Location) -> Unit = { onLocation(it) }

    fun start(r: Route, dest: Place, m: String) {
        route = r
        destination = dest
        mode = m
        progress = 0.0
        stepIndex = 0
        lastIdx = 0
        offCount = 0
        arrived = false
        offRoute = false
        rerouting = false
        note = ""
        said.clear()
        remaining = r.distance
        remainingTime = r.duration
        distToNext = r.steps.firstOrNull()?.distance ?: r.distance
        active = true
        Loc.start()
        Loc.addListener(listener)
        initTts()
        NavService.start(app)
        speak("Starting navigation. " + (r.steps.firstOrNull()?.let { Routing.text(it) } ?: ""))
        Loc.fix?.let { onLocation(it) }
    }

    fun stop() {
        active = false
        route = null
        destination = null
        arrived = false
        Loc.removeListener(listener)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        ttsReady = false
    }

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                try {
                    tts?.language = Locale.US
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun speak(text: String) {
        if (muted || !AppSettings.voice || !ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
        } catch (e: Exception) {
        }
    }

    fun repeatInstruction() {
        val n = next ?: return
        speak("In ${Fmt.speak(distToNext)}, ${Routing.text(n)}")
    }

    private fun onLocation(l: Location) {
        val r = route ?: return
        if (!active || arrived) return
        speed = if (l.hasSpeed()) l.speed else 0f

        val p = Geo.project(r, l.latitude, l.longitude, lastIdx)
        val limit = (35.0 + l.accuracy.coerceAtMost(40f)).coerceAtLeast(45.0)
        if (p.dist > limit) {
            offCount++
            if (offCount >= 3 || p.dist > 150.0) {
                offRoute = true
                reroute(l)
            }
            return
        }
        offCount = 0
        offRoute = false
        lastIdx = p.index
        progress = p.along
        remaining = (r.length - progress).coerceAtLeast(0.0)
        remainingTime = if (r.length > 0) r.duration * (remaining / r.length) else 0.0

        // which step are we on?
        var i = stepIndex.coerceAtMost(r.steps.size - 1).coerceAtLeast(0)
        while (i < r.steps.size - 1 && progress >= r.stepEnd[i] - 3.0) i++
        stepIndex = i
        distToNext = (r.stepEnd.getOrElse(i) { r.length } - progress).coerceAtLeast(0.0)

        val nxt = next
        if (nxt != null && !muted) {
            val levels = if (mode == "car") listOf(600.0, 200.0) else listOf(120.0, 40.0)
            for ((k, th) in levels.withIndex()) {
                if (distToNext <= th && said.add(stepIndex * 10 + k)) {
                    speak(if (k == 0) "In ${Fmt.speak(distToNext)}, ${Routing.text(nxt)}" else Routing.text(nxt))
                    break
                }
            }
        }

        val arriveAt = if (mode == "car") 25.0 else 12.0
        if (remaining <= arriveAt) {
            arrived = true
            speak("You have arrived at your destination")
        }
    }

    private fun reroute(l: Location) {
        val d = destination ?: return
        if (rerouting) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastReroute < 8000) return
        lastReroute = now
        rerouting = true
        note = "REROUTING..."
        speak("Rerouting")
        scope.launch {
            try {
                val list = Routing.route(l.latitude, l.longitude, d.lat, d.lon, mode)
                val best = list.firstOrNull()
                if (best != null && active) {
                    route = best
                    lastIdx = 0
                    offCount = 0
                    offRoute = false
                    stepIndex = 0
                    progress = 0.0
                    said.clear()
                    remaining = best.distance
                    remainingTime = best.duration
                    distToNext = best.steps.firstOrNull()?.distance ?: best.distance
                    note = ""
                    MapState.navRouteChanged++
                }
            } catch (e: Exception) {
                note = "REROUTE FAILED - CHECK INTERNET"
            }
            rerouting = false
        }
    }
}
