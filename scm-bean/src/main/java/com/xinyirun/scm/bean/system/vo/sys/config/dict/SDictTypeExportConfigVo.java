package com.xinyirun.scm.bean.system.vo.sys.config.dict;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 字典类型页导出弹窗提交的导出请求。
 *
 * 一次请求说明四件事：按什么条件导出、导出哪些列、子数据集怎么放、
 * 以及弹窗里勾过的偏好，服务端据此生成文件，并把偏好记到页面设置里。
 */
@Data
@NoArgsConstructor
public class SDictTypeExportConfigVo implements Serializable {

    private static final long serialVersionUID = 3194827365019283746L;

    /**
     * 列表当前的查询条件，包含编码、名称、启用状态与分页信息。
     * 导出范围是当前检索结果或当前页时使用。
     */
    private SDictTypeVo searchCondition;

    /**
     * 导出范围：
     * search-当前检索结果，page-当前页，all-系统全部字典类型。
     */
    private String scope;

    /**
     * 子数据集导出方式：
     * multiSheet-多 Sheet 工作簿，flat-平铺宽表。
     */
    private String strategy;

    /**
     * 主表勾选的字段键，顺序就是导出后的列顺序。
     */
    private List<String> mainFields;

    /**
     * 明细勾选的字段键，顺序就是导出后的列顺序。
     */
    private List<String> itemFields;

    /**
     * 弹窗背后的页面是否做磨砂模糊效果。
     */
    private Boolean glass;
}
