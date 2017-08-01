package com.longhorn.smartlib.task;

import com.longhorn.smartlib.protocol.DatumCode;
import com.longhorn.smartlib.protocol.GuideCode;
import com.longhorn.smartlib.util.ByteUtil;

import java.net.InetAddress;


public class EsptouchGenerator implements IEsptouchGenerator {

    private final byte[][] mGcBytes2;
    private final byte[][] mDcBytes2;

    public EsptouchGenerator(String apSsid, String apBssid, String apPassword,InetAddress inetAddress, boolean isSsidHiden) {
        // generate guide code
        GuideCode gc = new GuideCode();
        char[] gcU81 = gc.getU8s();
        mGcBytes2 = new byte[gcU81.length][];

        for (int i = 0; i < mGcBytes2.length; i++) {
            mGcBytes2[i] = ByteUtil.genSpecBytes(gcU81[i]);
        }

        // generate data code
        DatumCode dc = new DatumCode(apSsid, apBssid, apPassword, inetAddress, isSsidHiden);
        char[] dcU81 = dc.getU8s();
        mDcBytes2 = new byte[dcU81.length][];

        for (int i = 0; i < mDcBytes2.length; i++) {
            mDcBytes2[i] = ByteUtil.genSpecBytes(dcU81[i]);
        }
    }

    @Override
    public byte[][] getGCBytes2() {
        return mGcBytes2;
    }

    @Override
    public byte[][] getDCBytes2() {
        return mDcBytes2;
    }

}
