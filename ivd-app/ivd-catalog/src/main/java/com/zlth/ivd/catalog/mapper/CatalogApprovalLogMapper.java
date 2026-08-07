package com.zlth.ivd.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.CatalogApprovalLog;
import com.zlth.ivd.common.dto.ChangeLogQuery;
import com.zlth.ivd.common.vo.ChangeLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批日志 Mapper
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Mapper
public interface CatalogApprovalLogMapper extends BaseMapper<CatalogApprovalLog> {

    /**
     * 分页查询试剂目录变更记录（联表试剂目录）
     * MyBatis-Plus 分页插件自动拦截 COUNT + LIMIT
     */
    @Select("""
        <script>
        SELECT cal.id, cal.reagent_id, cal.change_type, cal.field_changes,
               cal.change_reason as note, cal.submitter_name as operator_name,
               cal.submitted_at as created_at,
               cal.review_status, cal.reviewer_name, cal.review_comment,
               rc.generic_name as reagent_name, rc.his_code
        FROM catalog_approval_log cal
        LEFT JOIN reagent_catalog rc ON cal.reagent_id = rc.id
        <where>
            <if test='query.changeType != null and query.changeType != ""'>
                AND cal.change_type = #{query.changeType}
            </if>
            <if test='query.startDate != null and query.startDate != ""'>
                AND cal.submitted_at &gt;= #{query.startDate}
            </if>
            <if test='query.endDate != null and query.endDate != ""'>
                AND cal.submitted_at &lt;= #{query.endDate}
            </if>
            <if test='query.keyword != null and query.keyword != ""'>
                AND (rc.generic_name LIKE CONCAT('%',#{query.keyword},'%')
                     OR rc.his_code LIKE CONCAT('%',#{query.keyword},'%'))
            </if>
        </where>
        ORDER BY cal.submitted_at DESC
        </script>
    """)
    IPage<ChangeLogVO> findAllWithReagent(Page<ChangeLogVO> page, @Param("query") ChangeLogQuery query);
}
