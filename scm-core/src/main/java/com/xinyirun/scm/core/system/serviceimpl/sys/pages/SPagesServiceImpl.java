package com.xinyirun.scm.core.system.serviceimpl.sys.pages;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xinyirun.scm.bean.entity.sys.config.config.SConfigEntity;
import com.xinyirun.scm.bean.entity.sys.pages.SPagesEntity;
import com.xinyirun.scm.bean.system.ao.result.CheckResultAo;
import com.xinyirun.scm.bean.system.ao.result.DeleteResultAo;
import com.xinyirun.scm.bean.system.ao.result.InsertResultAo;
import com.xinyirun.scm.bean.system.ao.result.UpdateResultAo;
import com.xinyirun.scm.bean.system.result.utils.v1.CheckResultUtil;
import com.xinyirun.scm.bean.system.result.utils.v1.DeleteResultUtil;
import com.xinyirun.scm.bean.system.result.utils.v1.InsertResultUtil;
import com.xinyirun.scm.bean.system.result.utils.v1.UpdateResultUtil;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesExportVo;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesVo;
import com.xinyirun.scm.common.constant.SystemConstants;
import com.xinyirun.scm.common.exception.system.BusinessException;
import com.xinyirun.scm.common.utils.bean.BeanUtilsSupport;
import com.xinyirun.scm.common.utils.string.StringUtils;
import com.xinyirun.scm.core.system.mapper.sys.pages.SPagesMapper;
import com.xinyirun.scm.core.system.service.sys.config.config.ISConfigService;
import com.xinyirun.scm.core.system.service.sys.pages.ISPagesService;
import com.xinyirun.scm.core.system.utils.mybatis.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 页面表 服务实现类
 * </p>
 *
 * @author zxh
 * @since 2020-06-05
 */
@Service
public class SPagesServiceImpl extends ServiceImpl<SPagesMapper, SPagesEntity> implements ISPagesService {

    @Autowired
    private SPagesMapper mapper;

    @Autowired
    private ISConfigService configService;

    /**
     * 获取列表，页面查询
     *
     * @param searchCondition
     * @return
     */
    @Override
    public IPage<SPagesVo> selectPage(SPagesVo searchCondition) {
        // 分页条件
        Page<SPagesVo> pageCondition =
            new Page(searchCondition.getPageCondition().getCurrent(), searchCondition.getPageCondition().getSize());
        // 通过page进行排序
        PageUtil.setSort(pageCondition, searchCondition.getPageCondition().getSort());
        return mapper.selectPage(pageCondition, searchCondition);
    }

    @Override
    public SPagesVo get(SPagesVo searchCondition) {
        return mapper.get(searchCondition);
    }

    /**
     * 获取列表，查询所有数据
     *
     * @param searchCondition
     * @return
     */
    @Override
    public List<SPagesVo> select(SPagesVo searchCondition) {
        // 查询 数据
        List<SPagesVo> list = mapper.select(searchCondition);
        return list;
    }

    /**
     * 插入一条记录（选择字段，策略插入）
     *
     * @param entity 实体对象
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public InsertResultAo<Integer> insert(SPagesEntity entity) {
        // 插入前check
        CheckResultAo cr = checkLogic(entity, CheckResultAo.INSERT_CHECK_TYPE);
        if (cr.isSuccess() == false) {
            throw new BusinessException(cr.getMessage());
        }
        // 插入逻辑保存
        return InsertResultUtil.OK(mapper.insert(entity));
    }

    /**
     * 更新一条记录（选择字段，策略更新）
     *
     * @param entity 实体对象
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public UpdateResultAo<Integer> update(SPagesEntity entity) {
        // 更新前check
        CheckResultAo cr = checkLogic(entity, CheckResultAo.UPDATE_CHECK_TYPE);
        if (cr.isSuccess() == false) {
            throw new BusinessException(cr.getMessage());
        }
        // 更新逻辑保存
//        entity.setU_id(null);
//        entity.setC_time(null);
        return UpdateResultUtil.OK(mapper.updateById(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpdateResultAo<Integer> update(SPagesVo vo) {
        SPagesEntity entity = new SPagesEntity();
        BeanUtilsSupport.copyProperties(vo, entity);
        return this.update(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateImportProcessingFalse(SPagesVo vo) {
        mapper.updateImportProcessingFalse(vo.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateImportProcessingTrue(SPagesVo vo) {
        mapper.updateImportProcessingTrue(vo.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExportProcessingFalse(SPagesVo vo) {
        mapper.updateExportProcessingFalse(vo.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExportProcessingTrue(SPagesVo vo) {
        mapper.updateExportProcessingTrue(vo.getId());
    }

    /**
     * 获取列表，查询所有数据
     *
     * @return
     */
    public Long selectByName(SPagesEntity entity, String moduleType) {
        return mapper.selectCount(new QueryWrapper<SPagesEntity>()
                .eq("name", entity.getName())
                .ne(CheckResultAo.UPDATE_CHECK_TYPE.equals(moduleType) ? true:false, "id", entity.getId())
        );
    }

    /**
     * 获取列表，查询所有数据
     *
     * @return
     */
    public Long selectByCode(SPagesEntity entity, String moduleType) {
        return mapper.selectCount(new QueryWrapper<SPagesEntity>()
                .eq("code", entity.getCode())
                .ne(CheckResultAo.UPDATE_CHECK_TYPE.equals(moduleType) ? true:false, "id", entity.getId())
        );
    }

    /**
     * check逻辑
     *
     * @return
     */
    public CheckResultAo checkLogic(SPagesEntity entity, String moduleType) {

        switch (moduleType) {
            case CheckResultAo.INSERT_CHECK_TYPE:
                // 新增场合，不能重复
                if (selectByCode(entity, moduleType) >= 1) {
                    return CheckResultUtil.NG("新增保存出错：页面编号【"+ entity.getCode() +"】出现重复!", entity.getCode());
                }
                if (selectByName(entity, moduleType) >= 1) {
                    return CheckResultUtil.NG("新增保存出错：页面名称【"+ entity.getName() +"】出现重复!", entity.getName());
                }

                break;
            case CheckResultAo.UPDATE_CHECK_TYPE:
                // 更新场合，不能重复设置
                if (selectByCode(entity, moduleType) >= 1) {
                    return CheckResultUtil.NG("更新保存出错：页面编号【"+ entity.getCode() +"】出现重复!", entity.getCode());
                }
                if (selectByName(entity, moduleType) >= 1) {
                    return CheckResultUtil.NG("更新保存出错：页面名称【"+ entity.getName() +"】出现重复!", entity.getName());
                }
                break;
            default:
        }
        return CheckResultUtil.OK();
    }

    /**
     * 查询by id，返回结果
     *
     * @param id
     * @return
     */
    @Override
    public SPagesVo selectByid(Long id) {
        // 查询 数据
        return mapper.selectId(id);
    }

    /**
     * 批量删除
     *
     * @param searchCondition
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public DeleteResultAo<Integer> realDeleteByIdsIn(List<SPagesVo> searchCondition) {
        List<Long> idList = new ArrayList<>();
        searchCondition.forEach(bean -> {
            idList.add(bean.getId());
        });
        int result=mapper.deleteBatchIds(idList);
        return DeleteResultUtil.OK(result);
    }

    /**
     * 更新 日生产报表执行状态
     *
     * @param pagesVo
     * @param status  0执行结束, 1进行中
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProductDailyProcessing(SPagesVo pagesVo, String status) {
        LambdaUpdateWrapper<SPagesEntity> wrapper = new LambdaUpdateWrapper<SPagesEntity>()
                .eq(SPagesEntity::getCode, pagesVo.getCode())
                .set(SPagesEntity::getProduct_daily_processing, status);
        mapper.update(null, wrapper);
    }

    /**
     * 导出查询
     *
     * @param searchCondition
     * @return
     */
    @Override
    public List<SPagesExportVo> selectExportList(SPagesVo searchCondition) {
        SConfigEntity sConfigEntity = configService.selectByKey(SystemConstants.EXPORT_LIMIT_KEY);
        if (null == searchCondition.getIds() && !Objects.isNull(sConfigEntity) && "1".equals(sConfigEntity.getValue()) && StringUtils.isNotEmpty(sConfigEntity.getExtra1())) {
            int count = mapper.selectExportNum(searchCondition);
            if (count > Integer.parseInt(sConfigEntity.getExtra1())) {
                throw new BusinessException(String.format(sConfigEntity.getExtra2(), sConfigEntity.getExtra1()));
            }
        }
        return mapper.selectExportList(searchCondition);
    }

    /**
     * 取某个页面设置里的一支配置
     *
     * @param code 页面编码
     * @param jsonPath 读取时传完整路径，形如 .pop.dicttypeExport
     * @return 这一支配置的 json 文本，没有时返回 null
     */
    @Override
    public String selectSettingJson(String code, String jsonPath) {
        return mapper.selectSettingJsonByCode(code, jsonPath);
    }

    /**
     * 写入某个页面设置里的一支配置
     *
     * @param code 页面编码
     * @param jsonPath 写入时传 pop 下面的一级相对路径，形如 .dicttypeExport；读取时可以用完整路径，形如 .pop.dicttypeExport
     * @param jsonValue 这一支的 json 文本
     * @return 更新行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveSettingJson(String code, String jsonPath, String jsonValue) {
        return mapper.updateSettingJsonByCode(code, jsonPath, jsonValue);
    }
}
