package com.longhorn.smartlib;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.InetAddress;

// it is used to process the message got from the IOT Device
// "I'm Light.  98:fe:34:77:ce:00   192.168.4.1"
public class IOTAddress implements Parcelable {
    private String mBSSID;
    private InetAddress mInetAddress;


    public IOTAddress(String BSSID, InetAddress inetAddress) {
        this.mBSSID = BSSID;
        this.mInetAddress = inetAddress;
    }

    public String getBSSID() {
        return mBSSID;
    }

    public InetAddress getInetAddress() {
        return mInetAddress;
    }


    @Override
    public String toString() {
        return "BSSID:" + mBSSID + ",InetAddress:" + mInetAddress ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IOTAddress))
            return false;
        return false;
    }

    @Override
    public int hashCode() {
        return mBSSID.hashCode();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mBSSID);
        dest.writeSerializable(this.mInetAddress);
    }

    protected IOTAddress(Parcel in) {
        this.mBSSID = in.readString();
        this.mInetAddress = (InetAddress) in.readSerializable();
    }

    public static final Creator<IOTAddress> CREATOR = new Creator<IOTAddress>() {
        @Override
        public IOTAddress createFromParcel(Parcel source) {
            return new IOTAddress(source);
        }

        @Override
        public IOTAddress[] newArray(int size) {
            return new IOTAddress[size];
        }
    };
}
