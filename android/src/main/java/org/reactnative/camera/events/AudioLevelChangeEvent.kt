package org.reactnative.camera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import org.reactnative.camera.CameraViewManager

class AudioLevelChangeEvent: Event<AudioLevelChangeEvent>() {
    override fun getCoalescingKey(): Short {
        return 0
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_ON_AUDIO_LEVEL_CHANGE.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
    }

    private fun serializeEventData(): WritableMap {
        return Arguments.createMap()
    }

    companion object {
        private val EVENTS_POOL = Pools.SynchronizedPool<AudioLevelChangeEvent>(3)
        @JvmStatic
        fun obtain(viewTag: Int): AudioLevelChangeEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = AudioLevelChangeEvent()
            }
            event.init(viewTag)
            return event
        }
    }
}