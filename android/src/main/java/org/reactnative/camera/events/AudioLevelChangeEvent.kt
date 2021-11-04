package org.reactnative.camera.events

import com.facebook.react.bridge.WritableMap
import org.reactnative.camera.CameraViewManager
import com.facebook.react.uimanager.events.RCTEventEmitter
import androidx.core.util.Pools.SynchronizedPool
import com.facebook.react.uimanager.events.Event

class AudioLevelChangeEvent private constructor() : Event<RecordingStartEvent>() {
    private var audioLevelDataSource: WritableMap? = null
    private fun init(viewTag: Int, data: WritableMap) {
        super.init(viewTag)
        audioLevelDataSource = data
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_ON_AUDIO_LEVEL_CHANGE.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, audioLevelDataSource)
    }

    companion object {
        private val EVENTS_POOL = SynchronizedPool<AudioLevelChangeEvent>(3)
        fun obtain(viewTag: Int, response: WritableMap): AudioLevelChangeEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = AudioLevelChangeEvent()
            }
            event.init(viewTag, response)
            return event
        }
    }
}