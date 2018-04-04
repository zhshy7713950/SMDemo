/**
 * 
 */
package com.dazhihui.smdemo.network.packet;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * @author dzh
 * 
 */
public abstract class AbstractRequest implements IRequest {

	protected long mStamp = 0;

    /**
     * 请求超时时间。
     */
	private long mMillisecond = 0;

	protected boolean mSendStatus;

	protected WeakReference<IRequestListener> mListener;

	protected ServerAddressType mAddressType = ServerAddressType.MARKET;

	protected Handler mHandler;

	private Object mAttach;
	private Object mExtralData;
	private boolean mResponsed;

	private RequestFinishHandler mFinishHandler;

	public AbstractRequest() {
		Looper mainLoop = Looper.getMainLooper();
		mHandler = new Handler(mainLoop) {

			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				if (!mResponsed) {
					if (mListener != null && mListener.get() != null) {
						switch (msg.what) {
						case IRequest.HANDLE_TIMEOUT:
							removeMessages(IRequest.HANDLE_EXCEPTION);
							removeMessages(IRequest.HANDLE_RESPONSE);
							processResponse(msg.what, msg.obj);
							mListener.get().handleTimeout(AbstractRequest.this);
							mResponsed = true;
//							NetworkManager.getInstance().processRequestTimeout(AbstractRequest.this);
//							NetworkManager.getInstance().removeSendRequest(AbstractRequest.this);
							break;
						case IRequest.HANDLE_EXCEPTION:
							removeMessages(IRequest.HANDLE_TIMEOUT);
							removeMessages(IRequest.HANDLE_RESPONSE);
							processResponse(msg.what, msg.obj);
							mListener.get().netException(AbstractRequest.this, (Exception) msg.obj);
							mResponsed = true;
//							NetworkManager.getInstance().removeSendRequest(AbstractRequest.this);
							break;
						case IRequest.HANDLE_RESPONSE:
							removeMessages(IRequest.HANDLE_EXCEPTION);
							removeMessages(IRequest.HANDLE_TIMEOUT);
							if (processResponse(msg.what, msg.obj)) {// 委托需对所有返回包进行特殊过滤
								mListener.get().netException(AbstractRequest.this, new Exception("Server Exception"));
							} else {
								try {
									mListener.get().handleResponse(AbstractRequest.this, (IResponse) msg.obj);
								}catch (Exception e){
									e.printStackTrace();
								}
							}
							break;
						}
					}
				}
				if (mFinishHandler != null) {
					mFinishHandler.handleFinished(msg.what);
				}
			}
		};
	}

	@Override
	public void setSendStatus(boolean status) {
		mSendStatus = status;
		if (status) {
			processTaskAfterSend();
		}
	}

	@Override
	public boolean hasSend() {
		return mSendStatus;
	}

	@Override
	public void setTimeout(long millisecond) {
		mMillisecond = millisecond;
	}

	@Override
	public long getTimeout() {
		return 1000 * 15;
	}

	public void setStamp(long stamp) {
		mStamp = stamp;
	}

	public long getStamp() {
		return mStamp;
	}

	@Override
	public void setRequestListener(IRequestListener listener) {
		mListener = new WeakReference<IRequestListener>(listener);
	}

	@Override
	public IRequestListener getRequestListener() {
		if(mListener == null){
			return null;
		}else {
			return mListener.get();
		}

	}

	public void setServeAddressType(ServerAddressType type) {
		mAddressType = type;
	}

	public ServerAddressType getServerAddressType() {
		return mAddressType;
	}

	public enum ServerAddressType {
		MARKET, DELEGATE
	}

	public boolean sendTimeoutMessageDelayed(Message msg, long delayMillis) {
		mStamp = System.currentTimeMillis();
		mHandler.removeMessages(msg.what);
		return mHandler.sendMessageDelayed(msg, delayMillis);
	}

	public void sendMessage(Message msg) {
		processOnce();
		mHandler.sendMessage(msg);
	}

	public void removeMessage(int what) {
		mHandler.removeMessages(what);
	}

	@Override
	public void requestNextTask() {
	}

	@Override
	public Object getAttach() {
		return mAttach;
	}

	@Override
	public void setAttach(Object obj) {
		mAttach = obj;
	}

	@Override
	public Object getExtraData() {
		return mExtralData;
	}

	@Override
	public void setExtraData(Object obj) {
		mExtralData = obj;
	}

	@Override
	public void setResponse(boolean flag) {
		mResponsed = flag;
	}

	protected void processTaskAfterSend() {

	}

	public void setRequestFinishHandler(RequestFinishHandler handler) {
		mFinishHandler = handler;
	}

	public abstract void processOnce();

	public abstract boolean waitResponse();

	public interface RequestFinishHandler {
		public void handleFinished(int type);
	}

	protected boolean processResponse(int what, Object obj) {
		return false;
	}

}
