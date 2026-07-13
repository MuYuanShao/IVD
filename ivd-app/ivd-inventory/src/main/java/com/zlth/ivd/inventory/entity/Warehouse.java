package com.zlth.ivd.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("warehouse")
public class Warehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Integer level;
    private Long parentId;
    private Long deptId;
    private String locationCode;
    private String location;
    private String contactPerson;
    private String contactPhone;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private Boolean isActive;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
