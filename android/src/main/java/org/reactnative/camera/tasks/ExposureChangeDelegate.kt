package org.reactnative.camera.tasks

import com.facebook.react.bridge.WritableMap

interface ExposureChangeDelegate {
    fun onExposeChange(response: WritableMap)
}