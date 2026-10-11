package com.zlth.ivd.common.service;
import java.util.*;
public interface StockBillingStatusService {
    Map<Long,BillingStatus> consumptionStatuses(Collection<Long> recordIds);
    record BillingStatus(String settlementNo,String status) {}
}
