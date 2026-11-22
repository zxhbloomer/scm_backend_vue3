package com.xinyirun.scm.bean.system.vo.sys.table;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * 表格列配置详情VO
 * </p>
 *
 * @author xinyirun
 * @since 2025-01-08
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class STableColumnConfigDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 8372562758391067483L;

    private Integer id;

    /**
     * 关联config表ID
     */
    private Integer config_id;

    /**
     * 表格code
     */
    private String table_code;

    /**
     * 表格id
     */
    private Integer table_id;

    /**
     * 字段名
     */
    private String name;

    /**
     * 表头名
     */
    private String label;

    /**
     * 是否显示
     */
    private Boolean is_enable;

    /**
     * 是否删除
     */
    private Boolean is_delete;

    /**
     * 排序，分组内子列的先后顺序
     */
    private Integer sort;

    /**
     * 列性质：0 数据列，可以从行数据取值，导出时带上；1 非数据列，操作按钮列、页面自己算出来的展示列，导出时跳过
     */
    private String column_type;
}