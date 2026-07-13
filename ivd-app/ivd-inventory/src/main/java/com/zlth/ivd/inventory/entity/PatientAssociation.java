package com.zlth.ivd.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 患者关联追踪 — 试剂批次与患者检验结果关联
 *
 * @author IVD Team
 * @since 2026-07-07
 */
@TableName("patient_association")
public class PatientAssociation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String batchNo;
    private String patientIdHash;
    private String testItemCode;
    private String testValue;
    private LocalDateTime testTime;
    private String auditStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public String getPatientIdHash() { return patientIdHash; }
    public void setPatientIdHash(String patientIdHash) { this.patientIdHash = patientIdHash; }

    public String getTestItemCode() { return testItemCode; }
    public void setTestItemCode(String testItemCode) { this.testItemCode = testItemCode; }

    public String getTestValue() { return testValue; }
    public void setTestValue(String testValue) { this.testValue = testValue; }

    public LocalDateTime getTestTime() { return testTime; }
    public void setTestTime(LocalDateTime testTime) { this.testTime = testTime; }

    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
