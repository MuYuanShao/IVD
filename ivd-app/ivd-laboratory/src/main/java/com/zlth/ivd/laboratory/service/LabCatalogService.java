package com.zlth.ivd.laboratory.service;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.vo.*;
public interface LabCatalogService {
    PageVO<TestItemVO> items(int page,int pageSize,String keyword);
    TestItemVO item(Long id);
    TestItemVO requireCode(String code);
    TestItemVO lockItem(Long id,boolean activeOnly);
    TestItemVO createItem(TestItemRequest request);
    TestItemVO updateItem(Long id,TestItemRequest request);
    PageVO<IntervalVO> intervals(int page,int pageSize,Long itemId,String kind);
    IntervalVO interval(Long id);
    IntervalVO createInterval(IntervalRequest request);
    IntervalVO updateInterval(Long id,IntervalRequest request);
    IntervalEvaluationVO evaluate(IntervalEvaluationRequest request);
    java.util.List<DeviceOptionVO> deviceOptions(String keyword);
}
