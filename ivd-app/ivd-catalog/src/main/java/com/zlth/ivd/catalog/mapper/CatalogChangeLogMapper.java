package com.zlth.ivd.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.CatalogChangeLog;
import com.zlth.ivd.common.dto.ChangeLogQuery;
import com.zlth.ivd.common.vo.ChangeLogVO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CatalogChangeLogMapper extends BaseMapper<CatalogChangeLog> {
    @Select("""
        <script>
        SELECT id, reagent_id, reagent_name, his_code, change_type, field_changes,
               operator_name, created_at, review_status, reviewer_name, review_comment, note
        FROM (
          SELECT id, reagent_id, reagent_name, his_code, change_type, field_changes, operator_name, created_at,
                 NULL AS review_status, NULL AS reviewer_name, NULL AS review_comment, NULL AS note
          FROM catalog_change_log
          UNION ALL
          SELECT cal.id, cal.reagent_id, rc.generic_name AS reagent_name, rc.his_code,
                 cal.change_type, cal.field_changes, cal.submitter_name AS operator_name, cal.submitted_at AS created_at,
                 cal.review_status, cal.reviewer_name, cal.review_comment, cal.change_reason AS note
          FROM catalog_approval_log cal LEFT JOIN reagent_catalog rc ON rc.id = cal.reagent_id
        ) history
        <where>
          <if test='query.changeType != null and query.changeType != ""'>AND change_type = #{query.changeType}</if>
          <if test='query.startDate != null and query.startDate != ""'>AND created_at &gt;= #{query.startDate}</if>
          <if test='query.endDateExclusive != null'>AND created_at &lt; #{query.endDateExclusive}</if>
          <if test='query.keyword != null and query.keyword != ""'>
            AND (reagent_name LIKE CONCAT('%',#{query.keyword},'%') OR his_code LIKE CONCAT('%',#{query.keyword},'%'))
          </if>
        </where>
        ORDER BY created_at DESC, id DESC
        </script>
        """)
    IPage<ChangeLogVO> pageChanges(Page<ChangeLogVO> page, @Param("query") ChangeLogQuery query);
}
