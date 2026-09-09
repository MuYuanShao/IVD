package com.zlth.ivd.analytics.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-model contract: money is CNY; records are ledger rows, never mixed-unit quantities. */
@Data
public class WorkbenchVO {
    private LocalDate businessDate;
    private LocalDateTime generatedAt;
    private String timezone;
    private String scope;
    private Metrics metrics;
    private Alerts alerts;
    private long todoTotal;
    private List<Todo> todos;
    private List<Trend> trend;
    private String noticeStatus = "NOT_CONFIGURED";

    @Data public static class ScopeUser { private Long deptId; private String role; }
    @Data @JsonInclude(JsonInclude.Include.ALWAYS) public static class Metrics {
        private BigDecimal consumptionAmount;
        private long consumedRecords;
        private long inboundRecords;
        private long transferRecords;
        private long unpricedRecords;
    }
    @Data public static class Alerts {
        private long expiredBatches;
        private long expiringBatches;
        private long pendingUdiBatches;
        private long shortageOrders;
    }
    @Data @JsonInclude(JsonInclude.Include.ALWAYS) public static class Trend {
        private LocalDate date;
        private BigDecimal inbound;
        private BigDecimal outbound;
        private long unpricedRecords;
    }
    @Data @JsonInclude(JsonInclude.Include.ALWAYS) public static class Todo {
        private String id;
        private String reqNo;
        private String status;
        private LocalDateTime createdAt;
        private long lineCount;
        private long fulfilledLines;
        private BigDecimal amount;
    }
    @Data public static class Product {
        private String id;
        private String reagentId;
        private String code;
        private String name;
        private String manufacturer;
        private String specification;
        private String unit;
        private BigDecimal quantity;
        private LocalDate expiry;
        private String batch;
        private String location;
        private String warehouse;
    }
}
