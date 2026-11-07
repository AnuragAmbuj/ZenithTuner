package com.zenith.tuner.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Manages Bluetooth audio device detection and firmware companion app integration.
 */
object DeviceManager {

    private const val TAG = "DeviceManager"
    const val BOAT_HEARABLES_PACKAGE_1 = "com.imagine.hearables"
    const val BOAT_HEARABLES_PACKAGE_2 = "com.boat.hearables"

    data class BluetoothDeviceInfo(
        val isConnected: Boolean,
        val deviceName: String,
        val isZenith: Boolean
    )

    /**
     * Inspects active Bluetooth audio devices to check if boAt Nirvana Zenith is connected.
     */
    fun getConnectedDeviceInfo(context: Context): BluetoothDeviceInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return BluetoothDeviceInfo(
                    isConnected = false,
                    deviceName = "Permission Required",
                    isZenith = false
                )
            }
        }

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: return BluetoothDeviceInfo(false, "No Bluetooth", false)

            if (!adapter.isEnabled) {
                return BluetoothDeviceInfo(false, "Bluetooth Off", false)
            }

            // Check bonded devices that are connected
            val bondedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
            if (bondedDevices != null) {
                for (device in bondedDevices) {
                    val name = device.name ?: ""
                    if (name.contains("Zenith", ignoreCase = true) || name.contains("Nirvana", ignoreCase = true)) {
                        return BluetoothDeviceInfo(
                            isConnected = true,
                            deviceName = name,
                            isZenith = true
                        )
                    }
                }

                // If bonded devices exist but not named Zenith, check first audio device
                bondedDevices.firstOrNull()?.let { first ->
                    val name = first.name ?: "Bluetooth Device"
                    val isZenith = name.contains("Zenith", ignoreCase = true)
                    return BluetoothDeviceInfo(
                        isConnected = true,
                        deviceName = name,
                        isZenith = isZenith
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking bluetooth devices", e)
        }

        return BluetoothDeviceInfo(
            isConnected = false,
            deviceName = "No Device Connected",
            isZenith = false
        )
    }

    /**
     * Tries launching the official boAt Hearables app.
     * If not installed, falls back to Google Play Store or Bluetooth settings.
     */
    fun launchBoatHearables(context: Context): Boolean {
        val pm = context.packageManager

        // Try package 1
        var launchIntent = pm.getLaunchIntentForPackage(BOAT_HEARABLES_PACKAGE_1)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return true
        }

        // Try package 2
        launchIntent = pm.getLaunchIntentForPackage(BOAT_HEARABLES_PACKAGE_2)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return true
        }

        // If not installed, open Google Play Store search for boAt Hearables
        try {
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=boAt+Hearables")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
            return true
        } catch (e: Throwable) {
            // Fallback to web Play Store
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=boAt+Hearables&c=apps")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                return true
            } catch (e2: Throwable) {
                // Fallback to system Bluetooth settings
                openBluetoothSettings(context)
                return false
            }
        }
    }

    /**
     * Opens system Bluetooth settings for pairing and codec configuration.
     */
    fun openBluetoothSettings(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
