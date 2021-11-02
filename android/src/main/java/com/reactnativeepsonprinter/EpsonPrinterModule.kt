package com.reactnativeepsonprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import com.epson.epos2.Epos2Exception
import com.epson.epos2.discovery.DeviceInfo
import com.epson.epos2.discovery.Discovery
import com.epson.epos2.discovery.FilterOption
import com.facebook.react.bridge.*
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList

class EpsonPrinterModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val disposable = CompositeDisposable()

    private fun stopNetworkDiscovery() {
        while (true) {
            try {
                Discovery.stop()
                break
            } catch (e: Epos2Exception) {
                if (e.errorStatus != Epos2Exception.ERR_PROCESSING) {
                    return
                }
            }
        }
    }

    override fun getName(): String {
        return "EpsonPrinter"
    }

    @ReactMethod
    fun discover(readableMap: ReadableMap, promise: Promise) {
        when (readableMap.getString("interface_type")) {
            "LAN" -> {
                stopNetworkDiscovery()

                val filterOption = FilterOption()
                filterOption.deviceType = Discovery.TYPE_PRINTER
                try {
                    val deviceInfoList = ArrayList<DeviceInfo>()
                    Discovery.start(reactApplicationContext, filterOption) { deviceInfo ->
                        UiThreadUtil.runOnUiThread {
                            if (deviceInfo.target.contains("TCP", true) &&
                                !deviceInfoList.any { it.ipAddress == deviceInfo.ipAddress }
                            ) {
                                deviceInfoList.add(deviceInfo)
                            }
                        }
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        val printerArray = Arguments.createArray()
                        for (i in 0 until deviceInfoList.size) {
                            val deviceInfo = deviceInfoList[i]
                            val printerData = Arguments.createMap()
                            printerData.putString("name", deviceInfo.deviceName)
                            printerData.putString("interface_type", "LAN")
                            printerData.putString("mac_address", deviceInfo.macAddress)
                            printerData.putString("target", deviceInfo.ipAddress)
                            printerArray.pushMap(printerData)
                        }
                        promise.resolve(printerArray)
                    }, 5000)
                } catch (e: Exception) {
                    val status = (e as Epos2Exception).errorStatus
                    promise.reject("Discovery Error", status.toString())
                }
            }
            "Bluetooth" -> {
                try {
                    val rxBluetooth = RxBluetooth(reactApplicationContext)
                    if (!rxBluetooth.isBluetoothEnabled) {
                        promise.reject("Discovery Error", "Please enable Bluetooth")
                    } else {
                        val bluetoothDeviceList = ArrayList<BluetoothDevice>()
                        disposable.add(rxBluetooth.observeDevices()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe { bluetoothDevice ->
                                if (!bluetoothDeviceList.any { it.address == bluetoothDevice.address }) {
                                    bluetoothDeviceList.add(bluetoothDevice)
                                }
                            })
                        if (!PermissionUtil.hasLocationPermission(reactApplicationContext)) {
                            promise.reject("Discovery Error", "Please enable Location")
                        } else {
                            rxBluetooth.startDiscovery()
                            Handler(Looper.getMainLooper()).postDelayed({
                                val printerArray = Arguments.createArray()
                                for (i in 0 until bluetoothDeviceList.size) {
                                    val bluetoothDevice = bluetoothDeviceList[i]
                                    val printerData = Arguments.createMap()
                                    printerData.putString("name", bluetoothDevice.name)
                                    printerData.putString("interface_type", "Bluetooth")
                                    printerData.putString("mac_address", bluetoothDevice.address)
                                    printerData.putString("target", bluetoothDevice.address)
                                    printerArray.pushMap(printerData)
                                }
                                promise.resolve(printerArray)
                            }, 5000)
                        }
                    }
                } catch (e: Exception) {
                    promise.reject("Discovery Error", e.message)
                }
            }
            else -> {
                promise.reject("Discovery Error", "Please select either LAN or Bluetooth interface")
            }
        }
    }

    @ReactMethod
    fun print(readableMap: ReadableMap, promise: Promise) {
        val printer = readableMap.getMap("printer")
        val interfaceType = printer?.getString("interface_type") ?: ""
        val target = printer?.getString("target") ?: ""
        val data = readableMap.getString("data") ?: ""
        val receiptCopyCount = readableMap.getInt("receipt_copy_count")
        val fontSize: ByteArray? = when (readableMap.getString("font_size")) {
            "Small" -> PrintUtil.FONT_SIZE_SMALL
            "Regular" -> PrintUtil.FONT_SIZE_REGULAR
            "Medium" -> PrintUtil.FONT_SIZE_MEDIUM
            "Large" -> PrintUtil.FONT_SIZE_LARGE
            else -> null
        }
        when (interfaceType) {
            "LAN" -> {
                disposable.add(Completable.fromAction {
                    val socket = Socket(target, 9100)
                    socket.getOutputStream().write(fontSize)
                    socket.getOutputStream().flush()
                    for (i in 0 until receiptCopyCount) {
                        socket.getOutputStream().write(data.toByteArray())
                        socket.getOutputStream().flush()
                        socket.getOutputStream().write("\n".toByteArray())
                        socket.getOutputStream().flush()
                        socket.getOutputStream().write(PrintUtil.CUT_PAPER)
                        socket.getOutputStream().flush()
                    }
                    socket.getOutputStream().close()
                    socket.close()
                }
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        promise.resolve("Print success")
                    }, {
                        promise.reject("Print Error", it)
                    }))
            }
            "Bluetooth" -> {
                val bluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(target)
                disposable.add(Completable.fromAction {
                    var socket =
                        bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    try {
                        socket.connect()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        socket = bluetoothDevice.javaClass.getMethod(
                            "createRfcommSocket", Int::class.javaPrimitiveType
                        ).invoke(bluetoothDevice, 1) as BluetoothSocket
                        socket.connect()
                    }
                    socket.outputStream.write(fontSize)
                    for (i in 0 until receiptCopyCount) {
                        socket.outputStream.write(data.toByteArray())
                        socket.outputStream.write("\n".toByteArray())
                        socket.outputStream.write(PrintUtil.CUT_PAPER)
                    }
                    Thread.sleep(1000)
                    socket.outputStream.close()
                    socket.close()
                }
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        promise.resolve("Print success")
                    }, {
                        promise.reject("Print Error", it)
                    }))
            }
            else -> {
                promise.reject("Print Error", "Interface type not supported")
            }
        }
    }
}
