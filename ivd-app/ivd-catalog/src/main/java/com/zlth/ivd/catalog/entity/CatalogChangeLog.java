package com.zlth.ivd.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Append-only business history, independent of mutable approval decisions. */
@Data
@TableName("catalog_change_log")
public class CatalogChangeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reagentId;
    private String reagentName;
    private String hisCode;
    private String changeType;
    private String fieldChanges;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
