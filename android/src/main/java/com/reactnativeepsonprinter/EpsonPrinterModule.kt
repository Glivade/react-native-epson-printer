package com.reactnativeepsonprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import com.epson.epos2.Epos2Exception
import com.epson.epos2.discovery.DeviceInfo
import com.epson.epos2.discovery.Discovery
import com.epson.epos2.discovery.FilterOption
import com.facebook.react.bridge.*
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.image.Bitonal
import com.github.anastaciocintra.escpos.image.BitonalOrderedDither
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.escpos.image.RasterBitImageWrapper
import com.github.anastaciocintra.output.TcpIpOutputStream
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList
import java.nio.charset.*;

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
                    for (i in 0 until receiptCopyCount) {
                        socket.getOutputStream().write(data.toByteArray(Charsets.UTF_8))
                        socket.getOutputStream().write("\n".toByteArray(Charsets.UTF_8))
                        socket.getOutputStream().write(PrintUtil.CUT_PAPER)
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

    @ReactMethod
    fun printImage(readableMap: ReadableMap, promise: Promise) {
        val printer = readableMap.getMap("printer")
        val interfaceType = printer?.getString("interface_type") ?: ""
        val target = printer?.getString("target") ?: ""
        val receiptCopyCount = readableMap.getInt("receipt_copy_count")

        @SuppressLint("InflateParams")
        val orderReceiptView = LayoutInflater.from(reactApplicationContext)
            .inflate(R.layout.receipt_order, null)
        layoutAndMeasureView(orderReceiptView, 255)
        val orderReceiptBitmap = Bitmap.createBitmap(
            orderReceiptView.width,
            orderReceiptView.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(orderReceiptBitmap)
        orderReceiptView.draw(canvas)

        val options = BitmapFactory.Options()
        options.inScaled = false
        val imageWrapper = RasterBitImageWrapper()
        val algorithm: Bitonal = BitonalOrderedDither()
        val escposImage =
            EscPosImage(CoffeeImageAndroidImpl(orderReceiptBitmap), algorithm)
        when (interfaceType) {
            "LAN" -> {
                disposable.add(Completable.fromAction {
                    TcpIpOutputStream(target, 9100).use { stream ->
                        val escpos = EscPos(stream)
                        escpos.write(imageWrapper, escposImage)
                        escpos.feed(1).cut(EscPos.CutMode.FULL)
                    }
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
                    for (i in 0 until receiptCopyCount) {
                        socket.outputStream.write(imageWrapper.getBytes(escposImage))
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

    private fun layoutAndMeasureView(view: View, viewWidth: Int) {
        val measuredWidth = View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY)
        val measuredHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(measuredWidth, measuredHeight)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.requestLayout()
    }
}
