package com.dazhihui.smdemo.trade.sm;


public class SM4Utils
{
	private byte[] secretKey = null;
	private byte[] iv = null;

	public SM4Utils()
	{
	}

	public void setSecretKey(byte[] secretKey) {
		this.secretKey = secretKey;
	}

	public void setIv(byte[] iv) {
		this.iv = iv;
	}

	public byte[] encryptData_ECB(String plainText)
	{
		try 
		{
			SM4_Context ctx = new SM4_Context();
			ctx.isPadding = false;
			ctx.mode = SM4.SM4_ENCRYPT;
			
			SM4 sm4 = new SM4();
			sm4.sm4_setkey_enc(ctx, secretKey);
			byte[] encrypted = sm4.sm4_crypt_ecb(ctx, plainText.getBytes());
			return encrypted;
		} 
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] decryptData_ECB(byte[] source)
	{
		try 
		{
			SM4_Context ctx = new SM4_Context();
			ctx.isPadding = false;
			ctx.mode = SM4.SM4_DECRYPT;
			
			SM4 sm4 = new SM4();
			sm4.sm4_setkey_dec(ctx, secretKey);
			byte[] decrypted = sm4.sm4_crypt_ecb(ctx, source);
			return decrypted;
		} 
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] encryptData_CBC(byte[] data)
	{
		try 
		{
			SM4_Context ctx = new SM4_Context();
			ctx.isPadding = false;
			ctx.mode = SM4.SM4_ENCRYPT;
			
			SM4 sm4 = new SM4();
			sm4.sm4_setkey_enc(ctx, secretKey);
			byte[] encrypted = sm4.sm4_crypt_cbc(ctx, iv, data);
			return encrypted;
		} 
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] decryptData_CBC(byte[] source)
	{
		try 
		{
			SM4_Context ctx = new SM4_Context();
			ctx.isPadding = false;
			ctx.mode = SM4.SM4_DECRYPT;
			
			SM4 sm4 = new SM4();
			sm4.sm4_setkey_dec(ctx, secretKey);
			byte[] decrypted = sm4.sm4_crypt_cbc(ctx, iv, source);
			return decrypted;
		} 
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
}
