package com.jarvis.assistant.commands

import android.content.Context
import android.hardware.camera2.CameraManager

/** Controls the device flashlight/torch. No special permission needed on API 23+. */
class FlashlightController(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String? by lazy {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun turnOn(): Boolean = setTorch(true)
    fun turnOff(): Boolean = setTorch(false)

    private fun setTorch(on: Boolean): Boolean {
        val id = cameraId ?: return false
        return try {
            cameraManager.setTorchMode(id, on)
            true
        } catch (e: Exception) {
            false
        }
    }
}
