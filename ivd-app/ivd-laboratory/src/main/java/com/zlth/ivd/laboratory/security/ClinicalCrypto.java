package com.zlth.ivd.laboratory.security;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Component
public class ClinicalCrypto {
 private final byte[] key;
 private final SecureRandom random=new SecureRandom();
 public ClinicalCrypto(@Value("${ivd.clinical.encryption-key:}") String configured,@Value("${ivd.jwt.secret:}") String jwtSecret){
  byte[] decoded=null;
  if(configured!=null&&!configured.isBlank()){
   try{decoded=Base64.getDecoder().decode(configured);}catch(IllegalArgumentException error){throw new IllegalArgumentException("临床加密密钥必须为Base64编码");}
   if(decoded.length!=32 || MessageDigest.isEqual(decoded,jwtSecret.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("临床密钥必须为独立的256位密钥");
  }
  key=decoded;
 }
 public boolean available(){return key!=null;}
 private void requireKey(){if(key==null)throw new BizException(50000,"临床加密密钥未配置，拒绝保存或读取敏感资料");}
 public byte[] encrypt(byte[] plain){
  requireKey(); try{
   byte[] nonce=new byte[12];random.nextBytes(nonce); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
   cipher.updateAAD(("IVD-LAB-V1|"+InstitutionContext.require().institutionId()).getBytes(StandardCharsets.UTF_8));
   byte[] encrypted=cipher.doFinal(plain);byte[] output=new byte[nonce.length+encrypted.length];
   System.arraycopy(nonce,0,output,0,nonce.length);System.arraycopy(encrypted,0,output,nonce.length,encrypted.length);return output;
  }catch(GeneralSecurityException error){throw new BizException(50000,"临床资料加密失败");}
 }
 public byte[] decrypt(byte[] encrypted){
  requireKey();if(encrypted==null||encrypted.length<28)throw new BizException(40900,"临床密文无效");
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(encrypted,12)));
   cipher.updateAAD(("IVD-LAB-V1|"+InstitutionContext.require().institutionId()).getBytes(StandardCharsets.UTF_8));
   return cipher.doFinal(Arrays.copyOfRange(encrypted,12,encrypted.length));
  }catch(GeneralSecurityException error){throw new BizException(40900,"临床资料密钥、归属或完整性校验失败");}
 }
 public String encryptText(String plain){return "v1."+Base64.getEncoder().encodeToString(encrypt(plain.getBytes(StandardCharsets.UTF_8)));}
 public String decryptText(String value){if(value==null||!value.startsWith("v1."))throw new BizException(40900,"临床密文版本无效");try{return new String(decrypt(Base64.getDecoder().decode(value.substring(3))),StandardCharsets.UTF_8);}catch(IllegalArgumentException error){throw new BizException(40900,"临床密文无效");}}
 public String index(String kind,String value){
  requireKey();try{Mac derivation=Mac.getInstance("HmacSHA256");derivation.init(new SecretKeySpec(key,"HmacSHA256"));
   byte[] indexKey=derivation.doFinal("IVD-LAB-INDEX-V1".getBytes(StandardCharsets.UTF_8));
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(indexKey,"HmacSHA256"));
   return HexFormat.of().formatHex(mac.doFinal((InstitutionContext.require().institutionId()+"|"+kind+"|"+value.trim()).getBytes(StandardCharsets.UTF_8)));
  }catch(GeneralSecurityException error){throw new BizException(50000,"临床资料索引失败");}
 }
}
