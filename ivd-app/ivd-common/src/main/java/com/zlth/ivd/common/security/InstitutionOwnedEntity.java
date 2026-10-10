package com.zlth.ivd.common.security;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Ownership is readable after loading, but ORM inserts must let the tenant interceptor supply it. */
public abstract class InstitutionOwnedEntity {
    @TableField(value = "institution_id", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long institutionId;

    public Long getInstitutionId() { return institutionId; }
}
