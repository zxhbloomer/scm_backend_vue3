package com.xinyirun.scm.bean.system.vo.sys.config.dict;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 字典类型的数量统计，供页面顶部的数量提示使用
 */
@Data
@NoArgsConstructor
public class SDictTypeStatisticsVo implements Serializable {

    private static final long serialVersionUID = 4517820939120487931L;

    /**
     * 符合条件的总条数
     */
    private Long total;

    /**
     * 未删除的条数，页面上称为正常使用
     */
    private Long normal;

    /**
     * 已删除的条数，页面上称为已停用
     */
    private Long disabled;
}
