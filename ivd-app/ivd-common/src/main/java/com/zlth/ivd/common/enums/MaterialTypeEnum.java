package com.zlth.ivd.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 物料类型枚举 — 决定全流程行为的分流字段
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public enum MaterialTypeEnum {

    /** 试剂 — 注册证必填 / tests_per_kit必填 / 扫码验收 / 进入UDI状态机 / 效益分析按项目分摊 */
    REAGENT("REAGENT", "试剂", false, true, true, true, "扫码验收"),

    /** 校准品 — 注册证必填 / 扫码验收 / 进入UDI状态机 / 不入效益分析 */
    CALIBRATOR("CALIBRATOR", "校准品", false, true, false, true, "扫码验收"),

    /** 质控品 — 注册证必填 / 扫码验收 / 进入UDI状态机 / 不入效益分析 */
    QC_MATERIAL("QC_MATERIAL", "质控品", false, true, false, true, "扫码验收"),

    /** 耗材 — 数量验收 / 不入UDI状态机 / 效益分析按科室均摊 */
    CONSUMABLE("CONSUMABLE", "耗材", false, false, false, false, "数量验收"),

    /** 其他 — 数量验收 / 不入UDI状态机 / 效益分析按科室均摊 */
    OTHER("OTHER", "其他", false, false, false, false, "数量验收");

    @EnumValue
    private final String code;
    private final String label;
    /** UDI-DI 是否必填 */
    private final boolean udiRequired;
    /** 注册证号 是否必填 */
    private final boolean registrationCertRequired;
    /** 标称测试数 是否必填 */
    private final boolean testsPerKitRequired;
    /** 是否进入 UDI 状态机 */
    private final boolean enterUdiStateMachine;
    /** 验收方式 */
    private final String acceptanceMethod;

    MaterialTypeEnum(String code, String label, boolean udiRequired,
                     boolean registrationCertRequired, boolean testsPerKitRequired,
                     boolean enterUdiStateMachine, String acceptanceMethod) {
        this.code = code;
        this.label = label;
        this.udiRequired = udiRequired;
        this.registrationCertRequired = registrationCertRequired;
        this.testsPerKitRequired = testsPerKitRequired;
        this.enterUdiStateMachine = enterUdiStateMachine;
        this.acceptanceMethod = acceptanceMethod;
    }

    /**
     * 判断该物料类型是否需要五码映射
     */
    public boolean requiresFiveCodeMapping() {
        return this == REAGENT || this == CALIBRATOR || this == QC_MATERIAL;
    }

    /**
     * 判断该物料类型是否进入UDI状态机
     */
    public boolean entersUdiStateMachine() {
        return enterUdiStateMachine;
    }
}
