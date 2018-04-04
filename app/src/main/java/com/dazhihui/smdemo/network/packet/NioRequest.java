package com.dazhihui.smdemo.network.packet;

import android.os.Handler;
import android.os.Message;


public class NioRequest extends AbstractRequest {

	protected byte mTag = '{';
	// private byte mRightTag = '}';

	protected NioRequestType mRequestType = NioRequestType.SCREEN;
	protected int mCount = 0;
	protected int mSize = 0;

	private boolean mNeedAck = true;
	private boolean mReusable = false;
	/**
	 * 识别谁发的包？
	 */
	private String mWhatThis = "";

	public NioRequest() {
	}

	public NioRequestType getRequestType() {
		return mRequestType;
	}

	public void setRequestType(NioRequestType type) {
		mRequestType = type;
	}

	public void setTag(byte tag) {
		if (mListener == null) {
			mTag = 0;
		} else {
			mTag = tag;
		}
	}

	public byte getTag() {
		return mTag;
	}

	public byte[] getData() {
		return null;
	}

	public enum NioRequestType {
		BEFRORE_LOGIN, NO_SCREEN, SCREEN, PROTOCOL_SPECIAL
	}

	public void decreaseCount(int value) {
		mCount = mCount - value;
	}

	@Override
	public boolean waitResponse() {
		if (mCount <= 0) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void processOnce() {
		mCount--;
	}

	@Override
	public void sendMessage(Message msg) {
		processOnce();
		Object obj = msg.obj;
		if (obj != null && obj instanceof NioResponse) {
			int index = mSize - mCount - 1;
			NioResponse resp = (NioResponse) obj;
			resp.setExtraObject(index);
		}
		mHandler.sendMessage(msg);
	}

	@Override
	protected void processTaskAfterSend() {
		if (!mNeedAck) {
//			undoSendRequest();
		}
	}


	public void setNeedAck(boolean value) {
		mNeedAck = value;
	}

	public boolean needAck() {
		return mNeedAck;
	}

	public void setReusable(boolean flag) {
		mReusable = flag;
	}

	public boolean isResuable() {
		return mReusable;
	}

	public Handler getHandler() {
		return mHandler;
	}

}
