package com.dazhihui.smdemo.network.packet;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

/**
 * 返回信息的解析方法类
 * <p>
 * Title:
 * </p>
 * 
 * <p>
 * Description:
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2008
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */
public class ParseResponse {
	private DataInputStream in;
	private ByteArrayInputStream bin;

	public ParseResponse(byte[] data) {
		bin = new ByteArrayInputStream(data);
		in = new DataInputStream(bin);
		try {
			in.available();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public ParseResponse(InputStream _in) {
		in = new DataInputStream(_in);
	}

	public int readLength() {
		return readShort();
	}

	public boolean readBoolean() {
		try {
			return in.readBoolean();
		} catch (Exception ex) {
			throw new RuntimeException();
		}
	}

	public boolean[] readBooleans() {
		boolean[] v = new boolean[readLength()];
		for (int i = 0; i < v.length; i++) {
			v[i] = readBoolean();
		}
		return v;
	}

	public boolean[][] readBooleans2() {
		int length = readLength();
		if (length == 0) {
			return new boolean[0][];
		}
		boolean[][] v = new boolean[length][readLength()];
		for (int i = 0; i < v.length; i++) {
			for (int j = 0; j < v[0].length; j++) {
				v[i][j] = readBoolean();
			}
		}
		return v;
	}

	public int readChar() {
		try {
			return in.readChar();
		} catch (Exception ex) {
			throw new RuntimeException();
		}
	}

	public int readByte() {
		try {
			return in.readByte();
		} catch (Exception ex) {
			//throw new RuntimeException();
			return 0;
		}
	}
	
	public int available(){
		try {
			return in.available();
		} catch (IOException e) {
			return -1;
		}
	}

	public byte[] readBytesWithLength(int length) throws IOException {
		byte[] b = new byte[length];
		in.read(b);
		return b;
	}

	public int[] readBytes() {
		int[] v = new int[readLength()];
		for (int i = 0; i < v.length; i++) {
			v[i] = readByte();
		}
		return v;
	}

	public int[][] readBytes2() {
		int length = readLength();
		if (length == 0) {
			return new int[0][];
		}
		int[][] v = new int[length][readLength()];
		for (int i = 0; i < v.length; i++) {
			for (int j = 0; j < v[0].length; j++) {
				v[i][j] = readByte();
			}
		}
		return v;
	}

	public int readShortWithSign() {
		int it = readShort();
		if (it >> 15 == 0) {
			return it;
		} else {
			return ((~it + 1) & 0xffff) * (-1);
		}
	}

	public int readShort() {
		try {
			int v2 = in.read();
			int v1 = in.read();
			// Functions.Log("v2 = " + v2 +"; v1 = " + v1);
			if ((v1 | v2) < 0) {
				throw new EOFException();
			}
			return ((v1 << 8) + (v2 << 0));
		} catch (IOException ex) {
			return 0;
		}

	}

	public int readIntWithSign() {
		int it = readInt();
		if (it >> 31 == 0) {
			return it;
		} else {
			return ((~it + 1) & 0xffffffff) * (-1);
		}
	}

	/**
	 * 读取三个字节长度数据
	 * 
	 * @return int
	 */
	public int readSpecialSignInt() {
		try {
			int v3 = in.read();
			int v2 = in.read();
			int v1 = in.read();
			if ((v1 | v2 | v3) < 0) {
				throw new EOFException();
			}
			int it = ((v1 << 16) + (v2 << 8) + (v3 << 0));

			if (it >> 23 == 0) {
				return it;
			} else {
				return ((~it + 1) & 0x00ffffff) * (-1);
			}

		} catch (IOException ex) {
			return 0;
		}
	}

	public int[] readShorts() {
		int[] v = new int[readLength()];
		for (int i = 0; i < v.length; i++) {
			v[i] = readShort();
		}
		return v;
	}

	public int[][] readShorts2() {
		int length = readLength();
		if (length == 0) {
			return new int[0][];
		}
		int[][] v = new int[length][readLength()];
		for (int i = 0; i < v.length; i++) {
			for (int j = 0; j < v[0].length; j++) {
				v[i][j] = readShort();
			}
		}
		return v;
	}

	public int readInt() {

		try {
			int v4 = in.read();
			int v3 = in.read();
			int v2 = in.read();
			int v1 = in.read();
			if ((v1 | v2 | v3 | v4) < 0) {
				throw new EOFException();
			}
			return ((v1 << 24) + (v2 << 16) + (v3 << 8) + (v4 << 0));
		} catch (IOException ex) {
			return 0;
		}
	}

	public float readFloat() {

		try {
			return Float.intBitsToFloat(readInt());
		} catch (Exception e) {
			return 0;
		}
	}

	public long readIntToLong() {

		try {
			int v4 = in.read();
			int v3 = in.read();
			int v2 = in.read();
			int v1 = in.read();
			if ((v1 | v2 | v3 | v4) < 0) {
				throw new EOFException();
			}

			long v = 0;

			if ((v1 & 0xC0) == 64) {
				v = ((v1 << 26) + (v2 << 18) + (v3 << 10) + (v4 << 2));
				v <<= 4;
			} else if ((v1 & 0xC0) == 128) {
				v = ((v1 << 26) + (v2 << 18) + (v3 << 10) + (v4 << 2));
				v <<= 8;
			} else if ((v1 & 0xC0) == 192) {
				v = ((v1 << 26) + (v2 << 18) + (v3 << 10) + (v4 << 2));
				v <<= 12;
			} else
				v = ((v1 << 24) + (v2 << 16) + (v3 << 8) + (v4 << 0));

			return v;

		} catch (IOException ex) {
			return 0;
		}
	}

	public int readInt24() {

		try {
			int v3 = in.read();
			int v2 = in.read();
			int v1 = in.read();

			int value = ((v1 << 16) & 0xff0000) | ((v2 << 8) & 0xff00) | ((v3 << 0) & 0xff);

			int sign = v1 & 0x00000080;

			if (sign != 0) {
				value = ~value;
				value &= 0x00ffffff;
				value += 1;
				value *= -1;
			}
			return value;
		} catch (IOException ex) {
			return 0;
		}
	}

	public int readInt24_unsign() {

		try {
			// int v4 = in.read();
			int v3 = in.read();
			int v2 = in.read();
			int v1 = in.read();

			if ((v1 | v2 | v3) < 0) {
				throw new EOFException();
			}

			return ((v1 << 16) + (v2 << 8) + (v3 << 0));
		} catch (IOException ex) {
			return 0;
		}
	}

	public int[] readInts() {
		int[] v = new int[readLength()];
		for (int i = 0; i < v.length; i++) {
			v[i] = readInt();
		}
		return v;
	}

	public int[][] readInts2() {
		int length = readLength();
		if (length == 0) {
			return new int[0][];
		}
		int[][] v = new int[length][readLength()];
		for (int i = 0; i < v.length; i++) {
			for (int j = 0; j < v[0].length; j++) {
				v[i][j] = readInt();
			}
		}
		return v;
	}

	public int[] readNumbers() {
		int size = readByte();
		if (size == 1) {
			return readBytes();
		}
		if (size == 2) {
			return readShorts();
		}
		return readInts();
	}

	public int[][] readNumbers2() {
		int size = readByte();
		if (size == 1) {
			return readBytes2();
		}
		if (size == 2) {
			return readShorts2();
		}
		return readInts2();
	}

	public long readLong() {
		try {
			long v8 = in.read();
			long v7 = in.read();
			long v6 = in.read();
			long v5 = in.read();
			long v4 = in.read();
			long v3 = in.read();
			long v2 = in.read();
			long v1 = in.read();
			if ((v1 | v2 | v3 | v4 | v5 | v6 | v7 | v8) < 0) {
				throw new EOFException();
			}
			return ((v1 << 56) + (v2 << 48) + (v3 << 40) + (v4 << 32) + (v5 << 24) + (v6 << 16) + (v7 << 8) + (v8 << 0));
		} catch (IOException ex) {
			return 0;
		}
	}

	public String readString() {
		try {

			int len = readLength();
			byte[] b = new byte[len];
			in.read(b);
			return new String(b, "UTF-8");
		} catch (IOException ex) {
			return null;
		}
	}

	public String[] readStrings() {
		String[] v = new String[readLength()];
		for (int i = 0; i < v.length; i++) {
			v[i] = readString();
		}
		return v;
	}

	public String[][] readStrings2() {
		String[][] v = new String[readLength()][];
		for (int i = 0; i < v.length; i++) {
			v[i] = readStrings();
		}
		return v;
	}

	public byte[] readByteArray(int type) {
		int len = 0;
		if (type == 0)
			len = readShort();
		else
			len = readInt();

		byte[] v = new byte[len];

		if (v.length == 0) {
			return v;
		}
		int offset = 0;
		try {
			while (offset < v.length) {
				int length = in.read(v, offset, v.length - offset);
				if (length == -1) {
					throw new IOException();
				}
				offset += length;
			}

		} catch (IOException ex1) {
		}
		return v;
	}
	
	public int getDataLengthByType(int type){
		int len = 0;
		if (type == 0)
			len = readShort();
		else
			len = readInt();
		return len;
	}

	public byte[] getOthers() {
		try {
			int num = in.available();
			byte[] tmp = new byte[num];
			for (int i = 0; i < num; i++) {
				tmp[i] = in.readByte();
			}
			return tmp;
		} catch (IOException ex) {
			return null;
		}
	}

	public Vector<String> readVector() {
		int num = readLength();
		Vector<String> v = new Vector<String>(num);
		for (int i = 0; i < num; i++) {
			v.addElement(readString());
		}
		return v;
	}

	public Vector<Integer> readVectorByte() {
		int num = readLength();
		Vector<Integer> v = new Vector<Integer>(num);
		for (int i = 0; i < num; i++) {
			v.addElement(new Integer(readByte()));
		}
		return v;
	}

	public byte[] readBuffer(int number) {
		byte[] data = new byte[number];
		try {
			in.read(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}

	public void close() {
		try {
			if (in != null) {
				in.close();
			}
			if (bin != null) {
				bin.close();
			}
			in = null;
			bin = null;
		} catch (IOException ex) {
		}
	}

}
