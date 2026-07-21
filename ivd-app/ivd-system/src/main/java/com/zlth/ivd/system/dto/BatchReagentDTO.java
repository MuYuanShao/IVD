package com.zlth.ivd.system.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量添加/移除科室试剂 DTO
 *
 * @author IVD Team
 * @since 2026-07-15
 */
public class BatchReagentDTO {

    @NotEmpty(message = "试剂ID列表不能为空")
    private List<Long> reagentIds;

    public List<Long> getReagentIds() { return reagentIds; }
    public void setReagentIds(List<Long> reagentIds) { this.reagentIds = reagentIds; }
}
