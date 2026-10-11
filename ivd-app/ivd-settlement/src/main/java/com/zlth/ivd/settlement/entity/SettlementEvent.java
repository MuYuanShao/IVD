package com.zlth.ivd.settlement.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("settlement_event")
public class SettlementEvent {
    @TableId(type=IdType.AUTO) private Long id;
    private Long settlementId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
