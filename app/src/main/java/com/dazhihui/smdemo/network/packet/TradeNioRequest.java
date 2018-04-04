/**
 * 
 */
package com.dazhihui.smdemo.network.packet;


import com.dazhihui.smdemo.trade.TradePack;

/**
 * @author dzh
 * 
 */
public class TradeNioRequest extends NioRequest {

	private TradePack[] mRequestTradeDatas = null;

	private int mNewTradeTag;

	private String mRequestFuncId;

	public TradeNioRequest(TradePack[] requestDatas) {
		mRequestTradeDatas = requestDatas;
		mCount = mRequestTradeDatas.length;
		mRequestType = NioRequestType.PROTOCOL_SPECIAL;
		mAddressType = ServerAddressType.DELEGATE;
		setTimeout(15 * 1000);
	}

	public void setTradeTag(int tag){
		mNewTradeTag = tag;
	}

	public int getTradeTag(){
		return mNewTradeTag;
	}


	public void setRequestTradeDatas(TradePack[] requestDatas){
		mRequestTradeDatas = requestDatas;
	}
	
	public TradePack[] getRequestTradeDatas(){
		return mRequestTradeDatas;
	}
	
	@Override
    protected boolean processResponse(int what, Object obj) {
        // TODO Auto-generated method stub
	    boolean isOK = false;
        switch(what){
            case IRequest.HANDLE_TIMEOUT:
//            	if(NetworkManager.getInstance().isNetworkAvailable() && TradeHelper.hasLogined()){
//	            	NetworkManager.getInstance().mDelegateTimeoutTime++;
//	                if(NetworkManager.getInstance().mDelegateTimeoutTime >= NetworkManager.DELEGATE_TIMEOUT_TIMES){
//	//                    NetworkManager.getInstance().clearDelegate();
//	                    DelegateLoginManager.getInstance().netStatusChangeSendTradeLogin();
//	                    NetworkManager.getInstance().mDelegateTimeoutTime = 0;
//	                }
//            	}
            	break;
            case IRequest.HANDLE_EXCEPTION:
//            	 NetworkManager.getInstance().mDelegateTimeoutTime = 0;
//                if (DelegateLoginManager.getInstance().getReEnterFromBackgroundState()
//                        && NetworkManager.getInstance().isNetworkAvailable()) {
////                    //后台回来第一个包异常则自动重新登陆
////                    Log.d("trade", "ReEnterFromBackground  sendTradeLogin");
////                    DelegateLoginManager.getInstance().setReEnterFromBackgroundState(false);
////                    DelegateLoginManager.getInstance().sendTradeLogin();
//                }
                break;
            case IRequest.HANDLE_RESPONSE:
//				NetworkManager.getInstance().mDelegateTimeoutTime = 0;
                TradeNioResponse tradeResp = (TradeNioResponse) obj;
                break;
        }
        return isOK;
    }

    private byte[] encodeByte = null;

    @Override
	public byte[] getData() {
		if(encodeByte == null){
			encodeByte = TradePack.encode(mRequestTradeDatas,this);
		}
		return encodeByte;
	}
}
