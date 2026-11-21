package com.xinyirun.scm.core.system.serviceimpl.sys.config.dict;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyirun.scm.bean.entity.sys.config.dict.SDictTypeEntity;
import com.xinyirun.scm.bean.system.ao.result.CheckResultAo;
import com.xinyirun.scm.bean.system.ao.result.InsertResultAo;
import com.xinyirun.scm.bean.system.ao.result.UpdateResultAo;
import com.xinyirun.scm.bean.system.result.utils.v1.CheckResultUtil;
import com.xinyirun.scm.bean.system.result.utils.v1.InsertResultUtil;
import com.xinyirun.scm.bean.system.result.utils.v1.UpdateResultUtil;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeExportVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeStatisticsVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeVo;
import com.xinyirun.scm.common.constant.SystemConstants;
import com.xinyirun.scm.common.exception.system.BusinessException;
import com.xinyirun.scm.common.utils.bean.BeanUtilsSupport;
import com.xinyirun.scm.common.utils.datasource.DataSourceHelper;
import com.xinyirun.scm.core.system.mapper.sys.config.dict.SDictTypeMapper;
import com.xinyirun.scm.core.system.service.sys.config.dict.ISDictTypeService;
import com.xinyirun.scm.core.system.serviceimpl.base.v1.BaseServiceImpl;
import com.xinyirun.scm.core.system.utils.mybatis.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 字典类型表、字典主表 服务实现类
 * </p>
 *
 * @author zxh
 * @since 2019-08-23
 */
@Service
public class SDictTypeServiceImpl extends BaseServiceImpl<SDictTypeMapper, SDictTypeEntity> implements ISDictTypeService {

    @Autowired
    private SDictTypeMapper mapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 页面允许排序的字段。
     *
     * 排序参数会原样拼进排序子句，能排的只能是表头上有排序图标、并且与字典类型表列对应的三个字段，
     * 其余字段一律不参与排序，避免请求参数把任意内容带进排序语句。
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "u_time");

    /**
     * 没有指定排序或指定了不允许的字段时使用的排序，按最后修改时间倒序，与页面默认显示一致
     */
    private static final String DEFAULT_SORT = "-u_time";

    /**
     * 获取列表，页面查询
     *
     * @param searchCondition 查询条件
     * @return 分页后的字典类型数据
     */
    @Override
    public IPage<SDictTypeVo> selectPage(SDictTypeVo searchCondition) {
        // 分页条件
        Page<SDictTypeVo> pageCondition =
            new Page(searchCondition.getPageCondition().getCurrent(), searchCondition.getPageCondition().getSize());
        // 通过page进行排序，只接受白名单里的字段
        PageUtil.setSort(pageCondition, resolveSort(searchCondition.getPageCondition().getSort()));
        return mapper.selectPage(pageCondition, searchCondition);
    }

    /**
     * 获取列表，查询所有数据
     *
     * @param searchCondition 查询条件
     * @return 字典类型数据
     */
    @Override
    public List<SDictTypeVo> select(SDictTypeVo searchCondition) {
        // 查询 数据
        List<SDictTypeVo> list = mapper.select(searchCondition);
        return list;
    }

    /**
     * 检查页面传来的排序内容是否可以落到排序子句里。
     *
     * 页面传的形式是字段名表示升序、减号加字段名表示降序；不在白名单里就整个换成默认排序，
     * 不做部分保留，这样调用方拿到的要么是按指定字段排的，要么是按默认的更新时间倒序排的。
     *
     * @param sort 页面传来的排序内容
     * @return 可以参与排序的内容
     */
    private String resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String field = sort.startsWith("-") ? sort.substring(1) : sort;
        if (!SORTABLE_FIELDS.contains(field)) {
            return DEFAULT_SORT;
        }
        return sort;
    }

    /**
     * 统计总条数、正常使用条数、已停用条数
     *
     * @param searchCondition 查询条件，只使用编码与名称，不使用启用状态
     * @return 三个统计数字
     */
    @Override
    public SDictTypeStatisticsVo selectStatistics(SDictTypeVo searchCondition) {
        return mapper.selectStatistics(searchCondition);
    }

    /**
     * 获取列表，根据id查询所有数据
     *
     * @param searchCondition
     * @return
     */
    @Override
    public List<SDictTypeVo> selectIdsIn(List<SDictTypeVo> searchCondition) {
        // 查询 数据
        List<SDictTypeVo> list = mapper.selectIdsIn(searchCondition);
        return list;
    }

    /**
     * 批量导入逻辑
     *
     * @param entityList
     * @return
     */
    @CacheEvict(value = SystemConstants.CACHE_PC.CACHE_DICT_TYPE, allEntries=true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveBatches(List<SDictTypeEntity> entityList) {
        return super.saveBatch(entityList, 500);
    }

    /**
     * 批量删除复原
     * @param searchCondition
     * @return
     */
    @CacheEvict(value = SystemConstants.CACHE_PC.CACHE_DICT_TYPE, allEntries=true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteByIdsIn(List<SDictTypeVo> searchCondition) {
        List<SDictTypeVo> list = mapper.selectIdsIn(searchCondition);
        list.forEach(
            bean -> {
                bean.setIs_del(!bean.getIs_del());
            }
        );
        List<SDictTypeEntity> entities = BeanUtilsSupport.copyProperties(list, SDictTypeEntity.class);
        saveOrUpdateBatch(entities, 500);
    }

    /**
     * 插入一条记录（选择字段，策略插入）
     * @param entity 实体对象
     * @return
     */
    @CacheEvict(value = SystemConstants.CACHE_PC.CACHE_DICT_TYPE, 
                key = "T(com.xinyirun.scm.common.utils.datasource.DataSourceHelper).getCurrentDataSourceName() + '::' + #entity.code")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public InsertResultAo<Integer> insert(SDictTypeEntity entity) {
        // 插入前check
        CheckResultAo cr = checkLogic(entity.getCode());
        if (!cr.isSuccess()) {
            throw new BusinessException(cr.getMessage());
        }
        entity.setIs_del(false);
        // 插入逻辑保存
        return InsertResultUtil.OK(mapper.insert(entity));
    }

    /**
     * 更新一条记录（选择字段，策略更新）
     * @param entity 实体对象
     * @return
     */
    @CacheEvict(value = SystemConstants.CACHE_PC.CACHE_DICT_TYPE, 
                key = "T(com.xinyirun.scm.common.utils.datasource.DataSourceHelper).getCurrentDataSourceName() + '::' + #entity.code")
    @Transactional(rollbackFor = Exception.class)
    @Override
    public UpdateResultAo<Integer> update(SDictTypeEntity entity) {
        // 更新前check
        CheckResultAo cr = checkLogic(entity.getCode());
        if (!cr.isSuccess()) {
            throw new BusinessException(cr.getMessage());
        }
        // 更新逻辑保存
//        entity.setU_id(null);
//        entity.setU_time(null);
        return UpdateResultUtil.OK(mapper.updateById(entity));
    }

    /**
     * 获取列表，查询所有数据
     *
     * @param code
     * @return
     */
    @Override
    public List<SDictTypeVo> selectByCode(String code) {
        // 查询 数据
        List<SDictTypeVo> list = mapper.selectByCode(code);
        return list;
    }

    /**
     * 为excel做的check，仅仅是为了尝试是否能够反射调用
     * @param vo
     * @return
     */
    @Override
    public Boolean testCheck(SDictTypeEntity vo) {
        return true;
    }

    /**
     * 部分 导出
     *
     * @param searchConditionList
     * @return
     */
    @Override
    public List<SDictTypeExportVo> selectListExport(List<SDictTypeVo> searchConditionList) {
        return mapper.selectListExport(searchConditionList);
    }

    /**
     * 全部导出
     *
     * @param searchCondition
     * @return
     */
    @Override
    public List<SDictTypeExportVo> selectAllExport(SDictTypeVo searchCondition) {
        return mapper.selectAllExport(searchCondition);
    }

    /**
     * 查询字典类型当前已经进缓存的编码。
     *
     * 一个缓存项在存储里的键由三段组成：缓存名、当前租户的数据源名、字典类型编码。
     * 这里按前两段做前缀扫描，用游标分批取，不一次把整个库的键都取上来；
     * 取到后只留下编码那一段，页面拿到这些编码逐行比对，就知道哪一行已经有缓存。
     *
     * @return 已经进缓存的字典类型编码
     */
    @Override
    public List<String> selectCachedCodes() {
        String prefix = SystemConstants.CACHE_PC.CACHE_DICT_TYPE + "::"
            + DataSourceHelper.getCurrentDataSourceName() + "::";
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(1000).build();
        List<String> codes = new ArrayList<>();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    codes.add(key.substring(prefix.length()));
                }
            }
            return null;
        });
        return codes;
    }

    /**
     * 清空字典类型缓存
     *
     * 新增、修改、删除字典本身都会清缓存，这个方法给页面上的手工清理入口使用，
     * 例如缓存与库里的数据对不上时，由操作人主动清一次。
     *
     * @return 是否清理成功，缓存不存在时返回否
     */
    @Override
    public boolean refreshCache() {
        Cache cache = cacheManager.getCache(SystemConstants.CACHE_PC.CACHE_DICT_TYPE);
        if (cache == null) {
            return false;
        }
        cache.clear();
        return true;
    }

    /**
     * check逻辑
     * @return
     */
    public CheckResultAo checkLogic(String _code){
        // code查重
        List<SDictTypeVo> list = selectByCode(_code);
        if(list.size() > 1){
            return CheckResultUtil.NG("字典类型出现重复", list);
        }

        return CheckResultUtil.OK();
    }
}
