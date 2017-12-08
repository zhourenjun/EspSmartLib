package com.longhorn.smartlib.util


import com.longhorn.smartlib.IOTAddress
import java.io.IOException
import java.net.*
import java.util.*

object UdpBroadcastUtil {

    private val DATA = "Are You Espressif IOT Smart Device?"
    private val IOT_DEVICE_PORT = 1025
    private val SO_TIMEOUT = 1000
    private val MAX = 5
    private val RECEIVE_LEN = 1024
    private val IOT_APP_PORT = 4025
    private var broadcastAddress: InetAddress? = null

    init {
        try {
            broadcastAddress = InetAddress.getByName("255.255.255.255")
        } catch (e: UnknownHostException) {
            LogUtil.e(e)
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
                LogUtil.e(e)
            }
        }
        do {
            try {
                hostPort = 1024 + Random().nextInt(65536 - 1024)
                socket = DatagramSocket(hostPort)
                success = true
            } catch (e: SocketException) {
                LogUtil.e(e)
            }
        } while (!success)
        return socket
    }

    private fun discoverDevices(bSsid: String?): List<IOTAddress>? {
        val responseList = ArrayList<IOTAddress>()
        var socket: DatagramSocket? = null
        var receiveContent: String
        var hostname: String
        var responseAddr: InetAddress
        var responseBSSID: String
        val realData: String = if (bSsid != null) DATA + " " + bSsid else DATA

        try {
            // 分配端口
            socket = allocPort(IOT_APP_PORT)
            // 设置超时
            socket.soTimeout = SO_TIMEOUT
            // 广播内容  DatagramPacket 来包装需要发送或者接收到的数据
            val packSend = DatagramPacket(realData.toByteArray(), realData.length, broadcastAddress, IOT_DEVICE_PORT)
            socket.send(packSend)   //DatagramSocket 的 send()方法和 receive()方法来发送和接收数据
            /**
             * 在创建 DatagramPacket 实例时，要注意：如果该实例用来包装待接收的数据，则不指定数据来源的远程主机和端口，
             * 只需指定一个缓存数据的 byte 数组即可（在调用 receive()方法接收到数据后，源地址和端口等信息会自动
             * 包含在 DatagramPacket 实例中 ）而如果该实例用来包装待发送的数据，则要指定要发 送到的目的主机和端口
             */
            //在接收了数据之后，其内部消息长度值会变为实际接收的消息的字节数
            var packReceive = DatagramPacket(ByteArray(RECEIVE_LEN), RECEIVE_LEN)
            do {
                socket.receive(packReceive) //会阻塞直至超时
                receiveContent = String(packReceive.data, packReceive.offset, packReceive.length)
                if (isValid(receiveContent)) {
                    LogUtil.e(receiveContent)//I'm Light.a0:20:a6:1b:ac:5c 192.168.1.100
                    hostname = filterIpAddress(receiveContent)
                    if (hostname == "0.0.0.0") continue

                    responseAddr = InetAddress.getByName(hostname)
                    responseBSSID = filterBssid(receiveContent)
                    val iotAddress = IOTAddress(responseBSSID, responseAddr)
                    if (!responseList.contains(iotAddress)) {
                        responseList.add(iotAddress)
                    }
                }
            } while (bSsid == null)
        } catch (e: SocketException) {
            LogUtil.e(e)
        } catch (e: IOException) {
            LogUtil.e(e)
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
