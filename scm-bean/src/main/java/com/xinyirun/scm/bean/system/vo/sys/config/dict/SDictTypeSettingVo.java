package com.xinyirun.scm.bean.system.vo.sys.config.dict;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 字典类型页导出弹窗的偏好设置。
 *
 * 这些值原本存在浏览器的本地缓存里，用来记住上一次打开导出弹窗时选的范围、
 * 子数据集方式和勾选的字段。保存到服务端后，换机器或清缓存也不会丢。
 */
@Data
@NoArgsConstructor
public class SDictTypeSettingVo implements Serializable {

    private static final long serialVersionUID = 6182739451028374651L;

    /**
     * 弹窗背后的页面是否做磨砂模糊效果。
     * 为空按不做处理。
     */
    private Boolean glass;

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
}
