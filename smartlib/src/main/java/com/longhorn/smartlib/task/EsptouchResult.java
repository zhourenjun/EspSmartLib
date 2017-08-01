package com.longhorn.smartlib.task;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class EsptouchResult implements IEsptouchResult {

	private final boolean mIsSuc;
	private final String mBssid;
	private final InetAddress mInetAddress;
	private AtomicBoolean mIsCancelled;


	public EsptouchResult(boolean isSuc, String bssid,InetAddress inetAddress) {
		mIsSuc = isSuc;
		mBssid = bssid;
		mInetAddress = inetAddress;
		mIsCancelled = new AtomicBoolean(false);
	}

	@Override
	public boolean isSuc() {
		return mIsSuc;
	}

	@Override
	public String getBssid() {
		return mBssid;
	}

	@Override
	public boolean isCancelled() {
		return mIsCancelled.get();
	}
	
	public void setIsCancelled(boolean isCancelled){
		mIsCancelled.set(isCancelled);
	}

	@Override
	public InetAddress getInetAddress() {
		return mInetAddress;
	}

}
