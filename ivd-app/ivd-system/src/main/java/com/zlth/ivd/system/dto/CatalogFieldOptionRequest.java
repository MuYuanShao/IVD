package com.zlth.ivd.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CatalogFieldOptionRequest {
    @NotBlank(message = "字段分类不能为空")
    private String fieldCode;
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称不能超过128个字符")
    private String optionLabel;
    @NotBlank(message = "选项值不能为空")
    @Size(max = 128, message = "选项值不能超过128个字符")
    private String optionValue;
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能大于9999")
    private Integer sortOrder;
    @Size(max = 512, message = "备注不能超过512个字符")
    private String remark;
}
