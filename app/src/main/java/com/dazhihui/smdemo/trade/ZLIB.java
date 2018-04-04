package com.dazhihui.smdemo.trade;

import com.dazhihui.smdemo.trade.calc.JZlib;
import com.dazhihui.smdemo.trade.calc.ZInputStream;
import com.dazhihui.smdemo.trade.calc.ZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ZLIB
{
    public static byte[] deflate (byte[] src)
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            ZOutputStream zOut = new ZOutputStream (out, JZlib.Z_BEST_COMPRESSION);
            zOut.write (src);
            zOut.close ();

            byte[] enc = out.toByteArray ();
            out.close ();

            return enc;
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    public static byte[] inflate (byte[] enc, int rawLen)
    {
        try
        {
            byte[] data = new byte[rawLen * 2];

            ByteArrayInputStream in = new ByteArrayInputStream(enc);
            ZInputStream zIn = new ZInputStream (in);
            int len = 0;
            int count;
            while ((count = zIn.read (data, len, len + 1024)) != -1)
            {
                len += count;
            }

            byte[] src = new byte [len];
            System.arraycopy (data, 0, src, 0, len);

            zIn.close();
            in.close();

            return src;
        }
        catch (Exception ex)
        {
            return null;
        }
    }
}