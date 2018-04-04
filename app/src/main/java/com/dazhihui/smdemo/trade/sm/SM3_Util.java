package com.dazhihui.smdemo.trade.sm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class SM3_Util {
	
	/**
	 * 字节数组拼接
	 * 
	 * @param params
	 * @return
	 */
	public static byte[] join(byte[]... params) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] res = null;
		try {
			for (int i = 0; i < params.length; i++) {
				baos.write(params[i]);
			}
			res = baos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return res;
	}
	
	/**
	 * sm3摘要
	 * 
	 * @param params
	 * @return
	 */
	public static byte[] sm3hash(byte[]... params) {
		byte[] res = null;
		try {
			res = SM3.hash(join(params));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return res;
	}
	
	public static byte[] hmac_SM3(byte[] message, byte[] key){
		byte[] ipadArray = new byte[64];
		byte[] opadArray = new byte[64];
		byte[] keyarray = new byte[64];
		if(key.length > 64){
			byte[] temp = sm3hash(key);
			for (int i = 0; i < 64; i++) {
				keyarray[i] = temp[i];
			}
		}
		else{
			for (int i = 0; i < key.length; i++) {
				keyarray[i] = key[i];
			}
			for (int i = key.length; i < 64; i++) {
				keyarray[i] = 0x00;
			}
		}
		for (int j = 0; j < 64; j++) {
			ipadArray[j] = (byte) (keyarray[j] ^ 0x36);
			opadArray[j] = (byte) (keyarray[j] ^ 0x5C);
		}
		byte[] temp1 = sm3hash(join(ipadArray,message));
		return sm3hash(join(opadArray,temp1));
	}

}
