package org.reactnative.camera.events

import androidx.core.util.Pools.SynchronizedPool
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import org.reactnative.camera.CameraViewManager

class ExposureChangeEvent: Event<ExposureChangeEvent>() {
    override fun getCoalescingKey(): Short {
        return 0
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_ON_EXPOSURE_CHANGE.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
    }

    private fun serializeEventData(): WritableMap {
        val event = Arguments.createMap()
        event.putDouble("exposureDuration", 0.0)
        event.putDouble("lensAperture", 0.0)
        event.putDouble("iso", 0.0)
        return event
    }

    companion object {
        private val EVENTS_POOL = SynchronizedPool<ExposureChangeEvent>(3)
        @JvmStatic
        fun obtain(viewTag: Int, data: WritableArray?): ExposureChangeEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = ExposureChangeEvent()
            }
            event.init(viewTag)
            return event
        }
    }
}