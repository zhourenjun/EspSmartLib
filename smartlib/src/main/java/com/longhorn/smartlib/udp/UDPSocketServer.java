package com.longhorn.smartlib.udp;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.longhorn.smartlib.util.LogUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;

public class UDPSocketServer {

    private DatagramPacket mReceivePacket;
    private DatagramSocket mServerSocket;
    private Context mContext;
    private WifiManager.MulticastLock mLock;
    private final byte[] buffer;
    private volatile boolean mIsClosed;

    private synchronized void acquireLock() {
        if (mLock != null && !mLock.isHeld()) {
            mLock.acquire();
        }
    }

    private synchronized void releaseLock() {
        if (mLock != null && mLock.isHeld()) {
            try {
                mLock.release();
            } catch (Throwable th) {

            }
        }
    }
    public UDPSocketServer(int port, int socketTimeout, Context context) {
        mContext = context;
        buffer = new byte[64];
        mReceivePacket = new DatagramPacket(buffer, 64);
        try {
            mServerSocket = new DatagramSocket(port);
            mServerSocket.setSoTimeout(socketTimeout);
            mIsClosed = false;
            WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            mLock = manager.createMulticastLock("test wifi");
            LogUtil.e( "mServerSocket is created, socket read timeout: " + socketTimeout + ", port: " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set the socket timeout in milliseconds
     */
    public boolean setSoTimeout(int timeout) {
        try {
            mServerSocket.setSoTimeout(timeout);
            return true;
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Receive one byte from the port and convert it into String
     */
    public byte receiveOneByte() {
        LogUtil.e( "receiveOneByte() entrance");
        try {
            acquireLock();
            mServerSocket.receive(mReceivePacket);
            LogUtil.e( "receive: " + (0 + mReceivePacket.getData()[0]));
            return mReceivePacket.getData()[0];
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Byte.MIN_VALUE;
    }

    /**
     * Receive specific length bytes from the port and convert it into String
     */
    public byte[] receiveSpecLenBytes(int len) {
        try {
            acquireLock();
            mServerSocket.receive(mReceivePacket);
            byte[] recDatas = Arrays.copyOf(mReceivePacket.getData(), mReceivePacket.getLength());
            LogUtil.e( "received len : " + recDatas.length);

            if (recDatas.length != len) {
                LogUtil.e( "received len is different from specific len, return null");
                return null;
            }
            return recDatas;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void interrupt() {
        LogUtil.e( "UDPSocketServer is interrupt");
        close();
    }

    public synchronized void close() {
        if (!mIsClosed) {
            LogUtil.e( "mServerSocket is closed");
            mServerSocket.close();
            releaseLock();
            mIsClosed = true;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

}
