package org.reactnative.camera.events

import org.reactnative.camera.events.FacesDetectedEvent
import com.facebook.react.bridge.WritableArray
import org.reactnative.camera.CameraViewManager
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import androidx.core.util.Pools.SynchronizedPool
import com.facebook.react.uimanager.events.Event

class FacesDetectedEvent private constructor() : Event<FacesDetectedEvent>() {
    private var mData: WritableArray? = null
    private fun init(viewTag: Int, data: WritableArray) {
        super.init(viewTag)
        mData = data
    }

    /**
     * note(@sjchmiela)
     * Should the events about detected faces coalesce, the best strategy will be
     * to ensure that events with different faces count are always being transmitted.
     */
    override fun getCoalescingKey(): Short {
        return if (mData!!.size() > Short.MAX_VALUE) {
            Short.MAX_VALUE
        } else mData!!.size().toShort()
    }

    override fun getEventName(): String {
        return CameraViewManager.Events.EVENT_ON_FACES_DETECTED.toString()
    }

    override fun dispatch(rctEventEmitter: RCTEventEmitter) {
        rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
    }

    private fun serializeEventData(): WritableMap {
        val event = Arguments.createMap()
        event.putString("type", "face")
        event.putArray("faces", mData)
        event.putInt("target", viewTag)
        return event
    }

    companion object {
        private val EVENTS_POOL = SynchronizedPool<FacesDetectedEvent>(3)
        fun obtain(viewTag: Int, data: WritableArray): FacesDetectedEvent {
            var event = EVENTS_POOL.acquire()
            if (event == null) {
                event = FacesDetectedEvent()
            }
            event.init(viewTag, data)
            return event
        }
    }
}