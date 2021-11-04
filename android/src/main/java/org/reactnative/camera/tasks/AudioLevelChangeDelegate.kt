package org.reactnative.camera.tasks

import com.facebook.react.bridge.WritableMap

interface AudioLevelChangeDelegate {
    fun onAudioLevelChange(response: WritableMap)
}