package com.zlth.ivd.laboratory.signature;
import com.zlth.ivd.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Component @ConditionalOnMissingBean(CaSignatureService.class)
public class UnavailableCaSignatureService implements CaSignatureService {
    public boolean available(){return false;}
    public Signature sign(byte[] digest){throw new BizException(40900,"未配置真实 CA 服务，不支持 CA 数字签名");}
    public boolean verify(byte[] digest,Signature signature){throw new BizException(40900,"未配置真实 CA 服务，不能模拟验签成功");}
}
