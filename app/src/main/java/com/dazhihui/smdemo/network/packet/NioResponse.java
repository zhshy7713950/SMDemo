package com.dazhihui.smdemo.network.packet;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NioResponse implements IResponse {

	private static NioResponse sPreResponse = null;

	public final static byte START_WARNING_FLAG = (byte) 0;// 0

	public final static int STRUCT_LENGTH_INT = 1;

	public final static int STRUCT_LENGTH_SHORT = 0;

	public synchronized static void clearPreResponse() {
		sPreResponse = null;
	}

	private static List<Integer> sProtocolList = new ArrayList<Integer>();

	protected byte mTag = -1;
	private boolean isPushMsg = false;
	private ResponseData mData = null;
	private int mProtocolType = 0;
	private boolean isZip = false;
	private boolean hasProcessed = false;
	private byte[] mPreData = null;
	private int mNextLen = 0;
	private Object mExtraObj;

	public NioResponse() {

	}

	public NioResponse(int protocolType, byte tag) {
		mProtocolType = protocolType;
		mTag = tag;
	}

	public int getProtocolType() {
		return mProtocolType;
	}

	public byte getTag() {
		return mTag;
	}

	public boolean isPushMsg() {
		return isPushMsg;
	}

	public void setPushMsg(boolean flag) {
		isPushMsg = flag;
	}

	public ResponseData getData() {
		return mData;
	}

	public boolean isZip() {
		return isZip;
	}

	public void setZip(boolean flag) {
		isZip = flag;
	}

	public boolean hasProcessed() {
		return hasProcessed;
	}

	public void setHasProcessed(boolean flag) {
		hasProcessed = flag;
	}

	public void setPreData(byte[] bytes) {
		mPreData = bytes;
	}

	public byte[] getPreData() {
		return mPreData;
	}

	public void setNextLen(int len) {
		mNextLen = len;
	}

	public int getNextLen() {
		return mNextLen;
	}

	public void setExtraObject(Object obj) {
		mExtraObj = obj;
	}

	public Object getExtraObject() {
		return mExtraObj;
	}

	@Override
	public void processResponseData(byte[] data) {
		mData = new ResponseData(mProtocolType, data);
		// analyzeData(data,true);
	}

	private static boolean supportProtocol(int protocol) {
		for (Integer supportProtocol : sProtocolList) {
			if (protocol == supportProtocol) {
				return true;
			}
		}
		return false;
	}

	public synchronized static List<NioResponse> analyzeData(byte[] data) {
		List<NioResponse> lists = new ArrayList<NioResponse>();
		ParseResponse structResp = new ParseResponse(data);
		int available = structResp.available();
		if (sPreResponse != null) {
			int readLen = 0;
			boolean eob = available >= sPreResponse.getNextLen();
			if (eob) {
				readLen = sPreResponse.getNextLen();
			} else {
				readLen = available;
			}
			byte[] temp = null;
			try {
				temp = structResp.readBytesWithLength(readLen);
			} catch (IOException e1) {
				e1.printStackTrace();
				sPreResponse = null;
				structResp.close();
				return lists;
			}
			byte[] distBytes = new byte[sPreResponse.getPreData().length + temp.length];
			System.arraycopy(sPreResponse.getPreData(), 0, distBytes, 0, sPreResponse.getPreData().length);
			System.arraycopy(temp, 0, distBytes, sPreResponse.getPreData().length, temp.length);
			if (eob) {
				sPreResponse.processResponseData(distBytes);
				lists.add(sPreResponse);
				sPreResponse = null;
			} else {
				sPreResponse.setPreData(distBytes);
				sPreResponse.setNextLen(sPreResponse.getNextLen() - available);
				structResp.close();
				return lists;
			}
		}

		byte tag = -128;
		byte[] subData = null;
		while (available > 0) {
			try {
				tag = (byte) structResp.readByte();
			} catch (RuntimeException e) {
				structResp.close();
				return lists;
			}
			Log.d("Tag", "response tag: " + tag + "   ");
			int protocolType = structResp.readShort();
			Log.d("Protocol", "response protocol: " + protocolType + "   ");
			if (protocolType == 0) {
				sPreResponse = null;
				structResp.close();
				return lists;
			}
			int attrs = structResp.readShort();
			int rarFlag = attrs & 2;//数据压缩位
			int zipFlag = attrs & 4;//数据zip压缩位
			int lenFlag = attrs & 8;//长度扩充位
			int lenType = STRUCT_LENGTH_SHORT;
			if (lenFlag == 8) {
				lenType = STRUCT_LENGTH_INT;
			}
			boolean isPushMsg = false;
			if (tag == START_WARNING_FLAG) {
				sPreResponse = null;
				isPushMsg = true;
			}
			NioResponse response = new NioResponse(protocolType, tag);
			response.setPushMsg(isPushMsg);
			int subDataLen = structResp.getDataLengthByType(lenType);
			if (subDataLen < 0 || !supportProtocol(protocolType)) {
				sPreResponse = null;
				break;
			}
			available = structResp.available();
			if (available < subDataLen) {
				/*
				 * Log.d("Protocol", "available: " + available + "   ");
				 * Log.d("Protocol", "subDataLen: " + subDataLen + "   ");
				 * Log.d("Protocol", "nextLen: " + (subDataLen-available) +
				 * "   ");
				 */
				if (rarFlag == 2) {
					response.setZip(true);
				}
				sPreResponse = response;
				try {
					sPreResponse.setPreData(structResp.readBytesWithLength(available));
				} catch (IOException e) {
					e.printStackTrace();
					sPreResponse = null;
					structResp.close();
					return lists;
				}
				sPreResponse.setNextLen(subDataLen - available);
				break;
			} else {
				try {
					subData = structResp.readBytesWithLength(subDataLen);
				} catch (IOException e1) {
					e1.printStackTrace();
					sPreResponse = null;
					structResp.close();
					return lists;
				}
				if (rarFlag == 2) {
					response.setZip(true);
					try {
						response.processResponseData(subData);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					response.setZip(false);
					response.processResponseData(subData);
				}
				lists.add(response);
			}
			// subData = structResp.readByteArray(lenType);
			available = structResp.available();
		}
		structResp.close();
		return lists;
	}

	public class ResponseData {

		public ResponseData(int type, byte[] bytes) {
			protocolType = type;
			data = bytes;
		}

		public int protocolType;
		public byte[] data;
	}

}
