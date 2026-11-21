package com.xinyirun.scm.core.system.service.sys.pages;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xinyirun.scm.bean.entity.sys.pages.SPagesEntity;
import com.xinyirun.scm.bean.system.ao.result.DeleteResultAo;
import com.xinyirun.scm.bean.system.ao.result.InsertResultAo;
import com.xinyirun.scm.bean.system.ao.result.UpdateResultAo;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesExportVo;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesVo;

import java.util.List;

/**
 * <p>
 * 页面表 服务类
 * </p>
 *
 * @author zxh
 * @since 2020-06-05
 */
public interface ISPagesService extends IService<SPagesEntity> {

    /**
     * 获取列表，页面查询
     */
    IPage<SPagesVo> selectPage(SPagesVo searchCondition) ;

    /**
     * 根据code获取明细
     */
    SPagesVo get(SPagesVo searchCondition);

    /**
     * 获取所有数据
     */
    List<SPagesVo> select(SPagesVo searchCondition) ;


    /**
     * 查询by id，返回结果
     *
     * @param id
     * @return
     */
    SPagesVo selectByid(Long id);


    /**
     * 插入一条记录（选择字段，策略插入）
     * @param entity 实体对象
     * @return
     */
    InsertResultAo<Integer> insert(SPagesEntity entity);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param entity 实体对象
     * @return
     */
    UpdateResultAo<Integer> update(SPagesEntity entity);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param entity 实体对象
     * @return
     */
    UpdateResultAo<Integer> update(SPagesVo entity);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param vo 实体对象
     * @return
     */
    void updateImportProcessingFalse(SPagesVo vo);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param vo 实体对象
     * @return
     */
    void updateImportProcessingTrue(SPagesVo vo);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param vo 实体对象
     * @return
     */
    void updateExportProcessingFalse(SPagesVo vo);

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param vo 实体对象
     * @return
     */
    void updateExportProcessingTrue(SPagesVo vo);


    /**
     * 批量物理删除
     * @param searchCondition
     * @return
     */
    DeleteResultAo<Integer> realDeleteByIdsIn(List<SPagesVo> searchCondition);

    /**
     * 更新 日生产报表执行状态
     * @param pagesVo
     * @param status 0执行结束, 1进行中
     */
    void updateProductDailyProcessing(SPagesVo pagesVo, String status);

    /**
     * 导出查询
     * @param searchCondition
     * @return
     */
    List<SPagesExportVo> selectExportList(SPagesVo searchCondition);

    /**
     * 取某个页面设置里的一支配置
     *
     * @param code 页面编码
     * @param jsonPath 配置在 setting_json 里的位置，形如 .pop.dicttypeExport
     * @return 这一支配置的 json 文本，没有时返回 null
     */
    String selectSettingJson(String code, String jsonPath);

    /**
     * 写入某个页面设置里的一支配置
     *
     * 只覆盖指定的这一支，同页面的其它设置不受影响。
     *
     * @param code 页面编码
     * @param jsonPath 配置在 setting_json 里的位置，形如 .pop.dicttypeExport
     * @param jsonValue 这一支的 json 文本
     * @return 更新行数
     */
    int saveSettingJson(String code, String jsonPath, String jsonValue);
}
