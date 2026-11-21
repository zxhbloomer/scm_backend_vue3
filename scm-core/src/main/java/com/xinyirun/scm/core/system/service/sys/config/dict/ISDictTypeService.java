package com.xinyirun.scm.core.system.service.sys.config.dict;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xinyirun.scm.bean.entity.sys.config.dict.SDictTypeEntity;
import com.xinyirun.scm.bean.system.ao.result.InsertResultAo;
import com.xinyirun.scm.bean.system.ao.result.UpdateResultAo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeExportVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeStatisticsVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeVo;

import java.util.List;

/**
 * <p>
 * 字典类型表、字典主表 服务类
 * </p>
 *
 * @author zxh
 * @since 2019-08-23
 */
public interface ISDictTypeService extends IService<SDictTypeEntity> {
    /**
     * 获取列表，页面查询
     *
     * 返回值用视图对象承接，实体类只用于新增、修改、删除。
     *
     * @param searchCondition 查询条件
     * @return 分页后的字典类型数据，每行带最后修改人的姓名
     */
    IPage<SDictTypeVo> selectPage(SDictTypeVo searchCondition) ;

    /**
     * 获取所有数据
     *
     * @param searchCondition 查询条件
     * @return 字典类型数据，每行带最后修改人的姓名
     */
    List<SDictTypeVo> select(SDictTypeVo searchCondition) ;

    /**
     * 统计总条数、正常使用条数、已停用条数
     *
     * @param searchCondition 查询条件，只使用编码与名称，不使用启用状态
     * @return 三个统计数字
     */
    SDictTypeStatisticsVo selectStatistics(SDictTypeVo searchCondition);

    /**
     * 获取所选id的数据
     */
    List<SDictTypeVo> selectIdsIn(List<SDictTypeVo> searchCondition) ;

    /**
     * 批量导入
     */
    boolean saveBatches(List<SDictTypeEntity> entityList);

    /**
     * 批量删除复原
     * @param searchCondition
     * @return
     */
    void deleteByIdsIn(List<SDictTypeVo> searchCondition);

    /**
     * 插入一条记录（选择字段，策略插入）
     * @param entity 实体对象
     * @return
     */
    InsertResultAo<Integer> insert(SDictTypeEntity entity);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param entity 实体对象
     * @return
     */
    UpdateResultAo<Integer> update(SDictTypeEntity entity);

    /**
     * 通过code查询
     *
     */
    List<SDictTypeVo> selectByCode(String code);

    /**
     * 为excel做的check，仅仅是为了尝试是否能够反射调用
     * @param vo
     * @return
     */
    Boolean testCheck(SDictTypeEntity vo);

    /**
     * 部分 导出
     * @param searchConditionList
     * @return
     */
    List<SDictTypeExportVo> selectListExport(List<SDictTypeVo> searchConditionList);

    /**
     * 全部导出
     * @param searchCondition
     * @return
     */
    List<SDictTypeExportVo> selectAllExport(SDictTypeVo searchCondition);

    /**
     * 按查询条件取导出用的主表数据
     *
     * 与列表用同一套筛选条件。
     *
     * @param searchCondition 查询条件
     * @return 字典类型数据
     */
    List<SDictTypeVo> selectForExport(SDictTypeVo searchCondition);

    /**
     * 查询已经进缓存的字典类型编码
     *
     * @return 已缓存的字典类型编码
     */
    List<String> selectCachedCodes();

    /**
     * 清空字典类型缓存
     *
     * @return 是否清理成功
     */
    boolean refreshCache();
}
