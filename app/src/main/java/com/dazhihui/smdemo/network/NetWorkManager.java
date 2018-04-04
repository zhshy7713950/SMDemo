package com.dazhihui.smdemo.network;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.dazhihui.smdemo.network.nio.ConnectHandler;
import com.dazhihui.smdemo.network.nio.NioConnection;
import com.dazhihui.smdemo.network.nio.PacketHandler;
import com.dazhihui.smdemo.network.packet.IRequest;
import com.dazhihui.smdemo.network.packet.NioRequest;
import com.dazhihui.smdemo.network.packet.NioResponse;
import com.dazhihui.smdemo.network.packet.TradeNioRequest;
import com.dazhihui.smdemo.network.packet.TradeNioResponse;
import com.dazhihui.smdemo.trade.DataBuffer;
import com.dazhihui.smdemo.trade.TradePack;

import java.util.List;
import java.util.Vector;

/**
 * Created by Android on 2018/3/16.
 */

public class NetWorkManager {
    private static final NetWorkManager ourInstance = new NetWorkManager();
    private static final int DELEGATE_HEART_MSG = 0;

    private byte[] mSpecialChars = { 0, '{', '}', ':' };
    private byte mTag = -128;

    private NioConnection mDelegateConnect = null;
    private Object lock = new Object();
    private Vector<NioRequest> mDelegateNioRequests = new Vector();
    private Vector<NioRequest> mDelegateBlockNioRequests = new Vector();

    private String delegateAddr = "218.66.110.215";
    private int delegateHost = 9925;

    private boolean canHeart = false;

    public boolean isCanHeart() {
        return canHeart;
    }

    public void setCanHeart(boolean canHeart) {
        this.canHeart = canHeart;
    }

    public static NetWorkManager getInstance() {
        return ourInstance;
    }

    private NetWorkManager() {
    }

    public void sendRequest(IRequest request) {
        request.setResponse(false);
        if (request instanceof NioRequest) {
            NioRequest nioRequest = (NioRequest) request;
            nioRequest.setTag(mTag);
            increaseTag();
            synchronized (lock) {
                mDelegateNioRequests.add(nioRequest);
            }
            long timeout = 30 *1000;
            if (mDelegateConnect == null) {
                synchronized (lock) {
                    mDelegateBlockNioRequests.add(nioRequest);
                }
                mDelegateConnect = new NioConnection(delegateAddr, delegateHost);
                checkProxyConnection(mDelegateConnect);
                mDelegateConnect.registConnectHandler(new ConnectHandler() {
                    @Override
                    public void invokeStatusChanged(int status) {
                        if (status == ConnectHandler.CONNECTED) {
                            synchronized (lock) {
                                while (!mDelegateBlockNioRequests.isEmpty()) {
                                    NioRequest request = mDelegateBlockNioRequests.get(0);
                                    byte[] data = request.getData();
                                    if (data != null) {
                                        mDelegateConnect.send(data);
                                    }
                                    request.setSendStatus(true);
                                    mDelegateBlockNioRequests.remove(0);
                                    if (request.needAck()) {
                                        Message msg = Message.obtain();
                                        msg.what = IRequest.HANDLE_TIMEOUT;
                                        request.sendTimeoutMessageDelayed(msg, request.getTimeout());
                                    }
                                }
                            }
                            prepareSendHeartPacket(DELEGATE_HEART_MSG);
                        }else if(status == ConnectHandler.DISCONNECT){
                            mDelegateNioRequests.clear();
                            mDelegateBlockNioRequests.clear();
                            if (mDelegateConnect != null) {
                                mDelegateConnect.close();
                                mDelegateConnect = null;
                            }
                        }
                    }

                    @Override
                    public void netConnectException(Exception e) {
                        e.printStackTrace();
                        synchronized (lock) {
                            for (NioRequest request : mDelegateNioRequests) {
                                Message msg = Message.obtain();
                                msg.what = IRequest.HANDLE_EXCEPTION;
                                msg.obj = e;
                                request.sendMessage(msg);
                            }
                        }
                    }
                });
                mDelegateConnect.registPacketHandler(new PacketHandler() {

                    @Override
                    protected void handleResponse(byte[] bytes) {
                        prepareSendHeartPacket(DELEGATE_HEART_MSG);
                        List<NioResponse> responses = TradeNioResponse.analyzeData(bytes);
                        for (NioResponse resp : responses) {
                            synchronized (lock) {
                                for (NioRequest request : mDelegateNioRequests) {
                                    int reqTag,respTag;
                                    reqTag = (request instanceof TradeNioRequest) ?
                                            ((TradeNioRequest) request).getTradeTag() :
                                            request.getTag();

                                    respTag = (resp instanceof TradeNioResponse) ?
                                            ((TradeNioResponse) resp).getTradeTag() :
                                            resp.getTag();
                                    if (reqTag == respTag) {
                                        Message msg = Message.obtain();
                                        msg.what = IRequest.HANDLE_RESPONSE;
                                        msg.obj = resp;
                                        request.sendMessage(msg);
                                        // request.decreaseCount(1);
                                        if (!request.waitResponse()) {
                                            mDelegateNioRequests.remove(request);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                });
                mDelegateConnect.start();
            }else if (mDelegateConnect.getConnectStatus() == ConnectHandler.DISCONNECT) {
                synchronized (lock) {
                    mDelegateBlockNioRequests.add(nioRequest);
                }
                checkProxyConnection(mDelegateConnect);
                mDelegateConnect.resetServerAddress(delegateAddr, delegateHost);
                mDelegateConnect.start();
            } else if (mDelegateConnect.getConnectStatus() == ConnectHandler.CONNECTING) {
                synchronized (lock) {
                    mDelegateBlockNioRequests.add(nioRequest);
                }
            } else if (mDelegateConnect.getConnectStatus() == ConnectHandler.CONNECTED) {
                synchronized (lock) {
                    byte[] data = nioRequest.getData();
                    if(data == null){//容错处理
                        nioRequest.setSendStatus(true);
                        return;
                    }
                    mDelegateConnect.send(data);
                }
                nioRequest.setSendStatus(true);
                prepareSendHeartPacket(DELEGATE_HEART_MSG);
                timeout = nioRequest.getTimeout();
            }
            if (nioRequest.needAck()) {
                Message msg = Message.obtain();
                msg.what = IRequest.HANDLE_TIMEOUT;
                nioRequest.sendTimeoutMessageDelayed(msg, timeout);
            }
        }
    }

    public void close(){
        if(mDelegateConnect != null){
            mDelegateConnect.close();
            mDelegateConnect = null;
        }
        setCanHeart(false);
    }

    public static void checkProxyConnection(NioConnection connect) {
        String proxyAddress = null;
        int proxyPort = -1;
            proxyAddress = System.getProperty("http.proxyHost")/* "203.201.163.122" */;
            String portStr = System.getProperty("http.proxyPort")/* "8080" */;
            proxyPort = Integer.parseInt((portStr != null ? portStr : "-1"));
        if (!TextUtils.isEmpty(proxyAddress) && proxyPort != -1) {
            connect.setProxy(proxyAddress, proxyPort);
            connect.setNeedProxy(true);
        } else {
            connect.setNeedProxy(false);
        }
    }

    private void prepareSendHeartPacket(int type) {
        if (type == DELEGATE_HEART_MSG) {
            mNetworkHandler.removeMessages(DELEGATE_HEART_MSG);
            mNetworkHandler.sendEmptyMessageDelayed(DELEGATE_HEART_MSG, 30 * 1000);
        }
    }

    private Handler mNetworkHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DELEGATE_HEART_MSG:
                    if (judegDelegateConnect() && isCanHeart()) {
                        // 由于后面会对发送包加密，因此每次都需要重新创建心跳包
                        sendRequest(genTradeHeartPackage());
                    }
                    break;
            }
        }
    };

    public NioRequest genTradeHeartPackage() {
        TradePack[] packs = new TradePack[] { new TradePack(TradePack.TYPE_HEART, DataBuffer.encodeString("")) };
        TradeNioRequest request = new TradeNioRequest(packs);
        return request;
    }

    public boolean judegDelegateConnect() {
        return mDelegateConnect != null && mDelegateConnect.getConnectStatus() == ConnectHandler.CONNECTED;
    }

    private synchronized void increaseTag() {
        mTag++;
        for (int i = 0; i < mSpecialChars.length; i++) {
            if (mTag == mSpecialChars[i]) {
                mTag++;
            }
        }
        if (mTag > 127) {
            mTag = -128;
        }
    }

}
