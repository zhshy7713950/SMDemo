/**
 * 
 */
package com.dazhihui.smdemo.network.packet;

/**
 * @author dzh
 *
 */
public interface IRequest {
	
	int HANDLE_TIMEOUT = 0;
	
	int HANDLE_EXCEPTION = 1;
	
	int HANDLE_RESPONSE = 2;
	
	void setSendStatus(boolean status);

	boolean hasSend();
	
	void setTimeout(long millisecond);
	
	long getTimeout();
	
	void setRequestListener(IRequestListener listener);
	
	IRequestListener getRequestListener();
	
	void requestNextTask();
	
	Object getAttach();
	
	void setAttach(Object obj);
	
	Object getExtraData();
	
	void setExtraData(Object obj);
	
	void setResponse(boolean flag);
	
}
