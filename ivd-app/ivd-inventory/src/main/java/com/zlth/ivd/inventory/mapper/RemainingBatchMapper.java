package com.zlth.ivd.inventory.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.inventory.vo.RemainingBatchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RemainingBatchMapper {
    @Select("<script>SELECT CAST(b.id AS CHAR(20)) id,c.his_code code,c.generic_name name,c.manufacturer,c.specification,"
            + "b.batch_no,b.expiry_date,r.remaining_quantity quantity,b.unit,b.unit_price,w.name warehouse,"
            + "w.level warehouse_level,b.location_code location,b.udi_status status,r.updated_at "
            + "FROM inventory_remaining_batch r JOIN reagent_batch b ON b.id=r.batch_id AND b.institution_id=r.institution_id "
            + "LEFT JOIN reagent_catalog c ON c.id=b.reagent_id AND c.institution_id=r.institution_id "
            + "LEFT JOIN warehouse w ON w.id=b.warehouse_id AND w.institution_id=r.institution_id "
            + "LEFT JOIN warehouse p ON p.id=w.parent_id AND p.institution_id=r.institution_id "
            + "LEFT JOIN warehouse g ON g.id=p.parent_id AND g.institution_id=r.institution_id "
            + "WHERE r.remaining_quantity&gt;0 "
            + "<if test='departmentId != null'> AND (w.level=1 OR COALESCE(w.dept_id,p.dept_id,g.dept_id)=#{departmentId})</if> "
            + "<if test='keyword != null and keyword != &quot;&quot;'> AND (LOCATE(#{keyword},c.generic_name)&gt;0 "
            + "OR LOCATE(#{keyword},c.his_code)&gt;0 OR LOCATE(#{keyword},c.manufacturer)&gt;0 OR LOCATE(#{keyword},b.batch_no)&gt;0)</if> "
            + "<if test='level != null'> AND w.level=#{level}</if> "
            + "ORDER BY b.expiry_date IS NULL,b.expiry_date,b.id</script>")
    IPage<RemainingBatchVO> list(Page<RemainingBatchVO> page, @Param("keyword") String keyword, @Param("level") Integer level,
                               @Param("departmentId") Long departmentId);
}
