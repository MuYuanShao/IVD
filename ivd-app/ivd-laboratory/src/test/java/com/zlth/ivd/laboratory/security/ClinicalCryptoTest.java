package com.zlth.ivd.laboratory.security;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class ClinicalCryptoTest {
 @Test void anotherInstitutionCannotDecryptAStoredPatient(){
  byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
  var crypto=new ClinicalCrypto(Base64.getEncoder().encodeToString(key),"another-secret");
  String value=InstitutionContext.within(new InstitutionContext.Scope(1L,2L,3L,"LAB_TECH"),()->crypto.encryptText("测试患者"));
  InstitutionContext.within(new InstitutionContext.Scope(2L,4L,5L,"LAB_TECH"),()->{assertThrows(BizException.class,()->crypto.decryptText(value));return null;});
  assertEquals("测试患者",InstitutionContext.within(new InstitutionContext.Scope(1L,2L,3L,"LAB_TECH"),()->crypto.decryptText(value)));
 }
 @Test void missingKeyNeverStoresPlaintext(){assertThrows(BizException.class,()->new ClinicalCrypto("","").encryptText("测试患者"));}
}
