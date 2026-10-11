package com.zlth.ivd.laboratory.signature;
public interface CaSignatureService {
    boolean available();
    Signature sign(byte[] digest);
    boolean verify(byte[] digest,Signature signature);
    record Signature(byte[] value,String certificateSubject,String certificateSerial) { }
}
