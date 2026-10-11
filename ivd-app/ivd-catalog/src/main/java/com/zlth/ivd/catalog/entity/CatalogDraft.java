package com.zlth.ivd.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Mutable working copy; submitted copies are frozen until reviewed. */
@Data
@TableName("catalog_draft")
public class CatalogDraft {
    @TableId(type = IdType.INPUT)
    private Long reagentId;
    private String snapshot;
    private LocalDateTime updatedAt;
}
