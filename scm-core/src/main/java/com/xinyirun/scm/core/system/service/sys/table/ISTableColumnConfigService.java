package com.xinyirun.scm.core.system.service.sys.table;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xinyirun.scm.bean.entity.sys.table.STableColumnConfigEntity;
import com.xinyirun.scm.bean.system.vo.sys.table.STableColumnCheckVo;
import com.xinyirun.scm.bean.system.vo.sys.table.STableColumnConfigVo;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author xinyirun
 * @since 2022-08-29
 */
public interface ISTableColumnConfigService extends IService<STableColumnConfigEntity> {

    /**
     * 查询页面表格配置信息
     * @param vo
     * @return
     */
    List<STableColumnConfigVo> list(STableColumnConfigVo vo);

    /**
     * 重置表格列配置
     * 根据前端传来的配置数据，进行全删全插操作（仅针对当前用户）
     * @param configs 列配置数据
     * @param pageCode 页面代码
     */
    void resetTableColumns(List<STableColumnConfigVo> configs, String pageCode);

    /**
     * 重置表格列配置并返回最新数据
     * 根据前端传来的配置数据，进行全删全插操作，然后返回最新的配置列表
     * @param configs 列配置数据
     * @param pageCode 页面代码
     * @return 重置后的配置列表
     */
    List<STableColumnConfigVo> resetTableColumnsAndReturn(List<STableColumnConfigVo> configs, String pageCode);

    /**
     * 比对库里的列配置与前端表格当前的列
     * 入参带页面编号和当前列名清单，出参给出新增、删除的列，以及整体是否有变化
     */
    STableColumnCheckVo check(STableColumnCheckVo vo);

    /**
     * 修改表格配置信息
     * @param list
     */
    void saveList(List<STableColumnConfigVo> list);

}
