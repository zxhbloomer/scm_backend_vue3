package com.xinyirun.scm.bean.entity.sys.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author xinyirun
 * @since 2022-08-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("s_table_column_config")
public class STableColumnConfigEntity implements Serializable {

    private static final long serialVersionUID = -6934386302210997740L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 表格code
     */
    @TableField("table_code")
    private String table_code;

    /**
     * 表格id
     */
    @TableField("table_id")
    private Integer table_id;

    /**
     * 字段名
     */
    @TableField("name")
    private String name;

    /**
     * 表头名
     */
    @TableField("label")
    private String label;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 系统保留列：1 表示系统列（序号、操作），0 或空表示普通列
     */
    @TableField("fix")
    private Boolean fix;

    /**
     * 固定位置：left 靠左固定、right 靠右固定、空 不固定
     */
    @TableField("pin")
    private String pin;

    /**
     * 是否显示
     */
    @TableField("is_enable")
    private Boolean is_enable;

    /**
     * 是否删除
     */
    @TableField("is_delete")
    private Boolean is_delete;

    /**
     * 是否为分组：0-普通列，1-分组
     */
    @TableField("is_group")
    private Integer is_group;

    /**
     * 列性质：0 数据列，可以从行数据取值，导出时带上；1 非数据列，操作按钮列、页面自己算出来的展示列、分组父列，导出时跳过
     */
    @TableField("column_type")
    private String column_type;
}
