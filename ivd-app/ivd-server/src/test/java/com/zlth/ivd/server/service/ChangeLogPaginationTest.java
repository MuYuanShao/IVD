package com.zlth.ivd.server.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.service.ReagentCatalogService;
import com.zlth.ivd.common.dto.*;
import com.zlth.ivd.common.vo.ChangeLogVO;
import com.zlth.ivd.license.mapper.*;
import com.zlth.ivd.license.service.impl.LicenseServiceImpl;
import com.zlth.ivd.common.config.MinioProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChangeLogPaginationTest {
    @Test void thirdCombinedPageIsNotDoublePaginated() {
        verifyPage(3);
        verifyPage(30); // Internal prefix exceeds the global 500-row page cap.
    }
    private void verifyPage(int requestedPage) {
        int prefix = requestedPage * 20;
        var mapper = mock(LicenseChangeLogMapper.class);
        var catalog = mock(ReagentCatalogService.class);
        var service = new LicenseServiceImpl(mock(SupplierLicenseMapper.class), mock(SupplierMapper.class),
                mapper, mock(ReagentSupplierRelMapper.class), catalog, mock(MinioClient.class), new MinioProperties());
        List<ChangeLogVO> licenses = rows("LICENSE", prefix, 1000);
        List<ChangeLogVO> reagents = rows("CATALOG", prefix, 2000);
        when(mapper.findAllWithLicense(any(), any())).thenAnswer(call -> {
            Page<ChangeLogVO> page = call.getArgument(0);
            assertEquals(1, page.getCurrent()); assertEquals(prefix, page.getSize());
            assertEquals((long)prefix,page.maxLimit());
            page.setTotal(prefix); page.setRecords(licenses); return page;
        });
        when(catalog.pageChangeLogs(any())).thenAnswer(call -> {
            ChangeLogQuery query = call.getArgument(0);
            assertEquals(1, query.getPage()); assertEquals(prefix, query.getPageSize());
            return new PageVO<>((long)prefix,1,prefix,reagents);
        });
        var query = new ChangeLogQuery(); query.setPage(requestedPage); query.setPageSize(20);
        var result = service.listChangeLogs(query);
        assertEquals(prefix*2,result.getTotal()); assertEquals(20,result.getRecords().size());
        assertEquals(requestedPage,query.getPage()); assertEquals(20,query.getPageSize());
        Set<Long> expected = new HashSet<>();
        for(int n=(requestedPage-1)*10;n<requestedPage*10;n++) { expected.add(1000L+n); expected.add(2000L+n); }
        assertEquals(expected,new HashSet<>(result.getRecords().stream().map(ChangeLogVO::getId).toList()));
    }
    private List<ChangeLogVO> rows(String module, int count, int base) {
        var list = new ArrayList<ChangeLogVO>();
        for(int n=0;n<count;n++) list.add(ChangeLogVO.builder().id((long)base+n).module(module)
                .changeType("UPDATE").createdAt(LocalDateTime.of(2026,10,11,12,0).minusMinutes(n)).build());
        return list;
    }
}
