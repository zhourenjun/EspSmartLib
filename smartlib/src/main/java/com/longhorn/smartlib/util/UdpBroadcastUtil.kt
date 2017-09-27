package com.longhorn.smartlib.util


import com.longhorn.smartlib.IOTAddress
import java.io.IOException
import java.net.*
import java.util.*

object UdpBroadcastUtil {

    private val DATA = "Are You Espressif IOT Smart Device?"
    private val IOT_DEVICE_PORT = 1025
    private val SO_TIMEOUT = 6000
    private val RECEIVE_LEN = 64
    private val IOT_APP_PORT = 4025
    private var broadcastAddress: InetAddress? = null
    init {
        try {
            broadcastAddress = InetAddress.getByName("255.255.255.255")
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }
    }

    private fun allocPort(hostPort: Int): DatagramSocket {
        var hostPort = hostPort
        var success = false
        var socket = DatagramSocket()
        if (-1 < hostPort && hostPort < 65536) {
            try {
                socket = DatagramSocket(hostPort)
                success = true
                return socket
            } catch (e: SocketException) {
                e.printStackTrace()
            }

        }
        do {
            try {
                hostPort = 1024 + Random().nextInt(65536 - 1024)
                socket = DatagramSocket(hostPort)
                success = true
            } catch (e: SocketException) {
                e.printStackTrace()
            }

        } while (!success)
        return socket
    }

    private fun discoverDevices(bssid: String?): List<IOTAddress>? {
        val responseList = ArrayList<IOTAddress>()
        var socket: DatagramSocket? = null
        val buf_receive = ByteArray(RECEIVE_LEN)
        val pack: DatagramPacket
        var receiveContent: String
        var hostname: String
        var responseAddr: InetAddress
        var responseBSSID: String
        val realData: String = if (bssid != null) {
            DATA + " " + bssid
        } else {
            DATA
        }
        try {
            // 分配端口
            socket = allocPort(IOT_APP_PORT)
            // 设置超时
            socket.soTimeout = SO_TIMEOUT
            // 广播内容
            pack = DatagramPacket(realData.toByteArray(), realData.length, broadcastAddress, IOT_DEVICE_PORT)
            socket.send(pack)
            pack.data = buf_receive
            do {
                socket.receive(pack)
                receiveContent = String(pack.data, pack.offset, pack.length)
                if (isValid(receiveContent)) {
                    LogUtil.e(receiveContent)//I'm Light.a0:20:a6:1b:ac:5c 192.168.1.100
                    hostname = filterIpAddress(receiveContent)
                    if (hostname == "0.0.0.0") {
                        continue
                    }
                    responseAddr = InetAddress.getByName(hostname)
                    responseBSSID = filterBssid(receiveContent)
                    val iotAddress = IOTAddress(responseBSSID, responseAddr)
                    if (!responseList.contains(iotAddress)) {
                        responseList.add(iotAddress)
                    }
                }
            } while (bssid == null)
        } catch (e: SocketException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (socket != null) {
                socket.disconnect()
                socket.close()
            }
        }
        return responseList
    }

    fun discoverIOTDevices(): List<IOTAddress> {
        return discoverDevices(null) ?: emptyList()
    }

    private fun filterBssid(data: String): String {
        val dataSplitArray = data.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return dataSplitArray[1].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
    }

    private fun filterIpAddress(data: String): String {
        val dataSplitArray = data.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return dataSplitArray[dataSplitArray.size - 1]
    }

    private val DEVICE_PATTERN_TYPE = "^I'm ((\\w)+( )*)+\\."
    private val DEVICE_PATTERN_BSSID = "([0-9a-fA-F]{2}:){5}([0-9a-fA-F]{2} )"
    private val DEVICE_PATTERN_IP = "(\\d+\\.){3}(\\d+)"
    private val DEVICE_PATTERN = DEVICE_PATTERN_TYPE + DEVICE_PATTERN_BSSID + DEVICE_PATTERN_IP
    private fun isValid(data: String): Boolean {
        return data.matches(DEVICE_PATTERN.toRegex())
    }
}
