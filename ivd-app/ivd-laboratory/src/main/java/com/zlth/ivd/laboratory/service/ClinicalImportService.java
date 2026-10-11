package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.laboratory.importing.ImportResource;
import com.zlth.ivd.laboratory.vo.ImportBatchVO;
public interface ClinicalImportService {
    byte[] template(ImportResource resource);
    ImportBatchVO preflight(ImportResource resource,byte[] xlsx);
    ImportBatchVO execute(Long id);
    ImportBatchVO detail(Long id);
}
