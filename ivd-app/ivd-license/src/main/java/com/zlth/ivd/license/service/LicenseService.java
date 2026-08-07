package com.zlth.ivd.license.service;

import com.zlth.ivd.common.dto.ChangeLogQuery;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.vo.ChangeLogVO;
import com.zlth.ivd.license.dto.CreateLicenseRequest;
import com.zlth.ivd.license.dto.LicenseQuery;
import com.zlth.ivd.license.dto.UpdateLicenseRequest;
import com.zlth.ivd.license.vo.LicenseDetailVO;
import com.zlth.ivd.license.vo.LicenseVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 证照管理服务接口
 *
 * @author IVD Team
 * @since 2026-07-06
 */
public interface LicenseService {

    /** 分页查询证照列表 */
    PageVO<LicenseVO> listLicenses(LicenseQuery query);

    /** 查询证照明细（含关联试剂 + 附件 + 变更记录） */
    LicenseDetailVO getDetail(Long id);

    /** 创建证照 */
    LicenseVO create(CreateLicenseRequest request);

    /** 更新证照（记录变更日志） */
    LicenseVO update(Long id, UpdateLicenseRequest request);

    /** 统计卡片数据 */
    Map<String, Long> getStats();

    /** 供应商列表 */
    List<Map<String, Object>> getSuppliers();

    /** 效期预警列表 */
    List<LicenseVO> getExpiryAlerts(int days);

    /** 上传附件到 MinIO */
    String uploadAttachment(Long licenseId, MultipartFile file, Long operatorId);

    /** 下载/预览附件 */
    void downloadAttachment(Long licenseId, String objectKey, jakarta.servlet.http.HttpServletResponse response);

    /** 处理证照过期（XXL-Job） */
    void processExpiryCheck();

    /** 导出全部证照 */
    List<LicenseVO> exportAll();

    /** 批量导入证照，返回成功条数 */
    int importBatch(List<CreateLicenseRequest> requests);

    /** 删除证照附件（MinIO + DB 元数据） */
    void deleteAttachment(Long licenseId, String objectKey, Long operatorId);

    /** 替换证照附件（覆盖上传），返回新 objectKey */
    String replaceAttachment(Long licenseId, String oldObjectKey, MultipartFile file, Long operatorId);

    /** 分页查询所有变更记录 */
    PageVO<ChangeLogVO> listChangeLogs(ChangeLogQuery query);
}
