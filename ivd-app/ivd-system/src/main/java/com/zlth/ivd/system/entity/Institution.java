package com.zlth.ivd.system.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("institution")
public class Institution {
    @TableId(type=IdType.AUTO) private Long id;
    private String code; private String name; private String type; private Long ownerInstitutionId;
    private String contactPerson; private String contactPhone; private String address; private String capabilities;
    private Integer status; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
