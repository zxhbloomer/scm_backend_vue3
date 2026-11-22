package com.xinyirun.scm.bean.system.vo.sys.table;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 列配置与表格当前列的比对结果，同时也作为 check 接口的入参。
 *
 * @author xinyirun
 * @since 2026-11-22
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class STableColumnCheckVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 5218734519028571369L;

    /**
     * 页面编号，与列配置的 page_code 对应
     */
    private String page_code;

    /**
     * 前端表格当前显示的列名清单，按列名比对
     */
    private List<String> current_columns;

    /**
     * 库里的列配置与当前列对不上时为 true，前端据此决定要不要弹「列有变化」的确认框
     */
    private Boolean changed;

    /**
     * 表格里有、库里没有的列名，打开弹窗时要补到末尾
     */
    private List<String> added;

    /**
     * 库里有、表格里已经没有的列名，弹窗里不再展示
     */
    private List<String> removed;
}
