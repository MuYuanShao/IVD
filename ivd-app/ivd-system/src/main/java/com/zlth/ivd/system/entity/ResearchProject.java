package com.zlth.ivd.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("research_project")
public class ResearchProject {
    @TableId(type=IdType.AUTO) private Long id;
    private String name;
    private String leader;
    private String remark;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
