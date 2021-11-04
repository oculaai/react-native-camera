package org.reactnative.camera.events

//import org.reactnative.camera.CameraViewManager.Events.toString
//import org.reactnative.camera.events.RecordingEndEvent
import org.reactnative.camera.CameraViewManager
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import androidx.core.util.Pools.SynchronizedPool
import com.facebook.react.uimanager.events.Event

class RecordingEndEvent private constructor() : Event<RecordingEndEvent>() {
    override fun getCoalescingKey(): Short {
        return 0
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_ON_RECORDING_END.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
    }

    private fun serializeEventData(): WritableMap {
        return Arguments.createMap()
    }

    companion object {
        private val EVENTS_POOL = SynchronizedPool<RecordingEndEvent>(3)
        fun obtain(viewTag: Int): RecordingEndEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = RecordingEndEvent()
            }
            event.init(viewTag)
            return event
        }
    }
}