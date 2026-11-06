package com.zlth.ivd.settlement.vo;

import com.zlth.ivd.common.dto.PageVO;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Existing page envelope plus aggregates over the same unpaginated filters. */
public class ConsumptionDetailsPageVO extends PageVO<ConsumptionSettlementVO> {
    private final Summary summary;

    public ConsumptionDetailsPageVO(long total, int page, int pageSize,
                                    List<ConsumptionSettlementVO> records, Summary summary) {
        super(total, page, pageSize, records);
        this.summary = summary;
    }

    public Summary getSummary() { return summary; }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(long activeSettlementCount, BigDecimal activeSettlementAmount, long reagentCount) {}
}
