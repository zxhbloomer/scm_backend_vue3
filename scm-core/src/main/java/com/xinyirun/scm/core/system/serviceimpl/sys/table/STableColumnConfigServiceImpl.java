package com.xinyirun.scm.core.system.serviceimpl.sys.table;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xinyirun.scm.bean.entity.sys.table.STableColumnConfigEntity;
import com.xinyirun.scm.bean.entity.sys.table.STableColumnConfigDetailEntity;
import com.xinyirun.scm.bean.entity.sys.table.STableConfigEntity;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesVo;
import com.xinyirun.scm.bean.system.vo.sys.table.STableColumnCheckVo;
import com.xinyirun.scm.bean.system.vo.sys.table.STableColumnConfigVo;
import com.xinyirun.scm.bean.system.vo.sys.table.STableColumnConfigDetailVo;
import com.xinyirun.scm.bean.system.vo.sys.table.STableConfigVo;
import com.xinyirun.scm.core.system.mapper.sys.table.STableColumnConfigMapper;
import com.xinyirun.scm.core.system.mapper.sys.table.STableColumnConfigDetailMapper;
import com.xinyirun.scm.core.system.mapper.sys.table.STableConfigMapper;
import com.xinyirun.scm.core.system.mapper.sys.pages.SPagesMapper;
import com.xinyirun.scm.core.system.service.sys.table.ISTableColumnConfigService;
import com.xinyirun.scm.core.system.serviceimpl.common.autocode.STableAutoCodeServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author xinyirun
 * @since 2022-08-29
 */
@Service
public class STableColumnConfigServiceImpl extends ServiceImpl<STableColumnConfigMapper, STableColumnConfigEntity> implements ISTableColumnConfigService {

    @Autowired
    private STableColumnConfigMapper mapper;

    @Autowired
    private STableConfigMapper sTableConfigMapper;

    @Autowired
    private STableAutoCodeServiceImpl autoCode;

    @Autowired
    private STableColumnConfigDetailMapper detailMapper;
    
    @Autowired
    private SPagesMapper sPagesMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<STableColumnConfigVo> list(STableColumnConfigVo vo) {
        // 主表配置一次查出，分组子列再按 config_id 单独补上
        List<STableColumnConfigVo> result = mapper.listWithDetails(vo);

        for (STableColumnConfigVo item : result) {
            if (item.getIs_group() != null && item.getIs_group() == 1) {
                item.setGroupChildren(toGroupChildren(mapper.listGroupChildren(item.getId())));
            }
        }

        return result;
    }

    /**
     * 把明细表的子列数据装成主表 VO。
     * 前端按同一种列结构渲染普通列与分组子列，所以这里统一成主表 VO；
     * 明细表没有固定位置和系统列标记，转换后这两项为空。
     */
    private List<STableColumnConfigVo> toGroupChildren(List<STableColumnConfigDetailVo> details) {
        List<STableColumnConfigVo> children = new ArrayList<>();
        if (details == null || details.isEmpty()) {
            return children;
        }
        for (STableColumnConfigDetailVo detail : details) {
            STableColumnConfigVo child = new STableColumnConfigVo();
            child.setId(detail.getId());
            child.setTable_code(detail.getTable_code());
            child.setTable_id(detail.getTable_id());
            child.setName(detail.getName());
            child.setLabel(detail.getLabel());
            child.setIs_enable(detail.getIs_enable());
            child.setIs_delete(detail.getIs_delete());
            child.setSort(detail.getSort());
            child.setColumn_type(detail.getColumn_type());
            children.add(child);
        }
        return children;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetTableColumns(List<STableColumnConfigVo> configs, String pageCode) {
        if (configs == null || configs.isEmpty()) {
            throw new RuntimeException("重置配置数据不能为空");
        }

        // 页面还没有表格配置时，这里顺手登记一条
        STableConfigVo tableConfig = getTableConfigForReset(pageCode);

        // 重置只做两件事：顺序回到表格当前列、所有列恢复显示。
        // 固定位置和系统列标记是使用者自己设过的，得跟着原列保留，所以删之前先按列名读出来，删完再写回
        Map<String, STableColumnConfigVo> oldFixedSetting = readFixedSetting(pageCode);
        deleteUserConfigData(pageCode);
        insertResetConfigData(configs, tableConfig, oldFixedSetting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<STableColumnConfigVo> resetTableColumnsAndReturn(List<STableColumnConfigVo> configs, String pageCode) {
        resetTableColumns(configs, pageCode);

        STableColumnConfigVo queryVo = new STableColumnConfigVo();
        queryVo.setPage_code(pageCode);
        return this.list(queryVo);
    }

    /**
     * 按页面编号取表格配置，没有就新登记一条，页面名称从 s_pages 里取。
     */
    private STableConfigVo getTableConfigForReset(String pageCode) {
        STableColumnConfigVo searchVo = new STableColumnConfigVo();
        searchVo.setPage_code(pageCode);
        STableConfigVo tableConfig = sTableConfigMapper.get(searchVo);
        if (tableConfig != null) {
            return tableConfig;
        }

        String pageName = "";
        SPagesVo pageInfo = sPagesMapper.selectByCode(pageCode);
        if (pageInfo != null && pageInfo.getName() != null) {
            pageName = pageInfo.getName();
        }

        STableConfigEntity newTableConfig = new STableConfigEntity();
        newTableConfig.setCode(autoCode.autoCode().getCode());
        newTableConfig.setName(pageName);
        newTableConfig.setPage_code(pageCode);
        // type=1 表示这是列配置页面
        newTableConfig.setType("1");
        sTableConfigMapper.insert(newTableConfig);

        STableConfigVo created = new STableConfigVo();
        created.setId(newTableConfig.getId());
        created.setCode(newTableConfig.getCode());
        created.setName(newTableConfig.getName());
        created.setPage_code(newTableConfig.getPage_code());
        created.setType(newTableConfig.getType());
        return created;
    }

    /**
     * 按页面读出各列现在的固定位置和系统列标记，键是列名。
     * 重置会先删后插，删之前把这两项留一份，插的时候按列名认领回去。
     */
    private Map<String, STableColumnConfigVo> readFixedSetting(String pageCode) {
        Map<String, STableColumnConfigVo> setting = new HashMap<>();
        STableColumnConfigVo query = new STableColumnConfigVo();
        query.setPage_code(pageCode);
        List<STableColumnConfigVo> rows = mapper.listWithDetails(query);
        if (rows == null) {
            return setting;
        }
        for (STableColumnConfigVo row : rows) {
            STableColumnConfigVo keep = new STableColumnConfigVo();
            keep.setFix(row.getFix());
            keep.setPin(row.getPin());
            // 同一页允许有重名列（创建人与更新人都是 c_name），先出现的那行保留固定位置，
            // 后面的不覆盖，免得把另一列的固定位置套到这一列上
            setting.putIfAbsent(row.getName(), keep);
        }
        return setting;
    }

    /**
     * 删除整页的列配置：先删分组子列，再删主表。
     * 子列必须先删，主表删了就拿不到 config_id，明细会变成没有父行的野行。
     */
    private void deleteUserConfigData(String pageCode) {
        STableColumnConfigVo queryCondition = new STableColumnConfigVo();
        queryCondition.setPage_code(pageCode);
        List<STableColumnConfigVo> existingConfigs = mapper.list(queryCondition);

        if (existingConfigs != null) {
            for (STableColumnConfigVo config : existingConfigs) {
                if (config.getIs_group() != null && config.getIs_group() == 1) {
                    QueryWrapper<STableColumnConfigDetailEntity> detailWrapper = new QueryWrapper<>();
                    detailWrapper.eq("config_id", config.getId());
                    detailMapper.delete(detailWrapper);
                }
            }
        }

        STableColumnConfigVo deleteCondition = new STableColumnConfigVo();
        deleteCondition.setPage_code(pageCode);
        mapper.delete(deleteCondition);
    }

    /**
     * 插入重置后的列配置：顺序按前端给的数组，所有列恢复显示，
     * 原来的固定位置与系统列标记按列名认领回来。
     */
    private void insertResetConfigData(List<STableColumnConfigVo> configs, STableConfigVo tableConfig,
                                       Map<String, STableColumnConfigVo> oldFixedSetting) {
        for (int i = 0; i < configs.size(); i++) {
            STableColumnConfigVo configVo = configs.get(i);
            STableColumnConfigVo oldSetting = oldFixedSetting.get(configVo.getName());

            STableColumnConfigEntity mainEntity = new STableColumnConfigEntity();
            mainEntity.setTable_id(tableConfig.getId());
            mainEntity.setTable_code(tableConfig.getCode());
            mainEntity.setName(configVo.getName());
            mainEntity.setLabel(configVo.getLabel());
            mainEntity.setSort(i);
            mainEntity.setIs_group(configVo.getIs_group());
            // 没给值时按数据列写，重置时前端漏传也不会把列性质写空；分组父列本身没有取值字段，一律算非数据列
            mainEntity.setColumn_type(normalizeColumnType(configVo.getColumn_type(), configVo.getIs_group()));
            mainEntity.setFix(oldSetting != null && Boolean.TRUE.equals(oldSetting.getFix()));
            mainEntity.setPin(oldSetting != null ? oldSetting.getPin() : null);
            // 重置后所有列都显示，不能沿用删掉的那份显示状态
            mainEntity.setIs_enable(true);
            mainEntity.setIs_delete(false);
            this.save(mainEntity);

            if (configVo.getIs_group() != null && configVo.getIs_group() == 1
                    && configVo.getGroupChildren() != null && !configVo.getGroupChildren().isEmpty()) {
                insertGroupChildren(configVo.getGroupChildren(), mainEntity.getId(), tableConfig);
            }
        }
    }

    /**
     * 归整列性质：分组父列固定算非数据列，其余只认 1，其他值一律按数据列处理。
     *
     * @param value - 前端提交的列性质，可能为空
     * @param isGroup - 是否分组父列，1 表示是
     * @returns 0 表示数据列可以导出，1 表示非数据列导出跳过
     */
    private String normalizeColumnType(String value, Integer isGroup) {
        if (isGroup != null && isGroup == 1) {
            return "1";
        }
        return "1".equals(value == null ? null : value.trim()) ? "1" : "0";
    }

    /**
     * 插入分组子列，顺序按数组先后。
     */
    private void insertGroupChildren(List<STableColumnConfigVo> children, Integer configId, STableConfigVo tableConfig) {
        for (int i = 0; i < children.size(); i++) {
            STableColumnConfigVo childVo = children.get(i);

            STableColumnConfigDetailEntity detailEntity = new STableColumnConfigDetailEntity();
            detailEntity.setConfig_id(configId);
            detailEntity.setTable_id(tableConfig.getId());
            detailEntity.setTable_code(tableConfig.getCode());
            detailEntity.setName(childVo.getName());
            detailEntity.setLabel(childVo.getLabel());
            detailEntity.setSort(i);
            detailEntity.setColumn_type(normalizeColumnType(childVo.getColumn_type(), null));
            detailEntity.setIs_enable(true);
            detailEntity.setIs_delete(false);
            detailMapper.insert(detailEntity);
        }
    }

    /**
     * 比对库里的列配置与前端表格当前的列。
     * 表格里多出配置没有的列、或配置里的列表格已经没有了，都算有变化，前端据此提示使用者确认一次列配置。
     * 系统列（序号、操作）由前端固定提供，不参与比对。
     */
    @Override
    public STableColumnCheckVo check(STableColumnCheckVo vo) {
        STableColumnCheckVo result = new STableColumnCheckVo();
        List<String> currentColumns = vo == null || vo.getCurrent_columns() == null
                ? new ArrayList<>()
                : vo.getCurrent_columns();
        result.setPage_code(vo == null ? null : vo.getPage_code());
        result.setCurrent_columns(currentColumns);
        result.setAdded(new ArrayList<>());
        result.setRemoved(new ArrayList<>());
        result.setChanged(false);

        if (vo == null || vo.getPage_code() == null || vo.getPage_code().isEmpty()) {
            return result;
        }

        STableColumnConfigVo query = new STableColumnConfigVo();
        query.setPage_code(vo.getPage_code());
        List<STableColumnConfigVo> rows = this.list(query);
        if (rows == null || rows.isEmpty()) {
            // 这一页还没登记过列配置，第一次打开弹窗会自己登记，不算列有变化
            return result;
        }

        List<String> configured = new ArrayList<>();
        for (STableColumnConfigVo row : rows) {
            if (Boolean.TRUE.equals(row.getFix())) {
                continue;
            }
            if (row.getIs_group() != null && row.getIs_group() == 1
                    && row.getGroupChildren() != null && !row.getGroupChildren().isEmpty()) {
                for (STableColumnConfigVo child : row.getGroupChildren()) {
                    configured.add(child.getName());
                }
            } else {
                configured.add(row.getName());
            }
        }

        for (String name : currentColumns) {
            if (!configured.contains(name)) {
                result.getAdded().add(name);
            }
        }
        for (String name : configured) {
            if (!currentColumns.contains(name)) {
                result.getRemoved().add(name);
            }
        }

        // 顺序不参与比对：页面上的先后本来就跟着保存的配置走，把顺序算进来会让每页都提示有变化
        result.setChanged(!result.getAdded().isEmpty() || !result.getRemoved().isEmpty());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveList(List<STableColumnConfigVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }

        String pageCode = list.get(0).getPage_code();
        if (pageCode == null || pageCode.isEmpty()) {
            // 没带页面编号时，保存会凭空新建一套表格配置，等于写进了别的页面，必须拦住
            throw new RuntimeException("保存列配置缺少 page_code，请刷新页面后重试");
        }

        // 列配置按页面共用一份，保存就是整份替换：先删干净再按新顺序插
        deleteUserConfigData(pageCode);
        insertAllConfigData(list, pageCode);
    }

    /**
     * 插入整份列配置，顺序按数组先后，pin 为固定位置，fix 为系统列标记。
     */
    private void insertAllConfigData(List<STableColumnConfigVo> list, String pageCode) {
        STableConfigVo tableConfigVo = getTableConfigForReset(pageCode);

        for (int i = 0; i < list.size(); i++) {
            STableColumnConfigVo vo = list.get(i);
            vo.setSort(i);
            vo.setTable_code(tableConfigVo.getCode());
            vo.setTable_id(tableConfigVo.getId());

            STableColumnConfigEntity mainEntity = new STableColumnConfigEntity();
            mainEntity.setName(vo.getName());
            mainEntity.setLabel(vo.getLabel());
            mainEntity.setSort(vo.getSort());
            mainEntity.setFix(vo.getFix());
            mainEntity.setPin(vo.getPin());
            mainEntity.setIs_enable(vo.getIs_enable() != null ? vo.getIs_enable() : true);
            mainEntity.setIs_delete(vo.getIs_delete() != null ? vo.getIs_delete() : false);
            mainEntity.setIs_group(vo.getIs_group());
            // 没给值时按数据列写，保存接口被老版本前端调用时也能正常写入；分组父列本身没有取值字段，一律算非数据列
            mainEntity.setColumn_type(normalizeColumnType(vo.getColumn_type(), vo.getIs_group()));
            mainEntity.setTable_code(vo.getTable_code());
            mainEntity.setTable_id(vo.getTable_id());

            mapper.insert(mainEntity);

            if (vo.getIs_group() != null && vo.getIs_group() == 1
                    && vo.getGroupChildren() != null && !vo.getGroupChildren().isEmpty()) {
                insertGroupChildrenData(vo.getGroupChildren(), mainEntity.getId(), tableConfigVo);
            }
        }
    }

    /**
     * 插入分组子列，顺序按数组先后。
     */
    private void insertGroupChildrenData(List<STableColumnConfigVo> children, Integer parentId, STableConfigVo tableConfig) {
        for (int i = 0; i < children.size(); i++) {
            STableColumnConfigVo childVo = children.get(i);

            STableColumnConfigDetailEntity childEntity = new STableColumnConfigDetailEntity();
            childEntity.setConfig_id(parentId);
            childEntity.setTable_code(tableConfig.getCode());
            childEntity.setTable_id(tableConfig.getId());
            childEntity.setName(childVo.getName());
            childEntity.setLabel(childVo.getLabel());
            childEntity.setSort(i);
            childEntity.setColumn_type(normalizeColumnType(childVo.getColumn_type(), null));
            childEntity.setIs_enable(childVo.getIs_enable() != null ? childVo.getIs_enable() : true);
            childEntity.setIs_delete(childVo.getIs_delete() != null ? childVo.getIs_delete() : false);
            detailMapper.insert(childEntity);
        }
    }
}
