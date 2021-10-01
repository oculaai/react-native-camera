package org.reactnative.camera.events

import org.reactnative.camera.events.CameraReadyEvent
import org.reactnative.camera.CameraViewManager
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import androidx.core.util.Pools.SynchronizedPool
import com.facebook.react.uimanager.events.Event

class CameraReadyEvent private constructor() : Event<CameraReadyEvent>() {
    override fun getCoalescingKey(): Short {
        return 0
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_CAMERA_READY.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
    }

    private fun serializeEventData(): WritableMap {
        return Arguments.createMap()
    }

    companion object {
        private val EVENTS_POOL = SynchronizedPool<CameraReadyEvent>(3)
        @JvmStatic
        fun obtain(viewTag: Int): CameraReadyEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = CameraReadyEvent()
            }
            event.init(viewTag)
            return event
        }
    }
}