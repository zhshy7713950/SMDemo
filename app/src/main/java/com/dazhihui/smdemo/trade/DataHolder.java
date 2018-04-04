package com.dazhihui.smdemo.trade;


import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class DataHolder
{
    private String func;
    private Hashtable head = new Hashtable();
    private Hashtable[] body;

    /**
     * C协议传参，web端做传参比对。
     */
    String webTest = null;//测试包用到，正式包不会用到

    public DataHolder(){
    }

    public DataHolder(String func)
    {
        this.func = func;
    }

    public String getFunc ()
    {
        return func;
    }

    public boolean isOK ()
    {
        return getMessage () == null;
    }

    public String getResult ()
    {
        return getString ("21008");
    }

    public String getMessage ()
    {
        return getString ("21009");
    }

    public String[] getKeys ()
    {
        return getKeys (head);
    }

    public DataHolder setString (String key, String value)
    {
        setString (head, key, value);
        return this;
    }

    public Hashtable[] getBody(){
        return body;
    }

    public void setBody(Hashtable[] table){
        body = table;
    }

    public Hashtable getHead(){
        return head;
    }

    public void setHead(Hashtable table){
        head = table;
    }

    public String getString (String key)
    {
        return getString (head, key);
    }

    public DataHolder setInt (String key, int value)
    {
        setInt (head, key, value);
        return this;
    }

    public int getInt (String key)
    {
        return getInt (head, key);
    }

    public int getRowCount ()
    {
        return body == null ? 0 : body.length;
    }

    public String[] getKeys (int row)
    {
        return getKeys (body [row]);
    }

    public String getString (int row, String key)
    {
        if (body == null)
            return "";
        return getString (body [row], key);
    }

    /**
     * 获取指定行指定关键字对应的字符串值，如未找到返回默认值。
     * @param row 行号。
     * @param key 键值。
     * @param defultValue 默认值。
     * @return
     */
    public String getString(int row, String key, String defultValue){
        String result = getString (body [row], key);

        if(result != null){
            return result;
        } else {
            return defultValue;
        }
    }

    public int getInt (int row, String key)
    {
        return getInt (body [row], key);
    }

    public byte[] getData ()
    {
        StringBuffer b = new StringBuffer();
        b.append ("8=DZH1.0\1");
        b.append ("21004=").append (func).append ("\1");

        Enumeration params = head.keys ();
        while (params.hasMoreElements ())
        {
            String param = (String) params.nextElement ();
            String value = (String) head.get (param);
            b.append (param).append ("=").append (value).append ("\1");
        }


        if(mExtraData != null){//如果有额外数据需要添加到请求中的，跟在最后面（目前网络投票功能需要）
            b.append(mExtraData);
        }

        try {
            return	b.toString().getBytes("GBK");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return	b.toString().getBytes();
        }
    }

    public String toString ()
    {
        return toString ("\r\n", "|");
    }

    private String toString (String flagRow, String flagColumn)
    {
        StringBuffer b = new StringBuffer();
        b.append ("   +---------------+").append (flagRow);
        b.append ("   | 21004 : ").append (func).append (flagRow);

        String[] names = new String[head.size ()];
        Enumeration params = head.keys ();
        for (int i = 0; i < names.length; i++) names [i] = (String) params.nextElement ();
        sort (names);

        String param = null, value = null;
        for (int i = 0; i < names.length; i++)
        {
            param = names [i];
            value = (String) head.get (param);
            b.append ("   | ").append (param).append ("     ".substring (param.length ()))
             .append (" : ").append (value).append (flagRow);
        }

        if (body != null)
        {
            Vector v = new Vector();
            for (int i = 0; i < body.length; i++)
            {
                params = body [i].keys ();
                while (params.hasMoreElements ())
                {
                    param = (String) params.nextElement ();
                    if (v.indexOf (param) == -1) v.addElement (param);
                }
            }

            String[] header = new String[v.size ()];
            for (int i = 0; i < header.length; i++) header [i] = (String) v.elementAt (i);
            sort (header);

            String[][] table = new String[body.length] [header.length];
            for (int i = 0; i < header.length; i++)
            {
                for (int j = 0; j < body.length; j++)
                {
                    table [j] [i] = (String) body [j].get (header [i]);
                }
            }

            String[] blanks = new String[header.length];
            String[] seps = new String[header.length];
            for (int c = 0; c < header.length; c++)
            {
                int width = 0;
                byte[] bs = header [c].getBytes ();
                if (bs.length > width) width = bs.length;
                for (int r = 0; r < table.length; r++)
                {
                    String s = (table [r] [c] == null ? "NULL" : table [r] [c]);
                    bs = s.getBytes ();
                    if (bs.length > width) width = bs.length;
                }

                char[] chars = new char [width];
                char[] chars1 = new char [width];
                for (int i = 0; i < width; i++)
                {
                    chars [i] = ' ';
                    chars1 [i] = '-';
                }
                blanks [c] = new String(chars);
                seps [c] = new String(chars1);
            }

            append (b, seps, flagRow);
            append (b, header, blanks, flagRow, flagColumn);
            append (b, seps, flagRow);
            for (int i = 0; i < table.length; i++) append (b, table [i], blanks, flagRow, flagColumn);
            append (b, seps, flagRow);
        }

        return b.toString ();
    }

    private void sort (String[] items)
    {
        for (int i = 0; i < items.length; i++)
        {
            for (int j = i + 1; j < items.length; j++)
            {
                if (items [i].length () > items [j].length () || items [i].compareTo (items [j]) > 0)
                {
                    String temp = items [i];
                    items [i] = items [j];
                    items [j] = temp;
                }
            }
        }
    }

    private void append (StringBuffer b, String[] seps, String flagRow)
    {
        b.append ("+");
        for (int i = 0; i < seps.length; i++)
        {
            b.append ("-").append (seps [i]).append ("-").append ("+");
        }
        b.append (flagRow);
    }

    private void append (StringBuffer b, String[] cols, String[] blanks, String flagRow, String flagColumn)
    {
        if (cols == null) return;
        b.append (flagColumn);
        for (int i = 0; i < cols.length; i++)
        {
            String s = (cols [i] == null ? "NULL" : cols [i]);
            byte[] bs = s.getBytes ();
            b.append (" ").append (s).append (blanks [i].substring (bs.length))
             .append (" ").append (flagColumn);
        }
        b.append (flagRow);
    }

    private static String[] getKeys (Hashtable data)
    {
        String[] keys = new String[data.size ()];
        Enumeration keys1 = data.keys ();
        for (int i = 0; i < keys.length; i++) keys [i] = (String) keys1.nextElement ();
        return keys;
    }

    private static void setString (Hashtable data, String key, String value)
    {
        data.put (key, value);
    }

    private static String getString (Hashtable data, String key)
    {
        return (String) data.get (key);
    }

    private static void setInt (Hashtable data, String key, int value)
    {
        data.put (key, String.valueOf (value));
    }

    private static int getInt (Hashtable data, String key)
    {
        String value = (String) data.get (key);
        return value == null ? -1 : Integer.parseInt (value);
    }

    private static void parse (Hashtable data, String s, int i1, int i2)
    {
        char c = 0;
        int p1 = i1, p2 = 0, p3 = 0;
        for (int i = i1; i < i2; i++)
        {
            c = s.charAt (i);
            if (c == '=' && p2 == 0) p2 = i;
            else if (c == '\1')
            {
                p3 = i;
                if (p1 > p2 || p2 + 1 > p3) continue;
                data.put (s.substring (p1, p2), s.substring (p2 + 1, p3));
                p1 = p3 + 1;
                p2 = 0;
            }
        }
    }

    public static DataHolder getFrom (byte[] b)
    {
        try {
            return getFrom(new String(b,"GBK"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return getFrom(new String(b));
        }
    }

    public static DataHolder getFrom (String s)
    {
        if (s == null || s.length () == 0) return null;
        s += "\1";

        int p1 = s.indexOf ("\1" + "21004=");
        if (p1 == -1) return null;
        p1 += 7;

        int p2 = s.indexOf ("\1", p1);
        if (p2 == -1) return null;

        DataHolder dh = new DataHolder ();
        dh.func = s.substring (p1, p2).trim ();

        int p3 = s.indexOf ("\1" + "21000=", p2);
        parse (dh.head, s, p2 + 1, (p3 == -1 ? s.length () : p3 + 1));

        if (p3 == -1) return dh;

        Vector v = new Vector();
        boolean first = true;
        while (true)
        {
            p1 = s.indexOf ("\1" + "21002=", p3 + 7);
            if (p1 == -1)
            {
                int p4 = s.indexOf ("\1", p3 + 7);
                int p5 = s.indexOf ("\1" + "21001=", p4);
                if (p5 > p4) parse (dh.head, s, p4 + 1, p5 + 1);
                break;
            }
            else if (first)
            {
                int p4 = s.indexOf ("\1", p3 + 7);
                if (p1 > p4) parse (dh.head, s, p4 + 1, p1 + 1);
                first = false;
            }
            p1 += 7;

            p2 = s.indexOf ("\1", p1);
            if (p2 == -1) break;

            p3 = s.indexOf ("\1" + "21003=", p2);
            if (p3 == -1) break;

            Hashtable data = new Hashtable();
            parse (data, s, p2 + 1, p3 + 1);
            v.addElement (data);
        }

        if (v.size () == 0) return dh;

        /**
         * 特殊处理返回一条记录中含有相同字段 2016-2-29
         */
        if (dh.func.equals("11101")) {
            try {
                int index = s.indexOf("\1" + "1291");
                if(index != -1) {
                    int index1 = s.indexOf("\1", index + 1);
                    if(index1 != -1) {
                        int amount = 0;
                        String amountStr = s.substring(index + 6, index1).trim();
                        if (amountStr != null && !amountStr.equals("")) {
                            amount = Integer.parseInt(amountStr);
                            dh.properManagerMsg = new String[amount][2];
                            dh.properManagerMsg = parseProperManagerMsg(s, amount);
                        }
                    }
                }
                else{
                    dh.properManagerMsg = new String[][]{};
                }
            } catch (Exception e) {
                dh.properManagerMsg = new String[][]{};
                e.printStackTrace();
            }
        }
        
        dh.body = new Hashtable[v.size ()];
        for (int i = 0; i < v.size (); i++) dh.body [i] = (Hashtable) v.elementAt (i);

        return dh;
    }
    
    private String[][] properManagerMsg;

    private static String[][] parseProperManagerMsg(String s, int amount) {
        String result[][] = new String[amount][2];
        int p, p1 = 0, p2, p3 = 0;
        for (int i = 0; i < amount; i++) {
            p = s.indexOf("\1" + "1292", p1);
            p += 6;
            p1 = s.indexOf("\1", p);
            result[i][0] = s.substring(p, p1);
            p = p1;

            p2 = s.indexOf("\1" + "1799", p3);
            p2 += 6;
            p3 = s.indexOf("\1", p2);
            result[i][1] = s.substring(p2, p3);
            p2 = p3;
        }

        return result;
    }
    
    public String[][] getProperManagerMsg() {
        return properManagerMsg;
    }
    
    public String getValue(String string, String key) {
        try {
            final String str1 = key + "=";
            int p1 = string.indexOf(str1);
            if (p1 < 0 || p1 >= string.length()) {
                return null;
            }
            String str2 = string.substring(p1);
            int p2 = str2.indexOf("\1");
            if (p2 < 0 || p2 >= string.length()) {
                return null;
            }
            String str3 = str2.substring(0, p2);
            int p3 = str3.indexOf("=");
            if (p3 < 0 || p3 >= str3.length()) {
                return null;
            }
            String str4 = str3.substring(p3 + 1);
            str4 = str4.trim();
            return str4;
        } catch (Exception e) {
            return null;
        }
    }    

    private StringBuffer multiDatasetBuffer;
    /**
     * 多数据集开始
     * @param count 多数据集总项数。
     */
    public void multiDataBegin(int count){
    	multiDatasetBuffer = new StringBuffer();
    	multiDatasetBuffer.append("21000").append("=").append(count).append("\1");
    }
    /**
     * 多数据集结束
     * @param count 多数据集总项数。
     */
    public void multiDataEnd(int count){    	
    	multiDatasetBuffer.append("21001").append("=").append(count).append("\1");
    }
    /**
     * 多数据集的一项开始
     * @param itemIndex 多数据集一项的索引。
     */
    public void multiDataItemBegin(int itemIndex){
    	multiDatasetBuffer.append("21002").append("=").append(itemIndex).append("\1");
    }
    /**
     * 多数据集的一项结束
     * @param itemIndex 多数据集一项的索引。
     */
    public void multiDataItemEnd(int itemIndex){    	
    	multiDatasetBuffer.append("21003").append("=").append(itemIndex).append("\1");
    }
    /**
     * 设置多数据集一项的键值对。
     */
    public DataHolder setMultiDataItemInt(String key, int value){
    	multiDatasetBuffer.append(key).append("=").append(value).append("\1");
    	
    	return this;
    }
    /**
     * 设置多数据集一项的键值对。
     */
    public DataHolder setMultiDataItemString(String key, String value){
    	multiDatasetBuffer.append(key).append("=").append(value).append("\1");
    	
    	return this;
    }
    /**
     * 获取多数据集Data。
     * @return
     */
    public byte[] getMultiData(){
        StringBuffer b = new StringBuffer();
        b.append("8=DZH1.0\1");
        b.append("21004=").append(func).append("\1");
        Enumeration params = head.keys();
        while (params.hasMoreElements()) {
            String param = (String) params.nextElement();
            String value = (String) head.get(param);
            b.append(param).append("=").append(value).append("\1");
        }
        b.append(multiDatasetBuffer);
        return DataBuffer.encodeString(b.toString());
    }


    private String mExtraData;

    /**
     * 设置额外的请求数据，发送请求时，加在发送的字符串后面（目前网络投票功能需要）
     * @param extraData
     */
    public void setExtraData(String extraData){
        mExtraData = extraData;
    }
}