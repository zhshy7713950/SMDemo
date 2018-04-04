package com.dazhihui.smdemo;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.dazhihui.smdemo.network.NetWorkManager;
import com.dazhihui.smdemo.network.packet.IRequest;
import com.dazhihui.smdemo.network.packet.IRequestListener;
import com.dazhihui.smdemo.network.packet.IResponse;
import com.dazhihui.smdemo.network.packet.NioRequest;
import com.dazhihui.smdemo.network.packet.NioResponse;
import com.dazhihui.smdemo.network.packet.TradeNioRequest;
import com.dazhihui.smdemo.network.packet.TradeNioResponse;
import com.dazhihui.smdemo.trade.DataBuffer;
import com.dazhihui.smdemo.trade.DataHolder;
import com.dazhihui.smdemo.trade.TradePack;
import com.dazhihui.smdemo.trade.Util;
import com.dazhihui.smdemo.trade.sm.SMHelper;
import com.dazhihui.smdemo.trade.sm.util.ByteUtils;

/**
 * Created by Android on 2018/3/16.
 */

public class MainPresenter implements MainContract.Presenter,IRequestListener {

    private MainContract.View mView;

    public MainPresenter(MainContract.View mView) {
        this.mView = mView;
        mView.setPresenter(this);
    }

    private void showRequestData(NioRequest nioRequest){
        if(nioRequest != null){
            mView.showResult("发送包：" + ByteUtils.bytesToHex(nioRequest.getData()));
        }
    }

    private void showResponseData(NioResponse nioResponse){
        if(nioResponse != null){
            mView.showResult("返回包：" + ByteUtils.bytesToHex(nioResponse.getData().data));
        }
    }

    private NioRequest mRequest_AE = null;

    /**
     * SM流程第一步，发送AE协议获取服务端证书，一次获取后不需要重复获取
     * */
    @Override
    public void sendAE() {
        mView.setLoadingIndicator(true);
        mView.showResult("第一步，发送AE协议获取服务端证书......");

        DataHolder dh = new DataHolder("10000")
                .setString("1205", "13")//客户端类别
                .setString("1202", "2.30")//客户端版本
                .setString("1750", "2")//公用功能版本号
                .setString("9030", MainActivity.QSFlag);//大智慧名称

        TradePack[] packs = new TradePack[] {
                new TradePack(TradePack.TYPE_CER_AE,dh.getData())
        };
        mRequest_AE = new TradeNioRequest(packs);
        mRequest_AE.setRequestListener(this);
        showRequestData(mRequest_AE);
        NetWorkManager.getInstance().sendRequest(mRequest_AE);
    }

    private NioRequest mRequest_A0 = null;

    /**
     * SM流程第二步，发送A0协议协商秘钥，协商成功后需要发送心跳包与服务端保持长连接
     * */
    @Override
    public void sendA0() {
        if(SMHelper.getCertServer() == null){
            mView.showMessage("请按照正确的流程发送发送：AE->A0->AC");
            return;
        }
        mView.setLoadingIndicator(true);
        mView.showResult("第二步，发送A0协议协商秘钥......");
        TradePack.clear();
        String R = SMHelper.keyExchange_1();//密钥协商第一步
        if(TextUtils.isEmpty(R) || SMHelper.getKeyPair() == null){
            return;
        }
        String pubKey = Base64.encodeToString(SMHelper.getKeyPair().getPublicKey(),Base64.DEFAULT);
        DataHolder dh = new DataHolder("10000")
                .setString("1205", "13")//客户端类别
                .setString("1202", "2.30")//客户端版本
                .setString("1750", "2")//公用功能版本号
                .setString("9030", SMHelper.DEFAULT_USER_ID_STRING)//客户端的用户身份标识,目前固定为1234567812345678
                .setString("9031", pubKey)//客户端的固定公钥的base64编码
                .setString("9032", R);//客户端的临时公钥的base64编码（即密钥交换第一步生成）

        TradePack[] packs = new TradePack[] {
                new TradePack(TradePack.TYPE_CONN_A0,dh.getData())
        };
        mRequest_A0 = new TradeNioRequest(packs);
        mRequest_A0.setRequestListener(this);
        showRequestData(mRequest_A0);
        NetWorkManager.getInstance().sendRequest(mRequest_A0);
    }

    private TradeNioRequest request_AC = null;
    /**
     * 正常的AC通信协议，这里是以获取手机验证码为例子
     * */
    @Override
    public void sendAC() {
        if(SMHelper.getCertServer() == null || SMHelper.getKey() == null){
            mView.showMessage("请按照正确的流程发送发送：AE->A0->AC");
            return;
        }
        mView.setLoadingIndicator(true);
        mView.showResult("第三步，正常的AC通信协议......");

        String mobile = "11111111111";
        DataHolder dh = new DataHolder("13028")
                .setString("2002", mobile)
                .setString("1205", "13")
                .setString("1750", "2");
        TradePack[] packs = new TradePack[]{new TradePack(dh.getData())};
        request_AC = new TradeNioRequest(packs);
        request_AC.setRequestListener(this);
        showRequestData(request_AC);
        NetWorkManager.getInstance().sendRequest(request_AC);
    }

    @Override
    public void handleResponse(IRequest request, IResponse response) {
        mView.setLoadingIndicator(false);
        TradeNioResponse resp = (TradeNioResponse) response;
        showResponseData(resp);
        TradePack pack = resp.getTradePack();
        DataHolder dh = DataHolder.getFrom(pack.getData());
        if (request == mRequest_AE) {//证书检验
            String serverCert = dh.getString(0, "9030");//AE包获取服务端证书
            try {
                String localCert = Util.inputStream2String(((Context)mView).getAssets().open("smcert.txt"));
                if (SMHelper.verifyCert(localCert, serverCert) && SMHelper.verifyCertCN(serverCert)) {
                    SMHelper.setCertServer(serverCert);
                    mView.showResult("证书校验成功！");
                }
            }catch (Exception e) {
                e.printStackTrace();
                mView.showMessage("证书校验失败！");
            }
        }else if(request == mRequest_A0){
            TradePack.setCookie(dh.getInt(0, "1208"));//cookie
            String sStr9030 = dh.getString(0, "9030");//字典文件校验和
            String sStr9031 = dh.getString(0, "9031");//营业部文件校验和
            String sStr9032 = dh.getString(0, "9032");//委托登录方式校验和
            String serverUserId = dh.getString(0, "9033");//服务端的用户身份标识,目前固定为1234567812345678
            String serverR = dh.getString(0, "9034");//服务端的临时公钥的base64编码（即密钥交换第一步生成）
            byte[] key = SMHelper.keyExchange_2(SMHelper.getCertServer(),
                    Base64.decode(serverR.getBytes(),Base64.DEFAULT),
                    serverUserId.getBytes());//密钥协商第二步
            SMHelper.setKey(key);
            mView.showResult("与服务器密钥协商成功！");
            NetWorkManager.getInstance().setCanHeart(true);
        }else if(request == request_AC){
            mView.showResult(dh.toString());
        }
    }

    @Override
    public void netException(IRequest request, Exception ex) {
        mView.setLoadingIndicator(false);
        mView.showMessage("网络请求异常");
    }

    @Override
    public void handleTimeout(IRequest request) {
        mView.setLoadingIndicator(false);
        mView.showMessage("网络请求超时");
    }
}
