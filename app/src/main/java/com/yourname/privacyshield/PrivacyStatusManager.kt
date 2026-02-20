package com.yourname.privacyshield

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PrivacyStatusManager {
    private val _isCameraInUse = MutableStateFlow(false)
    val isCameraInUse = _isCameraInUse.asStateFlow()

    private val _isMicInUse = MutableStateFlow(false)
    val isMicInUse = _isMicInUse.asStateFlow()

    private val _isLocationInUse = MutableStateFlow(false)
    val isLocationInUse = _isLocationInUse.asStateFlow()

    fun updateCameraStatus(inUse: Boolean) {
        _isCameraInUse.value = inUse
    }

    fun updateMicStatus(inUse: Boolean) {
        _isMicInUse.value = inUse
    }

    fun updateLocationStatus(inUse: Boolean) {
        _isLocationInUse.value = inUse
    }
}
