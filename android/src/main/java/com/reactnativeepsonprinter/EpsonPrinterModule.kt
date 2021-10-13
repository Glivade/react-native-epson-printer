package com.reactnativeepsonprinter

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
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
    ReactContextBaseJavaModule(reactContext), ActivityEventListener {

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

    override fun onActivityResult(p0: Activity?, p1: Int, p2: Int, p3: Intent?) {

    }

    override fun onNewIntent(p0: Intent?) {

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
                    Discovery.start(reactApplicationContext, filterOption) {
                        UiThreadUtil.runOnUiThread {
                            if (it.target.contains("TCP", true)) {
                                deviceInfoList.add(it)
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
                            printerData.putString("mac", deviceInfo.macAddress)
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
                            .subscribe {
                                bluetoothDeviceList.add(it)
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
                                    printerData.putString("mac", bluetoothDevice.address)
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
        when (interfaceType) {
            "LAN" -> {
                disposable.add(Completable.fromAction {
                    val socket = Socket(target, 9100)
                    socket.getOutputStream().write(PrintUtil.FONT_SIZE_NORMAL)
                    socket.getOutputStream().flush()
                    socket.getOutputStream().write(data.toByteArray())
                    socket.getOutputStream().flush()
                    socket.getOutputStream().write("\n".toByteArray())
                    socket.getOutputStream().flush()
                    socket.getOutputStream().write(PrintUtil.CUT_PAPER)
                    socket.getOutputStream().flush()
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
                    val socket =
                        bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    socket.connect()
                    socket.outputStream.write(PrintUtil.FONT_SIZE_NORMAL)
                    socket.outputStream.flush()
                    socket.outputStream.write(data.toByteArray())
                    socket.outputStream.flush()
                    socket.outputStream.write("\n".toByteArray())
                    socket.outputStream.flush()
                    socket.outputStream.write(PrintUtil.CUT_PAPER)
                    socket.outputStream.flush()
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
