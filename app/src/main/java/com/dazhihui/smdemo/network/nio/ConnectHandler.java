package com.dazhihui.smdemo.network.nio;

public interface ConnectHandler {
	
	public static final int DISCONNECT = 0;
	
	public static final int CONNECTING = 1;
	
	public static final int CONNECTED = 2;
	
	public void invokeStatusChanged(int status);
	
	public void netConnectException(Exception e);

}
