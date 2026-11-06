package com.zlth.ivd.common.service;

/** 统一签收事务端口，供采购入口调用，避免采购反向依赖结算模块。 */
public interface RequisitionReceiptService {
    void receiveAndSettleRequisition(Long requisitionId);
}
