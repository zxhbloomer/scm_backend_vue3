package com.xinyirun.scm.bean.system.vo.sys.config.dict;

import com.xinyirun.scm.bean.system.ao.fs.UploadFileResultAo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xinyirun.scm.bean.system.vo.common.condition.PageCondition;

// import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author zhangxh
 */
@Data
@NoArgsConstructor
// @ApiModel(value = "字典类型信息", description = "字典类型vo_bean")
@EqualsAndHashCode(callSuper=false)
public class SDictTypeVo extends UploadFileResultAo implements Serializable {

    private static final long serialVersionUID = 8149295048471235932L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 字典名称
     */
    private String name;

    /**
     * 字典类型：唯一
     */
    private String code;

    /**
     * 说明
     */
    private String descr;

    /**
     * 是否删除
     */
    private Boolean is_del;

    /**
     * 租户代码
     */
    private String corp_code;

    /**
     * 租户名称
     */
    private String corp_name;

    private Long c_id;

    private LocalDateTime c_time;

    private Long u_id;

    private LocalDateTime u_time;

    /**
     * 数据版本，乐观锁使用
     */
    private Integer dbversion;

    /**
     * 最后修改人的姓名，来自员工表的姓名列，只在列表展示时使用，不对应字典类型表的字段
     */
    private String u_name;

    /**
     * 导出弹窗上次的偏好设置
     * 只在读取导出弹窗初始值时返回，为空时不参与接口出参
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SDictTypeSettingVo setting;

    /**
     * 换页条件
     */
    private PageCondition pageCondition;
}
