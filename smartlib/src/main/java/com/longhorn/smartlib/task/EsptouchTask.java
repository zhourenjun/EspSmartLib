package com.longhorn.smartlib.task;

import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;

import com.longhorn.smartlib.udp.UDPSocketClient;
import com.longhorn.smartlib.udp.UDPSocketServer;
import com.longhorn.smartlib.util.ByteUtil;
import com.longhorn.smartlib.util.EspNetUtil;
import com.longhorn.smartlib.util.LogUtil;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


public class EsptouchTask implements IEsptouchTask {

    /**
     * one indivisible data contain 3 9bits info
     */
    private static final int ONE_DATA_LEN = 3;
    private volatile List<IEsptouchResult> mEsptouchResultList;
    private volatile boolean mIsSuc = false;
    private volatile boolean mIsInterrupt = false;
    private volatile boolean mIsExecuted = false;
    private UDPSocketClient mSocketClient;
    private UDPSocketServer mSocketServer;
    private String mApSsid;
    private String mApBssid;
    private boolean mIsSsidHidden;
    private String mApPassword;
    private Context mContext;
    private AtomicBoolean mIsCancelled;
    private IEsptouchTaskParameter mParameter;
    private volatile Map<String, Integer> mBssidTaskSucCountMap;
    private IEsptouchListener mEsptouchListener;

    public EsptouchTask(String apSsid, String apBssid, String apPassword, boolean isSsidHidden, Context context) {
        mParameter = new EsptouchTaskParameter();
        if (TextUtils.isEmpty(apSsid)) {
            throw new IllegalArgumentException("apSsid不能为空");
        }
        if (apPassword == null) {
            apPassword = "";
        }
        mContext = context;
        mApSsid = apSsid;
        mApBssid = apBssid;
        mApPassword = apPassword;
        mIsCancelled = new AtomicBoolean(false);
        mSocketClient = new UDPSocketClient();
        mSocketServer = new UDPSocketServer(mParameter.getPortListening(), mParameter.getWaitUdpTotalMillisecond(), context);
        mIsSsidHidden = isSsidHidden;
        mEsptouchResultList = new ArrayList<>();
        mBssidTaskSucCountMap = new HashMap<>();
    }


    @Override
    public void interrupt() {
        LogUtil.e("interrupt()");
        mIsCancelled.set(true);
        doInterrupt();
    }

    private synchronized void doInterrupt() {
        if (!mIsInterrupt) {
            mIsInterrupt = true;
            mSocketClient.interrupt();
            mSocketServer.interrupt();
            //打断用于等待udp响应的当前线程
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public IEsptouchResult executeForResult() throws RuntimeException {
        return executeForResults(1).get(0);
    }

    @Override
    public boolean isCancelled() {
        return mIsCancelled.get();
    }

    @Override
    public List<IEsptouchResult> executeForResults(int expectTaskResultCount) throws RuntimeException {
        if (expectTaskResultCount <= 0) {
            expectTaskResultCount = Integer.MAX_VALUE;
        }
        checkTaskValid();
        mParameter.setExpectTaskResultCount(expectTaskResultCount);
        LogUtil.e("开始执行");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("不要在UI线程操作");
        }
        InetAddress localInetAddress = EspNetUtil.getLocalInetAddress(mContext);
        LogUtil.e("本机ip: " + localInetAddress);

        // 数据转换，耗时
        IEsptouchGenerator generator = new EsptouchGenerator(mApSsid, mApBssid, mApPassword, localInetAddress, mIsSsidHidden);
        // 异步监听配置结果
        listenAsyn(mParameter.getEsptouchResultTotalLen());
        boolean isSuc;
        for (int i = 0; i < mParameter.getTotalRepeatTime(); i++) {
            isSuc = execute(generator);
            if (isSuc) {
                return getEsptouchResultList();
            }
        }

        if (!mIsInterrupt) {
            // 等待udp响应而不发送udp广播
            try {
                Thread.sleep(mParameter.getWaitUdpReceivingMillisecond());
            } catch (InterruptedException e) {
                // 接收udp广播或用户中断任务
                if (mIsSuc) {
                    return getEsptouchResultList();
                } else {
                    doInterrupt();
                    return getEsptouchResultList();
                }
            }
            doInterrupt();
        }
        return getEsptouchResultList();
    }


    private void checkTaskValid() {
        if (mIsExecuted) {
            throw new IllegalStateException("任务只能执行一次");
        }
        mIsExecuted = true;
    }

    private void listenAsyn(final int expectDataLen) {
        new Thread() {
            public void run() {
                LogUtil.e("异步监听开始");
                long startTimestamp = System.currentTimeMillis();
                byte[] apSsidAndPassword = ByteUtil.getBytesByString(mApSsid + mApPassword);
                byte expectOneByte = (byte) (apSsidAndPassword.length + 9);
                LogUtil.e("想要接收字节数: " + (0 + expectOneByte));
                byte receiveOneByte;
                byte[] receiveBytes;
                while (mEsptouchResultList.size() < mParameter.getExpectTaskResultCount() && !mIsInterrupt) {
                    receiveBytes = mSocketServer.receiveSpecLenBytes(expectDataLen);
                    if (receiveBytes != null) {
                        receiveOneByte = receiveBytes[0];
                    } else {
                        receiveOneByte = -1;
                    }
                    if (receiveOneByte == expectOneByte) {
                        LogUtil.e("接收正确广播");
                        // 更改socket超时
                        long consume = System.currentTimeMillis() - startTimestamp;
                        int timeout = (int) (mParameter.getWaitUdpTotalMillisecond() - consume);
                        if (timeout < 0) {
                            LogUtil.e("配置超时");
                            break;
                        } else {
                            LogUtil.e("mSocketServer 新超时： " + timeout + " milliseconds");
                            mSocketServer.setSoTimeout(timeout);
                            if (receiveBytes != null) {
                                String bssid = ByteUtil.parseBssid(receiveBytes, mParameter.getEsptouchResultOneLen(),
                                        mParameter.getEsptouchResultMacLen());
                                InetAddress inetAddress = EspNetUtil.parseInetAddr(receiveBytes,
                                        mParameter.getEsptouchResultOneLen() + mParameter.getEsptouchResultMacLen(),
                                        mParameter.getEsptouchResultIpLen());
                                putEsptouchResult(true, bssid, inetAddress);
                            }
                        }
                    } else {
                        LogUtil.e("接收垃圾消息, 忽略");
                    }
                }
                mIsSuc = mEsptouchResultList.size() >= mParameter.getExpectTaskResultCount();
                interrupt();
                LogUtil.e("异步监听结束");
            }
        }.start();
    }

    private boolean execute(IEsptouchGenerator generator) {
        long startTime = System.currentTimeMillis();
        long currentTime = startTime;
        long lastTime = currentTime - mParameter.getTimeoutTotalCodeMillisecond();
        byte[][] gcBytes2 = generator.getGCBytes2();
        byte[][] dcBytes2 = generator.getDCBytes2();
        int index = 0;
        while (!mIsInterrupt) {
            if (currentTime - lastTime >= mParameter.getTimeoutTotalCodeMillisecond()) {
                LogUtil.e("send gc code ");
                // send guide code
                while (!mIsInterrupt && System.currentTimeMillis() - currentTime < mParameter
                        .getTimeoutGuideCodeMillisecond()) {
                    mSocketClient.sendData(gcBytes2, mParameter.getTargetHostname(),
                            mParameter.getTargetPort(), mParameter.getIntervalGuideCodeMillisecond());
                    // 检查udp是否发送足够的时间
                    if (System.currentTimeMillis() - startTime > mParameter.getWaitUdpSendingMillisecond()) {
                        break;
                    }
                }
                lastTime = currentTime;
            } else {
                mSocketClient.sendData(dcBytes2, index, ONE_DATA_LEN, mParameter.getTargetHostname(),
                        mParameter.getTargetPort(), mParameter.getIntervalDataCodeMillisecond());
                index = (index + ONE_DATA_LEN) % dcBytes2.length;
            }
            currentTime = System.currentTimeMillis();

            if (currentTime - startTime > mParameter.getWaitUdpSendingMillisecond()) {
                break;
            }
        }

        return mIsSuc;
    }

    private void putEsptouchResult(boolean isSuc, String bssid, InetAddress inetAddress) {
        synchronized (mEsptouchResultList) {
            // 检查结果是否收到UDP响应
            boolean isTaskSucCountEnough;
            Integer count = mBssidTaskSucCountMap.get(bssid);
            if (count == null) {
                count = 0;
            }
            ++count;
            LogUtil.e("putEsptouchResult(): count = " + count);
            mBssidTaskSucCountMap.put(bssid, count);
            isTaskSucCountEnough = count >= mParameter.getThresholdSucBroadcastCount();
            if (!isTaskSucCountEnough) {
                LogUtil.e("putEsptouchResult(): count = " + count + ", isn't enough");
                return;
            }
            // check whether the result is in the mEsptouchResultList already
            boolean isExist = false;
            for (IEsptouchResult esptouchResultInList : mEsptouchResultList) {
                if (esptouchResultInList.getBssid().equals(bssid)) {
                    isExist = true;
                    break;
                }
            }
            // only add the result who isn't in the mEsptouchResultList
            if (!isExist) {
                LogUtil.e("putEsptouchResult(): put one more result");
                final IEsptouchResult esptouchResult = new EsptouchResult(isSuc, bssid, inetAddress);
                mEsptouchResultList.add(esptouchResult);
                if (mEsptouchListener != null) {
                    mEsptouchListener.onEsptouchResultAdded(esptouchResult);
                }
            }
        }
    }

    private List<IEsptouchResult> getEsptouchResultList() {
        synchronized (mEsptouchResultList) {
            if (mEsptouchResultList.isEmpty()) {
                EsptouchResult esptouchResultFail = new EsptouchResult(false, null, null);
                esptouchResultFail.setIsCancelled(mIsCancelled.get());
                mEsptouchResultList.add(esptouchResultFail);
            }
            return mEsptouchResultList;
        }
    }

    @Override
    public void setEsptouchListener(IEsptouchListener esptouchListener) {
        mEsptouchListener = esptouchListener;
    }
}
