package com.longhorn.smartlib.task;

import java.net.InetAddress;

public interface IEsptouchResult {
	boolean isSuc();
	String getBssid();
	boolean isCancelled();
	InetAddress getInetAddress();
}
