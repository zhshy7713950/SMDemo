/**
 *
 */
package com.dazhihui.smdemo.network.packet;


import com.dazhihui.smdemo.trade.DataBuffer;
import com.dazhihui.smdemo.trade.TradePack;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dzh
 *
 */
public class TradeNioResponse extends NioResponse {

    private TradePack mTradePack;

    private int mNewTradeTag;

    public TradeNioResponse(TradePack pack, byte tag) {
        mTradePack = pack;
        mTag = tag;
        mNewTradeTag = tag;
    }

    public int getTradeTag() {
        return mNewTradeTag;
    }

    public TradePack getTradePack() {
        return mTradePack;
    }

    public synchronized static List<NioResponse> analyzeData(byte[] data) {
        List<NioResponse> lists = new ArrayList();
        TradePack[] trades = TradePack.decode(data);
        if (trades != null) {
            for (TradePack pack : trades) {
                TradeNioResponse tradeNioResponse = new TradeNioResponse(pack, (byte) pack.getSynId());
                lists.add(tradeNioResponse);
                tradeNioResponse.processResponseData(data);
            }
        }
        return lists;
    }

    public synchronized static void clearDataBytes() {
        TradePack.clearDataBytes();
    }
}
