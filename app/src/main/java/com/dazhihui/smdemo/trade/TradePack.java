package com.dazhihui.smdemo.trade;

import android.content.Context;
import android.util.Base64;
import android.view.Gravity;
import android.widget.Toast;

import com.dazhihui.smdemo.network.packet.TradeNioRequest;
import com.dazhihui.smdemo.trade.sm.SM3_Util;
import com.dazhihui.smdemo.trade.sm.SMHelper;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Random;
import java.util.Vector;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class TradePack {
	public static byte[] KEY;
	private static int SEQ_ID = 0;
	private static int COOKIE = 0;

	private static byte[] sDataBytes = null;


	public static void setKey(byte[] key, int seqId) {
		KEY = key;
		SEQ_ID = seqId;
	}

	public static void clear() {
		KEY = null;
		SEQ_ID = 0;
		COOKIE = 0;
		//***********************************//
		SMHelper.clearKey();
	}
	
	public static boolean hasKey(){
	    return KEY == null ? false : true;
	}

	public static final int TYPE_DATA_AC = 0xAC;

	public static final int TYPE_CER_AE = 0xAE;//客户端取服务器证书
	public static final int TYPE_CONN_A0 = 0xA0;//当客户端已有证书的情况下,使用0x0建立安全环境
	public static final int TYPE_HEART = 0x01;//心跳类型，用于国密加密

	private int type;
	private byte[] data;

	private int cookie;
	private int synId;
	private int rawLen;
	private int zlib;

	private TradePack() {
	}

	public TradePack(byte[] data) {
		this(TYPE_DATA_AC, data);
	}

	public TradePack(int type, byte[] data) {
		this.type = type;
		this.data = data;
	}

	public int getType() {
		return type;
	}

	public byte[] getData() {
		return data;
	}

	public TradePack setSynId(int synId) {
		this.synId = synId;
		return this;
	}

	public int getSynId() {
		return synId;
	}

	public static void clearDataBytes() {
		sDataBytes = null;
	}

	public static byte[] encode(TradePack[] packs,TradeNioRequest request) {
		DataBuffer db = new DataBuffer();

		SEQ_ID++;

		for (int i = 0; i < packs.length; i++) {
			TradePack pack = packs[i];

			if(pack.type == TYPE_DATA_AC){
				byte[] wtdata = pack.data;
				DataBuffer db1 = new DataBuffer();
				db1.putSkip(32);
				db1.putInt(sslSeq);
				db1.putByte(0);
				db1.putInt(wtdata.length);
				db1.putInt(wtdata.length);
				db1.put(wtdata);
				int len = wtdata.length + 45;//ssl_data长度
				if (len % 16 != 0) {
					int num = (len / 16 + 1) * 16 - len;
					db1.putSkip(num);
				}
				byte[] hmac = SM3_Util.hmac_SM3(db1.getData(32), SMHelper.getKey());
				db1.put(0, hmac);
				pack.synId = sslSeq;
				pack.data = SMHelper.encodeCbc(db1.getData(), pack.synId);
				sslSeq++;
			}else if(pack.type == TYPE_CER_AE
					|| pack.type == TYPE_CONN_A0){
				pack.synId = 0;
				sslSeq = 1;
			}
			request.setTradeTag(pack.synId);

			pack.cookie = COOKIE;
			pack.rawLen = pack.data.length;

			db.putSkip(4);
			db.putByte(pack.zlib);
			db.putByte(pack.type);
			db.putInt(pack.cookie);
			db.putInt(pack.synId);
			db.putInt(pack.rawLen);
			db.putInt(pack.data.length);
			db.put(pack.data);

			int crc = CRC32.getCRC32(db.getData(4));
			db.putInt(0, CRC32.getCRC32(db.getData(4)));
		}

		return db.getData();
	}

	public static TradePack[] decode(byte[] data) {
		DataBuffer db = new DataBuffer();
		if (sDataBytes == null || sDataBytes.length == 0) {
			db.put(data);
		} else {
			db.put(sDataBytes);
			db.put(data);
		}
		db.skip(0);
		// DataBuffer db = new DataBuffer(data);
		Vector<TradePack> v = new Vector<TradePack>(1);
		while (true) {
			int startIndex = db.getOffset();
			TradePack pack = new TradePack();
			int crc32 = db.getInt();
			int offset = db.getOffset();
			pack.zlib = db.getByte();
			pack.type = db.getByte();
			pack.cookie = db.getInt();
			pack.synId = db.getInt();
			pack.rawLen = db.getInt();
			int length = db.getInt();
			if (!db.hasMore(length)) {
				sDataBytes = db.getData(startIndex);
				return null;
			} else {
				sDataBytes = null;
			}
			pack.data = db.get(length);
			if (CRC32.getCRC32(db.getData(offset, db.getOffset() - offset)) != crc32){
				sDataBytes = null;
				return null;
			}else{
				COOKIE = pack.cookie;
			}
			if (pack.zlib != 0)
				pack.data = ZLIB.inflate(pack.data, pack.rawLen);
			if ((pack.type & 0xff) == TYPE_DATA_AC) {
				pack.data = SMHelper.decodeCbc(pack.data, pack.synId);
				DataBuffer dataBuf = new DataBuffer(pack.data);
				byte[] md5 = dataBuf.get(0,32);
				dataBuf.skip(32);
				if (!match(md5, SM3_Util.hmac_SM3(dataBuf.getData(32),SMHelper.getKey())))
					return null;

				int seqId = dataBuf.getInt();
				int zlib = dataBuf.getByte();
				int rawLen = dataBuf.getInt();
				int len = dataBuf.getInt();
				pack.data = dataBuf.get(len);
				if (zlib != 0)
					pack.data = ZLIB.inflate(pack.data, rawLen);
			}

			v.addElement(pack);

			if (!db.hasMore())
				break;
		}

		TradePack[] packs = new TradePack[v.size()];
		for (int i = 0; i < packs.length; i++)
			packs[i] = (TradePack) v.elementAt(i);

		return packs;
	}

	public static boolean match(byte[] b1, byte[] b2) {
		for (int i = 0; i < b1.length; i++) {
			if (b1[i] != b2[i])
				return false;
		}
		return true;
	}

	private static final Random RANDOM = new Random();

	public static int randomInt() {
		int v = Math.abs(RANDOM.nextInt());
		if (v >= 10000000)
			v %= 10000000;
		if (v <= 10000000)
			v += 10000000;
		return v;
	}

	public static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		for (int i = 0; i < length; i++) {
			bytes[i] = (byte) (Math.abs(RANDOM.nextInt()) % 256);
		}
		return bytes;
	}

	public static byte[] toBytes(String s, int length) {
		byte[] bs = DataBuffer.encodeString(s);
		byte[] bytes = new byte[length];
		System.arraycopy(bs, 0, bytes, 0, Math.min(bs.length, length));
		return bytes;
	}

	public static String toString(byte[] bs, int length) {
		byte[] bytes = new byte[length];
		System.arraycopy(bs, 0, bytes, 0, Math.min(bs.length, length));
		return DataBuffer.decodeString(bytes);
	}

	/**
	 * 判断有没有获取到数据
	 * 
	 * @param packs
	 * @param context
	 * @return
	 */
	public static boolean existDB(TradePack packs, Context context) {
		boolean sign = true;
		if (packs == null) {
			Toast to = Toast.makeText(context, "　　连接失败，请重试。", Toast.LENGTH_SHORT);
			to.setGravity(Gravity.CENTER, 0, 0);
			to.show();
			sign = false;
		}
		return sign;
	}

	private static int sslSeq = 0;

	public static void setCookie(int cookie){
		COOKIE = cookie;
	}

}