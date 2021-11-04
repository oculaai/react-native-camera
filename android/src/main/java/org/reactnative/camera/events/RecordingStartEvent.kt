package org.reactnative.camera.events

//import org.reactnative.camera.CameraViewManager.Events.toString
//import org.reactnative.camera.events.RecordingStartEvent
import com.facebook.react.bridge.WritableMap
import org.reactnative.camera.CameraViewManager
import com.facebook.react.uimanager.events.RCTEventEmitter
import androidx.core.util.Pools.SynchronizedPool
import com.facebook.react.uimanager.events.Event

class RecordingStartEvent private constructor() : Event<RecordingStartEvent>() {
    private var mResponse: WritableMap? = null
    private fun init(viewTag: Int, response: WritableMap) {
        super.init(viewTag)
        mResponse = response
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_ON_RECORDING_START.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, mResponse)
    }

    companion object {
        private val EVENTS_POOL = SynchronizedPool<RecordingStartEvent>(3)
        fun obtain(viewTag: Int, response: WritableMap): RecordingStartEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = RecordingStartEvent()
            }
            event.init(viewTag, response)
            return event
        }
    }
}