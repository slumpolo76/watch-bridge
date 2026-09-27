package com.watchbridge.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import com.watchbridge.R

/**
 * GATT server, advertising e foreground service.
 */
class BLEPeripheralService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private val chunkAssembler = BLEProtocol.ChunkAssembler()

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            Log.d(TAG, "onConnectionStateChange device=${device.address} state=$newState status=$status")
            if (!connected) {
                chunkAssembler.reset()
            }
            mainHandler.post {
                BLEConnectionState.setWatchConnected(connected)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid != BLEProtocol.CHARACTERISTIC_TX) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            val complete = chunkAssembler.add(value) ?: return
            Log.d(TAG, "messaggio ricevuto: ${String(complete, Charsets.UTF_8)}")

            if (BLEProtocol.parseType(complete) == "ping") {
                Log.d(TAG, "ping ricevuto, invio pong")
                notifyChunked(device, BLEProtocol.pongMessage())
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            Log.d(TAG, "onDescriptorWriteRequest device=${device.address}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "advertising avviato")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "advertising fallito: $errorCode")
        }
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!startBleStack()) {
            Log.e(TAG, "impossibile avviare BLE")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        BLEConnectionState.setWatchConnected(false)
        super.onDestroy()
    }

    private fun startBleStack(): Boolean {
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "BluetoothAdapter assente")
            return false
        }
        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth disattivato")
            return false
        }

        if (!openGattServer()) {
            return false
        }
        startAdvertising(adapter)
        return true
    }

    private fun openGattServer(): Boolean {
        val manager = bluetoothManager ?: return false
        gattServer?.close()
        gattServer = manager.openGattServer(this, gattServerCallback) ?: run {
            Log.e(TAG, "openGattServer fallito")
            return false
        }

        val service = BluetoothGattService(
            BLEProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        val tx = BluetoothGattCharacteristic(
            BLEProtocol.CHARACTERISTIC_TX,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        rxCharacteristic = BluetoothGattCharacteristic(
            BLEProtocol.CHARACTERISTIC_RX,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CLIENT_CONFIG_DESCRIPTOR,
                    BluetoothGattDescriptor.PERMISSION_READ or
                        BluetoothGattDescriptor.PERMISSION_WRITE,
                ),
            )
        }

        service.addCharacteristic(tx)
        service.addCharacteristic(rxCharacteristic)

        val added = gattServer?.addService(service) == true
        if (!added) {
            Log.e(TAG, "addService fallito")
        } else {
            Log.d(TAG, "GATT service registrato")
        }
        return added
    }

    private fun startAdvertising(adapter: BluetoothAdapter) {
        advertiser = adapter.bluetoothLeAdvertiser
        val leAdvertiser = advertiser ?: run {
            Log.e(TAG, "BluetoothLeAdvertiser non disponibile")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BLEProtocol.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

        leAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising: ${e.message}")
        }
    }

    private fun notifyChunked(device: BluetoothDevice, payload: ByteArray) {
        val characteristic = rxCharacteristic ?: return
        val server = gattServer ?: return
        for (chunk in BLEProtocol.chunk(payload)) {
            characteristic.value = chunk
            val ok = server.notifyCharacteristicChanged(device, characteristic, false)
            if (!ok) {
                Log.w(TAG, "notifyCharacteristicChanged fallito")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_foreground_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "WatchBridge"

        private const val CHANNEL_ID = "watchbridge_ble"
        private const val NOTIFICATION_ID = 1001

        private val CLIENT_CONFIG_DESCRIPTOR: java.util.UUID =
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun start(context: Context) {
            val intent = Intent(context, BLEPeripheralService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
