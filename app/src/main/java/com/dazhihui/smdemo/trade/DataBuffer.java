package com.dazhihui.smdemo.trade;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Vector;

public class DataBuffer {
	private static boolean HL = false;

	public static void setDefaultHL(boolean hl) {
		HL = hl;
	}

	private byte[] buffer;
	private int offset;
	private int length;

	private boolean hl = HL;

	public DataBuffer() {
		this(256);
	}

	public DataBuffer(int initSize) {
		this.buffer = new byte[initSize];
		offset = 0;
		length = 0;
	}

	public DataBuffer(byte[] buffer) {
		this.buffer = buffer;
		offset = 0;
		length = buffer.length;
	}

	public void setHL(boolean hl) {
		this.hl = hl;
	}

	public void reset() {
		offset = 0;
		length = 0;
	}

	public void skip(int offset) {
		this.offset = offset;
	}

	public byte[] getData() {
		return getData(0, length);
	}

	public byte[] getData(int offset) {
		return getData(offset, length - offset);
	}

	public byte[] getRemainData(int left) {
		return getData(offset + left);
	}

	public byte[] getData(int offset, int length) {
		byte[] data = new byte[length];
		System.arraycopy(buffer, offset, data, 0, length);
		return data;
	}

	public int getOffset() {
		return offset;
	}

	public boolean hasMore() {
		return offset < length;
	}

	public boolean hasMore(int left) {
		return offset + left <= length;
	}

	private void ensure(int left) {
		if (offset + left > length)
			length = offset + left;
		if (offset + left <= buffer.length)
			return;
		byte[] temp = new byte[buffer.length + left + 256];
		System.arraycopy(buffer, 0, temp, 0, buffer.length);
		buffer = temp;
	}

	private void check(int left) {
	}

	public void putSkip(int length) {
		ensure(length);
		offset += length;
	}

	public void getSkip(int length) {
		check(length);
		offset += length;
	}

	public void putByte(int v) {
		ensure(1);
		buffer[offset++] = (byte) (v & 0xFF);
	}

	public int getByte() {
		check(1);
		return buffer[offset++];
	}

	public void putShort(int v) {
		ensure(2);
		if (hl) {
			buffer[offset++] = (byte) ((v >>> 8) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 0) & 0xFF);
		} else {
			buffer[offset++] = (byte) ((v >>> 0) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 8) & 0xFF);
		}
	}

	public int getShort() {
		check(2);
		if (hl) {
			return (short) (((buffer[offset++] & 0xFF) << 8) | ((buffer[offset++] & 0xFF) << 0));
		} else {
			return (short) (((buffer[offset++] & 0xFF) << 0) | ((buffer[offset++] & 0xFF) << 8));
		}
	}

	public void putInt(int v) {
		ensure(4);
		if (hl) {
			buffer[offset++] = (byte) ((v >>> 24) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 16) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 8) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 0) & 0xFF);
		} else {
			buffer[offset++] = (byte) ((v >>> 0) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 8) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 16) & 0xFF);
			buffer[offset++] = (byte) ((v >>> 24) & 0xFF);
		}
	}

	public int getInt() {
		check(4);
		if (hl) {
			return ((buffer[offset++] & 0xFF) << 24) | ((buffer[offset++] & 0xFF) << 16)
					| ((buffer[offset++] & 0xFF) << 8) | ((buffer[offset++] & 0xFF) << 0);
		} else {
			return ((buffer[offset++] & 0xFF) << 0) | ((buffer[offset++] & 0xFF) << 8)
					| ((buffer[offset++] & 0xFF) << 16) | ((buffer[offset++] & 0xFF) << 24);
		}
	}

	public void putLong(long v) {
		ensure(8);
		if (hl) {
			putInt((int) ((v >>> 32) & 0xFFFFFFFFL));
			putInt((int) ((v >>> 0) & 0xFFFFFFFFL));
		} else {
			putInt((int) ((v >>> 0) & 0xFFFFFFFFL));
			putInt((int) ((v >>> 32) & 0xFFFFFFFFL));
		}
	}

	public long getLong() {
		check(8);
		if (hl) {
			return (((long) getInt() << 32) & 0xFFFFFFFFL) | (((long) getInt() << 0) & 0xFFFFFFFFL);
		} else {
			return (((long) getInt() << 0) & 0xFFFFFFFFL) | (((long) getInt() << 32) & 0xFFFFFFFFL);
		}
	}

	public void putBoolean(boolean v) {
		putByte(v ? 1 : 0);
	}

	public boolean getBoolean() {
		return getByte() != 0;
	}

	public void put(byte[] v) {
		ensure(v.length);
		System.arraycopy(v, 0, buffer, offset, v.length);
		offset += v.length;
	}

	public byte[] get(int length) {
		check(length);
		byte[] v = new byte[length];
		System.arraycopy(buffer, offset, v, 0, length);
		offset += length;
		return v;
	}

	public void putString(String v) {
		byte[] b = encodeString(v);
		putShort(b.length);
		put(b);
	}

	public String getString() {
		int utflen = getShort();
		byte[] bytearr = get(utflen);
		return decodeDbString(bytearr);
	}

	public void putByte(int index, int v) {
		int offset = this.offset;
		this.offset = index;
		putByte(v);
		this.offset = offset;
	}

	public int getByte(int index) {
		int offset = this.offset;
		this.offset = index;
		int v = getByte();
		this.offset = offset;
		return v;
	}

	public void putShort(int index, int v) {
		int offset = this.offset;
		this.offset = index;
		putShort(v);
		this.offset = offset;
	}

	public int getShort(int index) {
		int offset = this.offset;
		this.offset = index;
		int v = getShort();
		this.offset = offset;
		return v;
	}

	public void putInt(int index, int v) {
		int offset = this.offset;
		this.offset = index;
		putInt(v);
		this.offset = offset;
	}

	public int getInt(int index) {
		int offset = this.offset;
		this.offset = index;
		int v = getInt();
		this.offset = offset;
		return v;
	}

	public void putLong(int index, long v) {
		int offset = this.offset;
		this.offset = index;
		putLong(v);
		this.offset = offset;
	}

	public long getLong(int index) {
		int offset = this.offset;
		this.offset = index;
		long v = getLong();
		this.offset = offset;
		return v;
	}

	public void putBoolean(int index, boolean v) {
		int offset = this.offset;
		this.offset = index;
		putBoolean(v);
		this.offset = offset;
	}

	public boolean getBoolean(int index) {
		int offset = this.offset;
		this.offset = index;
		boolean v = getBoolean();
		this.offset = offset;
		return v;
	}

	public void put(int index, byte[] v) {
		int offset = this.offset;
		this.offset = index;
		put(v);
		this.offset = offset;
	}

	public byte[] get(int index, int length) {
		int offset = this.offset;
		this.offset = index;
		byte[] v = get(length);
		this.offset = offset;
		return v;
	}

	public void putString(int index, String v) {
		int offset = this.offset;
		this.offset = index;
		putString(v);
		this.offset = offset;
	}

	public String getString(int index) {
		int offset = this.offset;
		this.offset = index;
		String v = getString();
		this.offset = offset;
		return v;
	}

	public void putBooleans(boolean[] v) {
		putShort(v.length);
		for (int i = 0; i < v.length; i++)
			putBoolean(v[i]);
	}

	public boolean[] getBooleans() {
		boolean[] v = new boolean[getShort()];
		for (int i = 0; i < v.length; i++)
			v[i] = getBoolean();
		return v;
	}

	public void putBytes(int[] v) {
		putShort(v.length);
		for (int i = 0; i < v.length; i++)
			putByte(v[i]);
	}

	public int[] getBytes() {
		int[] v = new int[getShort()];
		for (int i = 0; i < v.length; i++)
			v[i] = getByte();
		return v;
	}

	public void putBytes2(int[][] v) {
		putShort(v == null ? -1 : v.length);
		if (v != null) {
			for (int i = 0; i < v.length; i++)
				putBytes(v[i]);
		}
	}

	public int[][] getBytes2() {
		int length = getShort();
		if (length == -1) {
			return null;
		}
		int[][] v = new int[length][];
		for (int i = 0; i < v.length; i++)
			v[i] = getBytes();
		return v;
	}

	public void putShorts(int[] v) {
		putShort(v.length);
		for (int i = 0; i < v.length; i++)
			putShort(v[i]);
	}

	public int[] getShorts() {
		int[] v = new int[getShort()];
		for (int i = 0; i < v.length; i++)
			v[i] = getShort();
		return v;
	}

	public void putInts(int[] v) {
		putShort(v.length);
		for (int i = 0; i < v.length; i++)
			putInt(v[i]);
	}

	public int[] getInts() {
		int[] v = new int[getShort()];
		for (int i = 0; i < v.length; i++)
			v[i] = getInt();
		return v;
	}

	public void putStrings(String[] v) {
		putShort(v.length);
		for (int i = 0; i < v.length; i++)
			putString(v[i]);
	}

	public void putVector(Vector v) {
		String[] strs = new String[v.size()];
		for (int i = 0; i < strs.length; i++)
			strs[i] = (String) v.elementAt(i);
		putShort(strs.length);
		for (int i = 0; i < strs.length; i++)
			putString(strs[i]);
	}

	public String[] getStrings() {
		String[] v = new String[getShort()];
		for (int i = 0; i < v.length; i++)
			v[i] = getString();
		return v;
	}

	public void putStrings2(String[][] v) {
		putShort(v == null ? -1 : v.length);
		if (v != null) {
			for (int i = 0; i < v.length; i++)
				putStrings(v[i]);
		}
	}

	public String[][] getStrings2() {
		int length = getShort();
		if (length == -1)
			return null;
		String[][] v = new String[length][];
		for (int i = 0; i < v.length; i++)
			v[i] = getStrings();
		return v;
	}

	public static byte[] encodeString(String v) {
		int strlen = v.length();
		char[] charr = new char[strlen];
		v.getChars(0, strlen, charr, 0);

		int utflen = 0;
		int c, count = 0;

		for (int i = 0; i < strlen; i++) {
			c = charr[i];
			if (c >= 0x0001 && c <= 0x007F) {
				utflen++;
			} else if (c > 0x07FF) {
				utflen += 3;
			} else {
				utflen += 2;
			}
		}

		if (utflen > 32767) {
			return new byte[0];
		}

		byte[] bytearr = new byte[utflen];
		for (int i = 0; i < strlen; i++) {
			c = charr[i];
			if (c >= 0x0001 && c <= 0x007F) {
				bytearr[count++] = (byte) c;
			} else if (c > 0x07FF) {
				bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
			} else {
				bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
				bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
			}
		}

		return bytearr;
	}
	/**
	 * 解析数据库内容和老主站包，
	 * 由于会和国密网络请求包的解密冲突，所以单独写一个方法
	 * */
	private static String decodeDbString(byte[] b){
		int utflen = b.length;
		byte[] bytearr = b;

		int strlen = 0;
		int c, count = 0;

		for (int i = 0; i < utflen;) {
			c = ((int) bytearr[i] & 0xff) >> 4;
			if (c >= 0 && c <= 7) {
				i++;
				strlen++;
			} else if (c >= 12 && c <= 13) {
				i += 2;
				strlen++;
			} else if (c == 14) {
				i += 3;
				strlen++;
			} else {
				return null;
			}
		}

		char[] charr = new char[strlen];
		int char1, char2, char3;
		for (int i = 0; i < utflen;) {
			char1 = (int) bytearr[i] & 0xff;
			c = char1 >> 4;
			if (c >= 0 && c <= 7) {
				i++;
				charr[count++] = (char) char1;
			} else if (c >= 12 && c <= 13) {
				i += 2;
				char2 = (int) bytearr[i - 1];
				if ((char2 & 0xC0) != 0x80) {
					return null;
				}
				charr[count++] = (char) (((char1 & 0x1F) << 6) | (char2 & 0x3F));
			} else if (c == 14) {
				i += 3;
				char2 = (int) bytearr[i - 2];
				char3 = (int) bytearr[i - 1];
				if ((char2 & 0xC0) != 0x80 || (char3 & 0xC0) != 0x80) {
					return null;
				}
				charr[count++] = (char) (((char1 & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
			}
		}

		return new String(charr);
	}

	public static String decodeString(byte[] b) {
		String strdata = "";
		try {
			strdata = new String(b, "GBK");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return "";
		}
		return strdata;
	}
}