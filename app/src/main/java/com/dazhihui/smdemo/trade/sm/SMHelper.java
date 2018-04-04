package com.dazhihui.smdemo.trade.sm;

import android.text.TextUtils;
import android.util.Base64;


import com.dazhihui.smdemo.MainActivity;
import com.dazhihui.smdemo.trade.sm.exception.InvalidKeyDataException;
import com.dazhihui.smdemo.trade.sm.exception.InvalidSignDataException;
import com.dazhihui.smdemo.trade.sm.tlv.IllegalPbocTlvFormatException;
import com.dazhihui.smdemo.trade.sm.tlv.PbocTlvElement;
import com.dazhihui.smdemo.trade.sm.tlv.PbocTlvParser;
import com.dazhihui.smdemo.trade.sm.util.Base64Utils;

import org.bouncycastle.math.ec.ECPoint;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * sm操作整合:证书验证，密钥协商
 */

public final class SMHelper {

    public static final String DEFAULT_USER_ID_STRING = "1234567812345678";
    //默认用户ID
    public static final byte[] DEFAULT_USER_ID = DEFAULT_USER_ID_STRING.getBytes();

    private static final byte[] ref = {6,3,85,4,3};//参照数值对比，跳过后一位取长度，截取证书名字

    private static String certServer = null;

    private static SM2Cipher sm2Cipher = null;
    private static SM2Cipher.KeyPair keyPair = null;
    private static SM2Cipher.KeyExchange keyExchange = null;
    private static byte[] key = null;

    public static void clear(){
        certServer = null;
        clearKey();
    }

    public static void clearKey(){
        sm2Cipher = null;
        keyPair = null;
        keyExchange = null;
        key = null;
    }

    /**
     * sm4加密
     * 加密算法为SM4_CBC,IV的生成方法是外部包头的synid重复4次
     * */
    public static byte[] encodeCbc(byte[] data,int synId){
        SM4Utils sm4 = new SM4Utils();
        sm4.setSecretKey(key);
        sm4.setIv(getIv(synId));
        return sm4.encryptData_CBC(data);
    }

    /**
     * sm4解密
     * 解密算法为SM4_CBC,IV的生成方法是外部包头的synid重复4次
     * */
    public static byte[] decodeCbc(byte[] data,int synId){
        SM4Utils sm4 = new SM4Utils();
        sm4.setSecretKey(key);
        sm4.setIv(getIv(synId));
        return sm4.decryptData_CBC(data);
    }

    private static byte[] getIv(int synId){
        byte[] iv = new byte[16];
        byte[] idByte = int2byte(synId);
        int offset;
        for(int i=0;i<4;i++){
            offset = 4*i;
            System.arraycopy(idByte,0,iv,offset,idByte.length);
        }
        return iv;
    }

    private static byte[] int2byte(int v)
    {
        byte[] b = new byte[4];
        for(int i=0,j=3;i<4;i++,j--)
            b[i]=(byte)(v >> (24 - j*8));
        return b;
    }

    /**
     * 密钥协商
     * 返回R点
     * */
    public static String keyExchange_1(){
        sm2Cipher = new SM2Cipher();
        keyPair = sm2Cipher.generateKeyPair();
        keyExchange = new SM2Cipher.KeyExchange(DEFAULT_USER_ID,keyPair,sm2Cipher);
        SM2Cipher.TransportEntity entity = keyExchange.keyExchange_1();
        return Base64.encodeToString(entity.getR(), Base64.DEFAULT);
    }

    /**
     * 密钥协商
     * @param certServer 服务端下发证书
     * @param r R点
     * @param idBytes 服务端用户id
     * */
    public static byte[] keyExchange_2(final String certServer, byte[] r, byte[] idBytes){
        if(sm2Cipher == null || keyPair == null || keyExchange == null){
            return null;
        }
        try {
            byte[] pubKey = getPublicKey(certServer);
            ECPoint pubPoint = sm2Cipher.getCurve().decodePoint(pubKey);
            SM2Cipher.TransportEntity entity = new SM2Cipher.TransportEntity(r, null,
    				SM2Cipher.ZA(idBytes, pubPoint), pubKey);
            keyExchange.keyExchange_3(entity);
            return keyExchange.getKey();
        } catch (IllegalPbocTlvFormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    /***
     * 验证服务端下发证书CN部分是否为：券商名称+"委托服务器证书"
     * 举例,如果id为招商证券,则证书的subject前缀必须是"招商证券委托服务器证书",如不符合则报错
     */
    public static boolean verifyCertCN(final String cerServer){
        try {
            PbocTlvElement root = PbocTlvParser.parse(decodeCert(cerServer));
            List<PbocTlvElement> elements = root.getSubElements();
            PbocTlvElement certContentElement = elements.get(0);
            List<PbocTlvElement> elementSub = certContentElement.getSubElements();
            PbocTlvElement keyContentElement = elementSub.get(5);
            List<PbocTlvElement> elementSub1 = keyContentElement.getSubElements();
            byte[] cn = elementSub1.get(2).getValue();

            if(cn == null || cn.length == 0){
                return false;
            }
            int index1 = 0;
            int index2 = 0;
            while(true) {
                if(cn[index1] == ref[0])
                {
                    if(cn[index1 + 1] == ref[1])
                        if(cn[index1 + 2] == ref[2])
                            if(cn[index1 + 3] == ref[3])
                                if(cn[index1 + 4] == ref[4])
                                {
                                    index2 = index1 + 6;
                                    break;
                                }
                }
                index1++;
                if(index1 >= cn.length)   break;
            }
            if(index2 > 0) {
                int len = cn[index2];
                index2++;
                byte[] ary = new byte[len];
                System.arraycopy(cn, index2, ary, 0, len);
                String tradeName = new String(ary,"UTF-8");
                String str = MainActivity.QSFlag + "委托服务器证书";
                if(tradeName.indexOf(str) == 0) {
                    return true;
                }
            }
        } catch (IllegalPbocTlvFormatException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return false;
    }

    /***
     * 验证服务端下发证书是否为更高级级别的证书（本地证书）所颁发
     */
    public static boolean verifyCert(final String cerLocal, final String cerServer)  throws IllegalPbocTlvFormatException,InvalidSignDataException,InvalidKeyDataException {
        //本地公钥
        byte[] pubKey = getPublicKey(cerLocal);
        PbocTlvElement root = PbocTlvParser.parse(decodeCert(cerServer));
        List<PbocTlvElement> elements = root.getSubElements();
        PbocTlvElement certContentElement = elements.get(0);

        byte[] certContentTag = certContentElement.getTag();//tag
        byte[] certContentLength = certContentElement.getLength();//length
        byte[] certContentValue = certContentElement.getValue();//value
        if (certContentValue == null){
            throw new IllegalPbocTlvFormatException("illegal cert, tag 1 (cert info) has no value");
        }
        byte[] certContent = new byte[certContentTag.length + certContentLength.length + certContentValue.length];//被签名数据
        System.arraycopy(certContentTag, 0, certContent, 0, certContentTag.length);
        System.arraycopy(certContentLength, 0, certContent, certContentTag.length, certContentLength.length);
        System.arraycopy(certContentValue, 0, certContent, certContentTag.length + certContentLength.length, certContentValue.length);

        byte[] certSign = elements.get(2).getValue();
        if (certSign == null){
            throw new IllegalPbocTlvFormatException("illegal cert, tag 3 (cert sign) has no value");
        }
        SM2Cipher sm2Cipher = new SM2Cipher();
        return sm2Cipher.verifySignByASN1(DEFAULT_USER_ID, pubKey, SM3_Util.sm3hash(certContent), certSign);
    }

    /**
     * tlv格式证书获取公钥
     * **/
    public static byte[] getPublicKey(final String cerSource) throws IllegalPbocTlvFormatException {
        PbocTlvElement root = PbocTlvParser.parse(decodeCert(cerSource));
        List<PbocTlvElement> elements = root.getSubElements();
        if (elements == null || elements.size() != 3){
            throw new IllegalPbocTlvFormatException("illegal cert, it must have 3 tags, cert info / flag / sign");
        }
        //被签名数据
        PbocTlvElement certContentElement = elements.get(0);
        List<PbocTlvElement> elementSub = certContentElement.getSubElements();
        //被签名数据第七位
        PbocTlvElement keyContentElement = elementSub.get(6);
        List<PbocTlvElement> elementSub1 = keyContentElement.getSubElements();
        byte[] key = elementSub1.get(1).getValue();
        return filterZero(key);
    }

    /**
     * 过滤掉头部的0x00
     * */
    private static byte[] filterZero(final byte[] sourceData){
        byte[] _sourceData = sourceData;
        int startIndex = 0;
        for (int i = 0 ; i < sourceData.length ; i++){
            if (sourceData[i] != 0x00){
                break;
            }
            startIndex++;
        }
        if (startIndex > 0){
            _sourceData = new byte[sourceData.length - startIndex];
            System.arraycopy(sourceData, startIndex, _sourceData, 0, _sourceData.length);
        }
        return _sourceData;
    }

    /**
     * 1、去除x509证书头尾
     * 2、pem编码转der
     *
     * */
    private static byte[] decodeCert(final String cerSource){
        if(TextUtils.isEmpty(cerSource)){
            return null;
        }
        String temp = cerSource.replace("-----BEGIN CERTIFICATE-----","")
                .replace("-----END CERTIFICATE-----","").trim();
        return Base64Utils.decode(temp);
    }

    public static String getCertServer() {
        return certServer;
    }

    public static void setCertServer(String certServer) {
        SMHelper.certServer = certServer;
    }

    public static byte[] getKey() {
        return key;
    }

    public static void setKey(byte[] key) {
        SMHelper.key = key;
    }

    public static SM2Cipher.KeyPair getKeyPair() {
        return keyPair;
    }
}
