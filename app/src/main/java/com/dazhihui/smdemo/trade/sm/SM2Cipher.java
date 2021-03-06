package com.dazhihui.smdemo.trade.sm;

import com.dazhihui.smdemo.trade.sm.exception.InvalidCertificateException;
import com.dazhihui.smdemo.trade.sm.exception.InvalidCryptoDataException;
import com.dazhihui.smdemo.trade.sm.exception.InvalidCryptoParamsException;
import com.dazhihui.smdemo.trade.sm.exception.InvalidKeyDataException;
import com.dazhihui.smdemo.trade.sm.exception.InvalidKeyException;
import com.dazhihui.smdemo.trade.sm.exception.InvalidSignDataException;
import com.dazhihui.smdemo.trade.sm.tlv.IllegalPbocTlvFormatException;
import com.dazhihui.smdemo.trade.sm.tlv.PbocTlvElement;
import com.dazhihui.smdemo.trade.sm.tlv.PbocTlvParser;
import com.dazhihui.smdemo.trade.sm.util.CertificateUtils;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static com.dazhihui.smdemo.trade.sm.SM3_Util.join;
import static com.dazhihui.smdemo.trade.sm.SM3_Util.sm3hash;
import static com.dazhihui.smdemo.trade.sm.util.CommonUtils.byteConvert32Bytes;

/**
 * <p>SM2加密器</p>
 *
 * <p>注意:由于对象内存在buffer, 请勿多线程同时操作一个实例, 每次new一个Cipher使用, 或使用ThreadLocal保持每个线程一个Cipher实例.</p>
 *
 * Created by S.Violet on 2016/8/22.
 */
public class SM2Cipher {

    /**
     * SM2的ECC椭圆曲线参数
     */
    public static final BigInteger SM2_ECC_P = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF", 16);
    public static final BigInteger SM2_ECC_A = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC", 16);
    public static final BigInteger SM2_ECC_B = new BigInteger("28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93", 16);
    public static final BigInteger SM2_ECC_N = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16);
    public static final BigInteger SM2_ECC_GX = new BigInteger("32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7", 16);
    public static final BigInteger SM2_ECC_GY = new BigInteger("BC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0", 16);

    //测试曲线
//    private static final BigInteger SM2_ECC_P = new BigInteger("8542D69E4C044F18E8B92435BF6FF7DE457283915C45517D722EDB8B08F1DFC3", 16);
//    private static final BigInteger SM2_ECC_A = new BigInteger("787968B4FA32C3FD2417842E73BBFEFF2F3C848B6831D7E0EC65228B3937E498", 16);
//    private static final BigInteger SM2_ECC_B = new BigInteger("63E4C6D3B23B0C849CF84241484BFE48F61D59A5B16BA06E6E12D1DA27C5249A", 16);
//    private static final BigInteger SM2_ECC_N = new BigInteger("8542D69E4C044F18E8B92435BF6FF7DD297720630485628D5AE74EE7C32E79B7", 16);
//    private static final BigInteger SM2_ECC_GX = new BigInteger("421DEBD61B62EAB6746434EBC3CC315E32220B3BADD50BDC4C4E6C147FEDD43D", 16);
//    private static final BigInteger SM2_ECC_GY = new BigInteger("0680512BCBB42C07D47349D2153B70C4E5D7FDFCBFA36EA1A85841B9E46E09A2", 16);

    public static final int SHARE_KEY_LENGTH = 16;

    private ECCurve.Fp curve;//ECC曲线
    private ECPoint.Fp pointG;//基点
    private ECKeyPairGenerator keyPairGenerator;//密钥对生成器
    private Type type;//密文格式

    private ECDomainParameters domainParams;
    private ECPoint alternateKeyPoint;
    private SM3Digest alternateKeyDigest;
    private SM3Digest c3Digest;
    private int alternateKeyCount;
    private byte alternateKey[];
    private byte alternateKeyOff;
    private static int w = (int) Math.ceil(SM2_ECC_N.bitLength() * 1.0 / 2) - 1;
	private static BigInteger _2w = new BigInteger("2").pow(w);
	private SecureRandom secureRandom;

    public SM2Cipher(){
        this(Type.C1C3C2);
    }

    /**
     * 默认椭圆曲线参数的SM2加密器
     *
     * @param type 密文格式
     */
    public SM2Cipher(Type type) {
        this(
                new SecureRandom(),
                type
        );
    }

    /**
     * 默认椭圆曲线参数的SM2加密器
     *
     * @param secureRandom 秘钥生成随机数
     * @param type         密文格式
     */
    public SM2Cipher(SecureRandom secureRandom, Type type) {
        this(
                secureRandom,
                type,
                SM2_ECC_P,
                SM2_ECC_A,
                SM2_ECC_B,
                SM2_ECC_N,
                SM2_ECC_GX,
                SM2_ECC_GY
        );
    }

    /**
     * 默认椭圆曲线参数的SM2加密器
     *
     * @param secureRandom 秘钥生成随机数
     * @param type         密文格式
     * @param eccP         p
     * @param eccA         a
     * @param eccB         b
     * @param eccN         n
     * @param eccGx        gx
     * @param eccGy        gy
     */
    public SM2Cipher(SecureRandom secureRandom, Type type, BigInteger eccP, BigInteger eccA, BigInteger eccB, BigInteger eccN, BigInteger eccGx, BigInteger eccGy) {

        if (type == null) {
            throw new InvalidCryptoParamsException("[SM2]type of the SM2Cipher is null");
        }

        if (eccP == null || eccA == null || eccB == null || eccN == null || eccGx == null || eccGy == null) {
            throw new InvalidCryptoParamsException("[SM2]ecc params of the SM2Cipher is null");
        }

        if (secureRandom == null) {
            secureRandom = new SecureRandom();
        }
        this.secureRandom = secureRandom;

        this.type = type;

        //曲线
        ECFieldElement.Fp gxFieldElement = new ECFieldElement.Fp(eccP, eccGx);
        ECFieldElement.Fp gyFieldElement = new ECFieldElement.Fp(eccP, eccGy);
        this.curve = new ECCurve.Fp(eccP, eccA, eccB);

        //密钥对生成器
        this.pointG = new ECPoint.Fp(curve, gxFieldElement, gyFieldElement);
        domainParams = new ECDomainParameters(curve, pointG, eccN);
        ECKeyGenerationParameters keyGenerationParams = new ECKeyGenerationParameters(domainParams, secureRandom);
        this.keyPairGenerator = new ECKeyPairGenerator();
        this.keyPairGenerator.init(keyGenerationParams);
    }
    

    public ECKeyPairGenerator getKeyPairGenerator() {
		return keyPairGenerator;
	}

	public void setKeyPairGenerator(ECKeyPairGenerator keyPairGenerator) {
		this.keyPairGenerator = keyPairGenerator;
	}

	/**
     * @return 产生SM2公私钥对(随机)
     */
    public KeyPair generateKeyPair() {
        AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();
        ECPrivateKeyParameters privateKeyParams = (ECPrivateKeyParameters) keyPair.getPrivate();
        ECPublicKeyParameters publicKeyParams = (ECPublicKeyParameters) keyPair.getPublic();
        BigInteger privateKey = privateKeyParams.getD();
        ECPoint publicKey = publicKeyParams.getQ();
        return new KeyPair(publicKey, privateKey);
    }

    /**
     * SM2加密
     *
     * @param publicKey 公钥
     * @param data      数据
     */
    public byte[] encrypt(byte[] publicKey, byte[] data) throws InvalidKeyDataException {
        EncryptedData encryptedData = encryptInner(publicKey, data);
        if (encryptedData == null) {
            return null;
        }
        byte[] c1 = encryptedData.c1.getEncoded();
        byte[] c2 = encryptedData.c2;
        byte[] c3 = encryptedData.c3;

        //拼凑结果
        byte[] result = new byte[97 + c2.length];
        switch (type) {
            case C1C2C3:
                System.arraycopy(c1, 0, result, 0, 65);//C1:Point, 标志位1byte, 数据64byte
                System.arraycopy(c2, 0, result, 65, c2.length);//C2:加密数据
                System.arraycopy(c3, 0, result, 65 + c2.length, 32);//C3:摘要 32byte
                break;
            case C1C3C2:
                System.arraycopy(c1, 0, result, 0, 65);//C1:Point, 标志位1byte, 数据64byte
                System.arraycopy(c3, 0, result, 65, 32);//C3:摘要 32byte
                System.arraycopy(c2, 0, result, 97, c2.length);//C2:加密数据
                break;
            default:
                throw new InvalidCryptoParamsException("[SM2:Encrypt]invalid type(" + String.valueOf(type) + ")");
        }

        return result;
    }

    /**
     * SM2加密, ASN.1编码
     *
     * @param publicKey 公钥
     * @param data      数据
     */
    public byte[] encryptToASN1(byte[] publicKey, byte[] data) throws InvalidCryptoDataException, InvalidKeyDataException {
        EncryptedData encryptedData = encryptInner(publicKey, data);
        if (encryptedData == null) {
            return null;
        }
        ECPoint c1 = encryptedData.c1;
        byte[] c2 = encryptedData.c2;
        byte[] c3 = encryptedData.c3;

        DERInteger x = new DERInteger(c1.getX().toBigInteger());
        DERInteger y = new DERInteger(c1.getY().toBigInteger());
        DEROctetString derC2 = new DEROctetString(c2);
        DEROctetString derC3 = new DEROctetString(c3);
        ASN1EncodableVector vector = new ASN1EncodableVector();
        vector.add(x);
        vector.add(y);
        switch (type) {
            case C1C2C3:
                vector.add(derC2);
                vector.add(derC3);
                break;
            case C1C3C2:
                vector.add(derC3);
                vector.add(derC2);
                break;
            default:
                throw new InvalidCryptoParamsException("[SM2:EncryptASN1]invalid type(" + String.valueOf(type) + ")");
        }
        DERSequence seq = new DERSequence(vector);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DEROutputStream derOutputStream = new DEROutputStream(byteArrayOutputStream);
        try {
            derOutputStream.writeObject(seq);
        } catch (IOException e) {
            throw new InvalidCryptoDataException("[SM2:encrypt:ASN1]error while parse encrypted data to ASN.1", e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private EncryptedData encryptInner(byte[] publicKey, byte[] data) throws InvalidKeyDataException {
        if (publicKey == null || publicKey.length == 0) {
            throw new InvalidCryptoParamsException("[SM2:Encrypt]key is null");
        }

        if (data == null || data.length == 0) {
            return null;
        }

        //C2位数据域
        byte[] c2 = new byte[data.length];
        System.arraycopy(data, 0, c2, 0, data.length);
        ECPoint keyPoint;
        try {
            keyPoint = curve.decodePoint(publicKey);
        } catch (Exception e) {
            throw new InvalidKeyDataException("[SM2:Encrypt]invalid key data(format)", e);
        }

        AsymmetricCipherKeyPair generatedKey = keyPairGenerator.generateKeyPair();
        ECPrivateKeyParameters privateKeyParams = (ECPrivateKeyParameters) generatedKey.getPrivate();
        ECPublicKeyParameters publicKeyParams = (ECPublicKeyParameters) generatedKey.getPublic();
        BigInteger privateKey = privateKeyParams.getD();
        ECPoint c1 = publicKeyParams.getQ();
        this.alternateKeyPoint = keyPoint.multiply(privateKey);
        reset();

        this.c3Digest.update(c2);
        for (int i = 0; i < c2.length; i++) {
            if (alternateKeyOff >= alternateKey.length) {
                nextKey();
            }
            c2[i] ^= alternateKey[alternateKeyOff++];
        }

        byte p[] = byteConvert32Bytes(alternateKeyPoint.getY().toBigInteger());
        this.c3Digest.update(p);
        byte[] c3 = this.c3Digest.doFinal();
        reset();

        return new EncryptedData(c1, c2, c3);
    }

    /**
     * SM2解密
     *
     * @param privateKey 私钥
     * @param data       数据
     */
    public byte[] decrypt(byte[] privateKey, byte[] data) throws InvalidKeyException, InvalidCryptoDataException {
        if (data == null || data.length == 0) {
            return null;
        }

        if (data.length <= 97) {
            throw new InvalidCryptoDataException("[SM2:Decrypt]invalid encrypt data, length <= 97 bytes");
        }

        byte[] c1 = new byte[65];
        byte[] c2 = new byte[data.length - 97];
        byte[] c3 = new byte[32];
        switch (type) {
            case C1C2C3:
                System.arraycopy(data, 0, c1, 0, c1.length);//C1:Point, 标志位1byte, 数据64byte
                System.arraycopy(data, c1.length, c2, 0, c2.length);//C2:加密数据
                System.arraycopy(data, c1.length + c2.length, c3, 0, c3.length);//C3:摘要 32byte
                break;
            case C1C3C2:
                System.arraycopy(data, 0, c1, 0, c1.length);//C1:Point, 标志位1byte, 数据64byte
                System.arraycopy(data, c1.length, c3, 0, c3.length);//C3:摘要 32byte
                System.arraycopy(data, c1.length + c3.length, c2, 0, c2.length);//C2:加密数据
                break;
            default:
                throw new InvalidCryptoParamsException("[SM2:Decrypt]invalid type(" + String.valueOf(type) + ")");
        }

        return decryptInner(privateKey, c1, c2, c3);
    }

    /**
     * SM2解密, ASN.1编码
     *
     * @param privateKey 私钥
     * @param data       数据
     */
    public byte[] decryptFromASN1(byte[] privateKey, byte[] data) throws InvalidKeyException, InvalidCryptoDataException {
        if (data == null || data.length == 0) {
            return null;
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        ASN1InputStream asn1InputStream = new ASN1InputStream(byteArrayInputStream);
        DERObject derObj;
        try {
            derObj = asn1InputStream.readObject();
        } catch (IOException e) {
            throw new InvalidCryptoDataException("[SM2:decrypt:ASN1]invalid encrypted data", e);
        }
        ASN1Sequence asn1 = (ASN1Sequence) derObj;
        DERInteger x = (DERInteger) asn1.getObjectAt(0);
        DERInteger y = (DERInteger) asn1.getObjectAt(1);
        ECPoint c1;
        try {
            c1 = curve.createPoint(x.getValue(), y.getValue(), true);
        } catch (Exception e) {
            throw new InvalidCryptoDataException("[SM2:decrypt:ASN1]invalid encrypted data, c1", e);
        }
        byte[] c2;
        byte[] c3;
        switch (type) {
            case C1C2C3:
                c2 = ((DEROctetString) asn1.getObjectAt(2)).getOctets();
                c3 = ((DEROctetString) asn1.getObjectAt(3)).getOctets();
                break;
            case C1C3C2:
                c3 = ((DEROctetString) asn1.getObjectAt(2)).getOctets();
                c2 = ((DEROctetString) asn1.getObjectAt(3)).getOctets();
                break;
            default:
                throw new InvalidCryptoParamsException("[SM2:Decrypt:ASN1]invalid type(" + String.valueOf(type) + ")");
        }

        return decryptInner(privateKey, c1.getEncoded(), c2, c3);
    }

    private byte[] decryptInner(byte[] privateKey, byte[] c1, byte[] c2, byte[] c3) throws InvalidKeyException, InvalidCryptoDataException {
        if (privateKey == null || privateKey.length == 0) {
            throw new InvalidCryptoParamsException("[SM2:Decrypt]key is null");
        }

        if (c1 == null || c1.length <= 0 || c2 == null || c2.length <= 0 || c3 == null || c3.length <= 0) {
            throw new InvalidCryptoDataException("[SM2:Decrypt]invalid encrypt data, c1 / c2 / c3 is null or empty");
        }

        BigInteger decryptKey = new BigInteger(1, privateKey);
        ECPoint c1Point;
        try {
            c1Point = curve.decodePoint(c1);
        } catch (Exception e) {
            throw new InvalidCryptoDataException("[SM2:Decrypt]invalid encrypt data, c1 invalid", e);
        }
        this.alternateKeyPoint = c1Point.multiply(decryptKey);
        reset();

        for (int i = 0; i < c2.length; i++) {
            if (alternateKeyOff >= alternateKey.length) {
                nextKey();
            }
            c2[i] ^= alternateKey[alternateKeyOff++];
        }

        this.c3Digest.update(c2, 0, c2.length);

        byte p[] = byteConvert32Bytes(alternateKeyPoint.getY().toBigInteger());
        this.c3Digest.update(p, 0, p.length);
        byte[] verifyC3 = this.c3Digest.doFinal();

        if (!Arrays.equals(verifyC3, c3)) {
            throw new InvalidKeyException("[SM2:Decrypt]invalid key, c3 is not match");
        }

        reset();

        //返回解密结果
        return c2;
    }

    private void reset() {
        this.alternateKeyDigest = new SM3Digest();
        this.c3Digest = new SM3Digest();

        byte p[] = byteConvert32Bytes(alternateKeyPoint.getX().toBigInteger());
        this.alternateKeyDigest.update(p);
        this.c3Digest.update(p, 0, p.length);

        p = byteConvert32Bytes(alternateKeyPoint.getY().toBigInteger());
        this.alternateKeyDigest.update(p);
        this.alternateKeyCount = 1;
        nextKey();
    }

    private void nextKey() {
        SM3Digest digest = new SM3Digest(this.alternateKeyDigest);
        digest.update((byte) (alternateKeyCount >> 24 & 0xff));
        digest.update((byte) (alternateKeyCount >> 16 & 0xff));
        digest.update((byte) (alternateKeyCount >> 8 & 0xff));
        digest.update((byte) (alternateKeyCount & 0xff));
        alternateKey = digest.doFinal();
        this.alternateKeyOff = 0;
        this.alternateKeyCount++;
    }

    /**
     * 签名
     *
     * @param userId     用户ID
     * @param privateKey 私钥
     * @param sourceData 数据
     * @return 签名数据{r, s}
     */
    public BigInteger[] sign(byte[] userId, byte[] privateKey, byte[] sourceData) {
        if (privateKey == null || privateKey.length == 0) {
            throw new InvalidCryptoParamsException("[SM2:sign]key is null");
        }

        if (sourceData == null || sourceData.length == 0) {
            return null;
        }

        //私钥, 私钥和基点生成秘钥点
        BigInteger key = new BigInteger(1,privateKey);
        ECPoint keyPoint = pointG.multiply(key);

        //Z
//        SM3Digest digest = new SM3Digest();
        byte[] z = getZ(userId, keyPoint);

        //对数据做摘要
//        digest.update(z, 0, z.length);
//        digest.update(sourceData);
//        byte[] digestData = digest.doFinal();
        byte[] digestData = sm3hash(z,sourceData);

        //签名数据{r, s}
        return signInner(digestData, key, keyPoint);
    }

    /**
     * 签名, 输出(r + s)格式的标准签名
     *
     * @param userId     用户ID
     * @param privateKey 私钥
     * @param sourceData 数据
     * @return 签名数据{r, s}
     */
    public byte[] signToBytes(byte[] userId, byte[] privateKey, byte[] sourceData) {
        BigInteger[] rs = sign(userId, privateKey, sourceData);
        byte[] signData = new byte[64];//32bytes r, 32bytes s
        byte[] rBytes = byteConvert32Bytes(rs[0]);
        byte[] sBytes = byteConvert32Bytes(rs[1]);
        System.arraycopy(
                rBytes,
                rBytes.length > 32 ? rBytes.length - 32 : 0,
                signData,
                rBytes.length >= 32 ? 0 : 32 - rBytes.length,
                rBytes.length >= 32 ? 32 : rBytes.length);
        System.arraycopy(
                sBytes,
                sBytes.length > 32 ? sBytes.length - 32 : 0,
                signData,
                sBytes.length >= 32 ? 32 : 32 + (32 - sBytes.length),
                sBytes.length >= 32 ? 32 : sBytes.length);
        return signData;
    }

    /**
     * 签名(ASN.1编码)
     *
     * @param userId     用户ID
     * @param privateKey 私钥
     * @param sourceData 数据
     * @return 签名数据 byte[] ASN.1编码
     */
    public byte[] signToASN1(byte[] userId, byte[] privateKey, byte[] sourceData) {
        BigInteger[] signData = sign(userId, privateKey, sourceData);
        //签名数据序列化
        DERInteger derR = new DERInteger(signData[0]);//r
        DERInteger derS = new DERInteger(signData[1]);//s
        ASN1EncodableVector vector = new ASN1EncodableVector();
        vector.add(derR);
        vector.add(derS);
        DERObject sign = new DERSequence(vector);
        return sign.getDEREncoded();
    }

    /**
     * 验签
     *
     * @param userId     用户ID
     * @param publicKey  公钥
     * @param sourceData 数据
     * @param signR      签名数据r
     * @param signS      签名数据s
     * @return true:签名有效
     */
    public boolean verifySign(byte[] userId, byte[] publicKey, byte[] sourceData, BigInteger signR, BigInteger signS) throws InvalidKeyDataException {
        if (publicKey == null || publicKey.length == 0) {
            throw new InvalidCryptoParamsException("[SM2:verifySign]key is null");
        }

        if (sourceData == null || sourceData.length == 0 || signR == null || signS == null) {
            return false;
        }

        //公钥
        ECPoint key;
        try {
            key = curve.decodePoint(publicKey);
        } catch (Exception e) {
            throw new InvalidKeyDataException("[SM2:verifySign]invalid public key (format)", e);
        }

        //Z
        SM3Digest digest = new SM3Digest();
        byte[] z = getZ(userId, key);

        //对数据摘要
        digest.update(z, 0, z.length);
        digest.update(sourceData, 0, sourceData.length);
        byte[] digestData = digest.doFinal();

        //验签
        return signR.equals(verifyInner(digestData, key, signR, signS));
    }

    /**
     * 验签, (r + s)格式的标准签名
     *
     * @param userId     用户ID
     * @param publicKey  公钥
     * @param sourceData 数据
     * @param signData   签名数据, r + s格式的标准签名
     * @return true 签名有效
     * @throws InvalidSignDataException ASN.1编码无效
     */
    public boolean verifySignByBytes(byte[] userId, byte[] publicKey, byte[] sourceData, byte[] signData) throws InvalidSignDataException, InvalidKeyDataException {
        if (signData == null || signData.length != 64) {
            throw new InvalidSignDataException("[SM2:verifySignByBytes]invalid sign data, length is not 64 bytes (r + s)");
        }
        byte[] rBytes = new byte[32];
        byte[] sBytes = new byte[32];
        System.arraycopy(signData, 0, rBytes, 0, 32);
        System.arraycopy(signData, 32, sBytes, 0, 32);
        BigInteger r;
        BigInteger s;
        try {
            r = new BigInteger(1, rBytes);
            s = new BigInteger(1, sBytes);
        } catch (Exception e) {
            throw new InvalidSignDataException("[SM2:verifySignByBytes]invalid sign data, can not parse to r and s (BigInteger)");
        }
        return verifySign(userId, publicKey, sourceData, r, s);
    }

    /**
     * 验签(ASN.1编码签名)
     *
     * @param userId     用户ID
     * @param publicKey  公钥
     * @param sourceData 数据
     * @param signData   签名数据(ASN.1编码)
     * @return true 签名有效
     * @throws InvalidSignDataException ASN.1编码无效
     */
    @SuppressWarnings("unchecked")
    public boolean verifySignByASN1(byte[] userId, byte[] publicKey, byte[] sourceData, byte[] signData) throws InvalidSignDataException, InvalidKeyDataException {
        byte[] _signData = signData;

        //过滤头部的0x00
        int startIndex = 0;
        for (int i = 0 ; i < signData.length ; i++){
            if (signData[i] != 0x00){
                break;
            }
            startIndex++;
        }
        if (startIndex > 0){
            _signData = new byte[signData.length - startIndex];
            System.arraycopy(signData, startIndex, _signData, 0, _signData.length);
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(_signData);
        ASN1InputStream asn1InputStream = new ASN1InputStream(byteArrayInputStream);
        Enumeration<DERInteger> signObj;
        try {
            DERObject derObj = asn1InputStream.readObject();
            signObj = ((ASN1Sequence) derObj).getObjects();
        } catch (IOException e) {
            throw new InvalidSignDataException("[SM2:verifySign]invalid sign data (ASN.1)", e);
        }
        BigInteger r = signObj.nextElement().getValue();
        BigInteger s = signObj.nextElement().getValue();

        //验签
        return verifySign(userId, publicKey, sourceData, r, s);
    }

    /**
     * <p>使用X509证书中的主体公钥进行验签</p>
     * <p>
     * <p>注意:本方法不验证证书本身的合法性, 需要自行验证; 本方法不验证证书实体公钥的算法标识;</p>
     *
     * @param certData   X509证书数据(ASN.1, 非Base64)
     * @param sourceData 数据
     * @param signData   签名(r + s)
     * @return true:签名合法
     */
    public boolean verifySignByX509Cert(byte[] certData, byte[] sourceData, byte[] signData) throws InvalidCertificateException, InvalidSignDataException, InvalidKeyDataException {
        byte[] publicKey = null;
        try {
            X509CertificateStructure cert = CertificateUtils.parseX509(certData);
            SubjectPublicKeyInfo publicInfo = cert.getSubjectPublicKeyInfo();
            publicKey = publicInfo.getPublicKeyData().getBytes();
        } catch (Exception e) {
            throw new InvalidCertificateException("[SM2:verifySignByX509Cert]illegal cert", e);
        }
        if (publicKey == null) {
            throw new InvalidCertificateException("[SM2:verifySignByX509Cert]null public key in cert");
        }
        if (publicKey.length != 65) {
            throw new InvalidCertificateException("[SM2:verifySignByX509Cert]illegal public key in cert, length is not 65 bytes (0x04 + x + y)");
        }

        return verifySignByBytes(null, publicKey, sourceData, signData);
    }

    /**
     * 根据公钥验证证书的合法性
     * @param certData X509证书数据(ASN.1, 非Base64, TLV数据格式)
     * @param publicKey 公钥
     * @return true:合法
     */
    public boolean verifyCertByPublicKey(byte[] certData, byte[] publicKey) throws IOException, IllegalPbocTlvFormatException, InvalidSignDataException, InvalidKeyDataException {
        PbocTlvElement root = PbocTlvParser.parse(certData);
        List<PbocTlvElement> elements = root.getSubElements();
        if (elements == null || elements.size() != 3){
            throw new IOException("illegal cert, it must have 3 tags, cert info / flag / sign");
        }

        //被签名数据
        PbocTlvElement certContentElement = elements.get(0);
        byte[] certContentTag = certContentElement.getTag();//tag
        byte[] certContentLength = certContentElement.getLength();//length
        byte[] certContentValue = certContentElement.getValue();//value
        if (certContentValue == null){
            throw new IOException("illegal cert, tag 1 (cert info) has no value");
        }
        byte[] certContent = new byte[certContentTag.length + certContentLength.length + certContentValue.length];//被签名数据
        System.arraycopy(certContentTag, 0, certContent, 0, certContentTag.length);
        System.arraycopy(certContentLength, 0, certContent, certContentTag.length, certContentLength.length);
        System.arraycopy(certContentValue, 0, certContent, certContentTag.length + certContentLength.length, certContentValue.length);

        byte[] certSign = elements.get(2).getValue();
        if (certSign == null){
            throw new IOException("illegal cert, tag 3 (cert sign) has no value");
        }

        return verifySignByASN1(null, publicKey, certContent, certSign);
    }

    private byte[] getZ(byte[] userId, ECPoint userKey) {
        SM3Digest digest = new SM3Digest();

        int len = userId.length * 8;
        digest.update((byte) (len >> 8 & 0xFF));
        digest.update((byte) (len & 0xFF));
        digest.update(userId);

        byte[] p = byteConvert32Bytes(SM2_ECC_A);
        digest.update(p);

        p = byteConvert32Bytes(SM2_ECC_B);
        digest.update(p);

        p = byteConvert32Bytes(SM2_ECC_GX);
        digest.update(p);

        p = byteConvert32Bytes(SM2_ECC_GY);
        digest.update(p);

        p = byteConvert32Bytes(userKey.getX().toBigInteger());
        digest.update(p);

        p = byteConvert32Bytes(userKey.getY().toBigInteger());
        digest.update(p);

        return digest.doFinal();
    }

    /**
     * @return {r, s}
     */
    private BigInteger[] signInner(byte[] digestData, BigInteger key, ECPoint keyPoint) {
        BigInteger e = new BigInteger(1, digestData);
        BigInteger k;
        ECPoint kp;
        BigInteger r;
        BigInteger s;
        do {
            do {
                //正式环境
                AsymmetricCipherKeyPair keypair = keyPairGenerator.generateKeyPair();
                ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keypair.getPrivate();
                ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keypair.getPublic();
                k = privateKey.getD();
                kp = publicKey.getQ();

                //国密规范测试 随机数k
//                String kS = "6CB28D99385C175C94F94E934817663FC176D925DD72B727260DBAAE1FB2F96F";
//                k = new BigInteger(kS, 16);
//                kp = this.pointG.multiply(k);

                //r
                r = e.add(kp.getX().toBigInteger());
                r = r.mod(SM2_ECC_N);
            } while (r.equals(BigInteger.ZERO) || r.add(k).equals(SM2_ECC_N));

            //(1 + dA)~-1
            BigInteger da_1 = key.add(BigInteger.ONE);
            da_1 = da_1.modInverse(SM2_ECC_N);

            //s
            s = r.multiply(key);
            s = k.subtract(s).mod(SM2_ECC_N);
            s = da_1.multiply(s).mod(SM2_ECC_N);
        } while (s.equals(BigInteger.ZERO));

        return new BigInteger[]{r, s};
    }

    private BigInteger verifyInner(byte digestData[], ECPoint userKey, BigInteger r, BigInteger s) {
        BigInteger e = new BigInteger(1, digestData);
        BigInteger t = r.add(s).mod(SM2_ECC_N);
        if (t.equals(BigInteger.ZERO)) {
            return null;
        } else {
            ECPoint x1y1 = pointG.multiply(s);
            x1y1 = x1y1.add(userKey.multiply(t));
            return e.add(x1y1.getX().toBigInteger()).mod(SM2_ECC_N);
        }
    }
    
    /************************************密钥协商相关*********************************************/
    

	/**
	 * 取得用户标识字节数组
	 * 
	 * @param idaBytes
	 * @param aPublicKey
	 * @return
	 */
	public static byte[] ZA(byte[] idaBytes, ECPoint aPublicKey) {
		int entlenA = idaBytes.length * 8;
		byte[] ENTLA = new byte[] { (byte) (entlenA & 0xFF00), (byte) (entlenA & 0x00FF) };
		
		byte[] ZA = sm3hash(ENTLA, idaBytes, byteConvert32Bytes(SM2_ECC_A),
				byteConvert32Bytes(SM2_ECC_B), 
				byteConvert32Bytes(SM2_ECC_GX),
				byteConvert32Bytes(SM2_ECC_GY),
				byteConvert32Bytes(aPublicKey.getX().toBigInteger()),
				byteConvert32Bytes(aPublicKey.getY().toBigInteger()));
		return ZA;
	}
	
	
	/**
	 * 密钥派生函数
	 * 
	 * @param Z
	 * @param klen
	 *            生成klen字节数长度的密钥
	 * @return
	 */
	private static byte[] KDF(byte[] Z, int klen) {
		int ct = 1;
		int end = (int) Math.ceil(klen * 1.0 / 32);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			for (int i = 1; i < end; i++) {
				baos.write(sm3hash(Z, SM3.toByteArray(ct)));
				ct++;
			}
			byte[] last = sm3hash(Z, SM3.toByteArray(ct));
			if (klen % 32 == 0) {
				baos.write(last);
			} else
				baos.write(last, 0, klen % 32);
			return baos.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	

	/**
	 * 传输实体类
	 * 
	 * @author Potato
	 *
	 */
	public static class TransportEntity implements Serializable {
		final byte[] R; //R点
		final byte[] S; //验证S
		final byte[] Z; //用户标识
		final byte[] K; //公钥
		
		public TransportEntity(byte[] r, byte[] s,byte[] z,byte[] k) {
			R = r;
			S = s;
			Z=z;
			K=k;
		}
		
		public byte[] getR() {
			return R;
		}

		public byte[] getS() {
			return S;
		}

		public byte[] getZ() {
			return Z;
		}

		public byte[] getK() {
			return K;
		}
		
	}

	/**
	 * 密钥协商辅助类
	 * 
	 * @author Potato
	 *
	 */
	public static class KeyExchange {
		BigInteger rA;
		ECPoint RA;
		ECPoint V;
		byte[] Z;
		byte[] key;
		
		KeyPair keyPair;
		
		private ECCurve.Fp curve;//ECC曲线
	    private ECPoint.Fp pointG;//基点
	    private ECDomainParameters domainParams;
	    private SecureRandom secureRandom;
	    
		public KeyExchange(byte[] idBytes,KeyPair keyPair,SM2Cipher sm2Cipher) {
			this.keyPair = keyPair;
			this.Z=ZA(idBytes, keyPair.getPublicKeyEc());
			curve = sm2Cipher.getCurve();
			pointG = sm2Cipher.getPointG();
			domainParams = sm2Cipher.getDomainParams();
			secureRandom = sm2Cipher.getSecureRandom();
		}

		/**
		 * 密钥协商发起第一步
		 * 
		 * @return
		 */
		public TransportEntity keyExchange_1() {
			rA = random(SM2_ECC_N);
//			rA = new BigInteger("83A2C9C8B96E5AF70BD480B472409A9A327257F1EBB73F5B073354B248668563", 16);
			RA = pointG.multiply(rA);
			return new TransportEntity(RA.getEncoded(), null,Z,keyPair.getPublicKeyEc().getEncoded());
		}

		/**
		 * 密钥协商响应方
		 * 
		 * @param entity 传输实体
		 * @return
		 */
		public TransportEntity keyExchange_2(TransportEntity entity) {
			BigInteger rB = random(SM2_ECC_N);
//			BigInteger rB=new BigInteger("33FE21940342161C55619C4A0C060293D543C80AF19748CE176D83477DE71C80",16);
			ECPoint RB = pointG.multiply(rB);
			
			this.rA=rB;
			this.RA=RB;

			BigInteger x2 = RB.getX().toBigInteger();
			
			x2 = _2w.add(x2.and(_2w.subtract(BigInteger.ONE)));
			
			BigInteger tB = keyPair.getPrivateKeyBi().add(x2.multiply(rB)).mod(SM2_ECC_N);
			
			ECPoint RA = curve.decodePoint(entity.R);
			
			BigInteger x1 = RA.getX().toBigInteger();
			x1 = _2w.add(x1.and(_2w.subtract(BigInteger.ONE)));

			ECPoint aPublicKey=curve.decodePoint(entity.K);
			ECPoint temp = aPublicKey.add(RA.multiply(x1));
			
			ECPoint V = temp.multiply(domainParams.getH().multiply(tB));
			if (V.isInfinity())
				throw new IllegalStateException();
			this.V=V;
			
			byte[] xV = byteConvert32Bytes(V.getX().toBigInteger());
			byte[] yV = byteConvert32Bytes(V.getY().toBigInteger());
			byte[] KB = KDF(join(xV, yV,entity.Z,this.Z), SHARE_KEY_LENGTH);
			
			key = KB;
//			System.out.print("协商得B密钥:");
//			printHexString(KB);
			byte[] sB = sm3hash(new byte[] { 0x02 }, yV,
					sm3hash(xV, entity.Z, this.Z, byteConvert32Bytes(RA.getX().toBigInteger()),
							byteConvert32Bytes(RA.getY().toBigInteger()),
							byteConvert32Bytes(RB.getX().toBigInteger()),
							byteConvert32Bytes(RB.getY().toBigInteger())));
			return new TransportEntity(RB.getEncoded(), sB,this.Z,keyPair.getPublicKeyEc().getEncoded());
		}

		/**
		 * 密钥协商发起方第二步
		 * 
		 * @param entity 传输实体
		 */
		public TransportEntity keyExchange_3(TransportEntity entity) {
			BigInteger x1 = RA.getX().toBigInteger();
			x1 = _2w.add(x1.and(_2w.subtract(BigInteger.ONE)));

			BigInteger tA = keyPair.getPrivateKeyBi().add(x1.multiply(rA)).mod(SM2_ECC_N);
			
			ECPoint RB = curve.decodePoint(entity.R);
			
			BigInteger x2 = RB.getX().toBigInteger();
			x2 = _2w.add(x2.and(_2w.subtract(BigInteger.ONE)));

			ECPoint bPublicKey=curve.decodePoint(entity.K);
			ECPoint temp = bPublicKey.add(RB.multiply(x2));
			
			ECPoint U = temp.multiply(domainParams.getH().multiply(tA));
			if (U.isInfinity())
				throw new IllegalStateException();
			this.V=U;
			
			byte[] xU = byteConvert32Bytes(U.getX().toBigInteger());
			byte[] yU = byteConvert32Bytes(U.getY().toBigInteger());
			byte[] KA = KDF(join(xU, yU,
					this.Z, entity.Z), SHARE_KEY_LENGTH);
			key = KA;
//			System.out.print("协商得A密钥:");
//			printHexString(KA);
			byte[] s1= sm3hash(new byte[] { 0x02 }, yU,
					sm3hash(xU, this.Z, entity.Z, 
							byteConvert32Bytes(RA.getX().toBigInteger()),
							byteConvert32Bytes(RA.getY().toBigInteger()),
							byteConvert32Bytes(RB.getX().toBigInteger()), 
							byteConvert32Bytes(RB.getY().toBigInteger())));
			if(Arrays.equals(entity.S, s1))
				System.out.println("B->A 密钥确认成功");
			else
				System.out.println("B->A 密钥确认失败");
			byte[] sA= sm3hash(new byte[] { 0x03 }, yU,
					sm3hash(xU, this.Z, entity.Z, 
							byteConvert32Bytes(RA.getX().toBigInteger()),
							byteConvert32Bytes(RA.getY().toBigInteger()),
							byteConvert32Bytes(RB.getX().toBigInteger()),
							byteConvert32Bytes(RB.getY().toBigInteger())));
			
			return new TransportEntity(RA.getEncoded(), sA,this.Z,keyPair.getPublicKeyEc().getEncoded());
		}
		
		/**
		 * 密钥确认最后一步
		 * 
		 * @param entity 传输实体
		 */
		public void keyExchange_4(TransportEntity entity) {
			byte[] xV = byteConvert32Bytes(V.getX().toBigInteger());
			byte[] yV = byteConvert32Bytes(V.getY().toBigInteger());
			ECPoint RA = curve.decodePoint(entity.R);
			byte[] s2= sm3hash(new byte[] { 0x03 }, yV,
					sm3hash(xV, entity.Z, this.Z, 
							byteConvert32Bytes(RA.getX().toBigInteger()),
							byteConvert32Bytes(RA.getY().toBigInteger()),
							byteConvert32Bytes(this.RA.getX().toBigInteger()),
							byteConvert32Bytes(this.RA.getY().toBigInteger())));
			if(Arrays.equals(entity.S, s2))
				System.out.println("A->B 密钥确认成功");
			else
				System.out.println("A->B 密钥确认失败");
		}
		
		/**
		 * 随机数生成器
		 * 
		 * @param max
		 * @return
		 */
		private BigInteger random(BigInteger max) {

			BigInteger r = new BigInteger(256, secureRandom);

			while (r.compareTo(max) >= 0) {
				r = new BigInteger(128, secureRandom);
			}

			return r;
		}

		public byte[] getKey() {
			return key;
		}

		public ECPoint getRA() {
			return RA;
		}
		
	}
	

    public static class KeyPair {

        private ECPoint publicKeyEc;
    	private BigInteger privateKeyBi;
    	
        public KeyPair(ECPoint publicKey, BigInteger privateKey) {
            this.publicKeyEc = publicKey;
            this.privateKeyBi = privateKey;
        }
        

        public ECPoint getPublicKeyEc() {
			return publicKeyEc;
		}

		public BigInteger getPrivateKeyBi() {
			return privateKeyBi;
		}

		public byte[] getPrivateKey() {
        	if(privateKeyBi != null){
        		return byteConvert32Bytes(privateKeyBi);
        	}
            return new byte[0];
        }

        public byte[] getPublicKey() {
        	if(publicKeyEc != null){
        		return publicKeyEc.getEncoded();
        	}
            return new byte[0];
        }
        
        
    }
    

    public ECCurve.Fp getCurve() {
		return curve;
	}

	public void setCurve(ECCurve.Fp curve) {
		this.curve = curve;
	}

	public ECPoint.Fp getPointG() {
		return pointG;
	}

	public void setPointG(ECPoint.Fp pointG) {
		this.pointG = pointG;
	}

	public ECDomainParameters getDomainParams() {
		return domainParams;
	}

	public void setDomainParams(ECDomainParameters domainParams) {
		this.domainParams = domainParams;
	}

	public SecureRandom getSecureRandom() {
		return secureRandom;
	}

	public void setSecureRandom(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	public enum Type {
        C1C2C3,
        C1C3C2
    }

    private static class EncryptedData {

        private ECPoint c1;
        private byte[] c2;
        private byte[] c3;

        private EncryptedData(ECPoint c1, byte[] c2, byte[] c3) {
            this.c1 = c1;
            this.c2 = c2;
            this.c3 = c3;
        }
    }

}
