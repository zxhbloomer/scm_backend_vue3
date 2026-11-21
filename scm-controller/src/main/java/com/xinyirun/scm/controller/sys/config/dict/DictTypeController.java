package com.xinyirun.scm.controller.sys.config.dict;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xinyirun.scm.bean.entity.sys.config.dict.SDictTypeEntity;
import com.xinyirun.scm.bean.system.ao.result.JsonResultAo;
import com.xinyirun.scm.bean.system.result.utils.v1.ResultUtil;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictDataVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeExportConfigVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeExportVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeSettingVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeStatisticsVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeVo;
import com.xinyirun.scm.bean.system.vo.sys.rbac.role.SRoleVo;
import com.xinyirun.scm.common.annotations.RepeatSubmitAnnotion;
import com.xinyirun.scm.common.annotations.SysLogAnnotion;
import com.xinyirun.scm.common.enums.ResultEnum;
import com.xinyirun.scm.common.exception.system.BusinessException;
import com.xinyirun.scm.common.exception.system.InsertErrorException;
import com.xinyirun.scm.common.exception.system.UpdateErrorException;
import com.xinyirun.scm.common.utils.bean.BeanUtilsSupport;
import com.xinyirun.scm.common.utils.string.StringUtils;
import com.xinyirun.scm.core.system.service.sys.config.dict.ISDictDataService;
import com.xinyirun.scm.core.system.service.sys.config.dict.ISDictTypeService;
import com.xinyirun.scm.core.system.service.sys.pages.ISPagesService;
import com.xinyirun.scm.excel.export.ExcelUtil;
import com.xinyirun.scm.excel.upload.SystemExcelReader;
import com.xinyirun.scm.framework.base.controller.system.v1.SystemBaseController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author zhangxh
 */
@RestController
@RequestMapping(value = "/api/v1/dicttype")
@Slf4j
// @Api(tags = "字典主表相关")
public class DictTypeController extends SystemBaseController {

    @Autowired
    private ISDictTypeService isDictTypeService;

    @Autowired
    private ISDictDataService isDictDataService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ISPagesService isPagesService;

    /** 字典类型维护页在页面表里的编码，导出偏好按这个编码存放 */
    private static final String PAGE_CODE = "P00000035";

    /** 读取导出偏好时用的位置，可以带多级路径 */
    private static final String SETTING_READ_PATH = ".pop.dicttypeExport";

    /** 写入导出偏好时用的位置，只到 pop 下面一级，外层 pop 由写入语句单独处理 */
    private static final String SETTING_WRITE_PATH = ".dicttypeExport";

    /** 导出时间列的显示格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 主表可导出的字段键，生成表头与记录偏好都只认这一组 */
    private static final Set<String> MAIN_FIELD_KEYS =
            Set.of("code", "name", "items", "descr", "status", "uTime");

    /** 键值明细可导出的字段键 */
    private static final Set<String> ITEM_FIELD_KEYS =
            Set.of("dictValue", "label", "sort", "detailDescr", "detailStatus", "detailUTime");

//    @SysLogAnnotion("根据参数id，获取字典主表信息")
//    // @ApiOperation(value = "根据参数id，获取字典主表信息")
//    @PostMapping("{ id }")
//    @ResponseBody
//    public ResponseEntity<JsonResultAo<SDictTypeEntity>> info(@RequestParam("id") String id) {
//
//        SDictTypeEntity entity = isDictTypeService.getById(id);
//
////        ResponseEntity<OAuth2AccessToken
//        return ResponseEntity.ok().body(ResultUtil.OK(entity));
//    }

    @SysLogAnnotion("根据查询条件，获取字典主表信息")
    // @ApiOperation(value = "根据参数id，获取字典主表信息")
    @PostMapping("/list")
    @ResponseBody
    public ResponseEntity<JsonResultAo<IPage<SDictTypeVo>>> list(@RequestBody(required = false) SDictTypeVo searchCondition) {
        IPage<SDictTypeVo> page = isDictTypeService.selectPage(searchCondition);
        return ResponseEntity.ok().body(ResultUtil.OK(page));
    }

    @SysLogAnnotion("按编码与名称统计字典类型的数量")
    // @ApiOperation(value = "统计总数、正常使用数、已停用数")
    @PostMapping("/statistics")
    @ResponseBody
    public ResponseEntity<JsonResultAo<SDictTypeStatisticsVo>> statistics(@RequestBody(required = false) SDictTypeVo searchCondition) {
        return ResponseEntity.ok().body(ResultUtil.OK(isDictTypeService.selectStatistics(searchCondition)));
    }

    @SysLogAnnotion("按字典类型 id 批量获取键值明细")
    // @ApiOperation(value = "批量获取当页字典类型的键值明细")
    @PostMapping("/items")
    @ResponseBody
    public ResponseEntity<JsonResultAo<Map<Long, List<SDictDataVo>>>> items(@RequestBody(required = false) List<Long> dictTypeIds) {
        List<SDictDataVo> dataList = isDictDataService.selectItemsByDictTypeIds(dictTypeIds);
        // 页面按字典类型分组渲染，分组在这里一次做完，避免前端再循环一遍
        Map<Long, List<SDictDataVo>> grouped = new LinkedHashMap<>();
        for (SDictDataVo item : dataList) {
            grouped.computeIfAbsent(item.getDict_type_id(), key -> new ArrayList<>()).add(item);
        }
        return ResponseEntity.ok().body(ResultUtil.OK(grouped));
    }

    @SysLogAnnotion("查询已经进缓存的字典类型编码")
    // @ApiOperation(value = "查询已缓存的字典类型编码")
    @PostMapping("/cache/status")
    @ResponseBody
    public ResponseEntity<JsonResultAo<List<String>>> cacheStatus() {
        return ResponseEntity.ok().body(ResultUtil.OK(isDictTypeService.selectCachedCodes()));
    }

    @SysLogAnnotion("清空字典类型缓存")
    // @ApiOperation(value = "清空字典类型缓存")
    @PostMapping("/cache/refresh")
    @ResponseBody
    @RepeatSubmitAnnotion
    public ResponseEntity<JsonResultAo<Boolean>> refreshCache() {
        return ResponseEntity.ok().body(ResultUtil.OK(isDictTypeService.refreshCache()));
    }

    @SysLogAnnotion("字典主表数据更新保存")
    // @ApiOperation(value = "根据参数id，获取字典主表信息")
    @PostMapping("/save")
    @ResponseBody
    @RepeatSubmitAnnotion
    public ResponseEntity<JsonResultAo<SDictTypeVo>> save(@RequestBody(required = false) SDictTypeEntity bean) {

        if(isDictTypeService.update(bean).isSuccess()){
            SDictTypeVo vo = (SDictTypeVo) BeanUtilsSupport.copyProperties(isDictTypeService.getById(bean.getId()), SDictTypeVo.class);
            return ResponseEntity.ok().body(ResultUtil.OK(vo,"更新成功"));
        } else {
            throw new UpdateErrorException("保存的数据已经被修改，请查询后重新编辑更新。");
        }
    }

    @SysLogAnnotion("字典主表数据新增保存")
    // @ApiOperation(value = "根据参数id，获取字典主表信息")
    @PostMapping("/insert")
    @ResponseBody
    @RepeatSubmitAnnotion
    public ResponseEntity<JsonResultAo<SDictTypeVo>> insert(@RequestBody(required = false) SDictTypeEntity bean) {
        if(isDictTypeService.insert(bean).isSuccess()){
            SDictTypeVo vo = (SDictTypeVo) BeanUtilsSupport.copyProperties(isDictTypeService.getById(bean.getId()), SDictTypeVo.class);
            return ResponseEntity.ok().body(ResultUtil.OK(vo,"插入成功"));
        } else {
            throw new InsertErrorException("新增保存失败。");
        }
    }

    @SysLogAnnotion("字典主表数据导出")
    // @ApiOperation(value = "根据选择的数据，字典主表数据导出")
    @PostMapping("/export_all")
    public void exportAll(@RequestBody(required = false) SDictTypeVo searchCondition, HttpServletResponse response) throws IOException {
        List<SDictTypeExportVo> searchResult = isDictTypeService.selectAllExport(searchCondition);
        ExcelUtil<SDictTypeExportVo> util = new ExcelUtil<>(SDictTypeExportVo.class);
        util.exportExcel("字典主表数据导出", "字典主表数据", searchResult, response);
    }

    @SysLogAnnotion("字典主表数据导出")
    // @ApiOperation(value = "根据选择的数据，字典主表数据导出")
    @PostMapping("/export_selection")
    public void exportSelection(@RequestBody(required = false) List<SDictTypeVo> searchConditionList, HttpServletResponse response) throws IOException {
        List<SDictTypeExportVo> rtnList = isDictTypeService.selectListExport(searchConditionList);
        ExcelUtil<SDictTypeExportVo> util = new ExcelUtil<>(SDictTypeExportVo.class);
        util.exportExcel("字典主表数据导出", "字典主表数据", rtnList, response);
    }

    @SysLogAnnotion("字典主表数据逻辑删除复原")
    // @ApiOperation(value = "根据参数id，逻辑删除复原数据")
    @PostMapping("/delete")
    @ResponseBody
    @RepeatSubmitAnnotion
    public ResponseEntity<JsonResultAo<String>> delete(@RequestBody(required = false) List<SDictTypeVo> searchConditionList) {
        isDictTypeService.deleteByIdsIn(searchConditionList);
        return ResponseEntity.ok().body(ResultUtil.OK("OK"));
    }

    @SysLogAnnotion("字典类型数据导入")
    // @ApiOperation(value = "字典类型数据模板导入")
    @PostMapping("/import")
    public ResponseEntity<JsonResultAo<Object>> importData(@RequestBody(required = false) SDictTypeVo uploadData,
                                                         HttpServletResponse response) throws Exception {

        // file bean 保存数据库

        // 文件下载并check类型
        // 1、获取模板配置类
//        String json = "{\"dataRows\":{\"dataCols\":[{\"index\":0,\"name\":\"type\"},{\"index\":1,\"name\":\"code\"},{\"index\":2,\"name\":\"name\"},{\"index\":3,\"name\":\"descr\"},{\"index\":4,\"name\":\"simple_name\"}]},\"titleRows\":[{\"cols\":[{\"colSpan\":1,\"title\":\"角色类型\"},{\"colSpan\":1,\"title\":\"角色编码\"},{\"colSpan\":1,\"title\":\"角色名称\"},{\"colSpan\":1,\"title\":\"说明\"},{\"colSpan\":1,\"title\":\"简称\"}]}]}";
        String json = "{\"dataRows\":{\"dataCols\":[{\"index\":0,\"name\":\"type\"},{\"convertor\":\"date\",\"index\":1,\"name\":\"code\"},{\"index\":2,\"listValidators\":[{\"param\":[{\"name\":\"className\",\"value\":\"com.xinyirun.scm.core.logic.system.v1.serviceimpl.sys.config.dict.SDictTypeServiceImpl\"},{\"name\":\"functionName\",\"value\":\"testCheck\"}],\"validtorName\":\"reflection\"}],\"name\":\"name\"},{\"index\":3,\"name\":\"descr\"},{\"index\":4,\"name\":\"simpleName\"}]},\"titleRows\":[{\"cols\":[{\"colSpan\":1,\"title\":\"角色类型\"},{\"colSpan\":1,\"title\":\"角色编码\"},{\"colSpan\":1,\"title\":\"角色名称\"},{\"colSpan\":1,\"title\":\"说明\"},{\"colSpan\":1,\"title\":\"简称\"}]}]}";
        SystemExcelReader pr = super.downloadExcelAndImportData(uploadData.getUrl(), json);
        List<SDictTypeEntity> beans = pr.readBeans(SDictTypeEntity.class);
        if (pr.isDataValid()) {
            pr.closeAll();
            // 读取没有错误，开始插入
            isDictTypeService.saveBatches(beans);
            return ResponseEntity.ok().body(ResultUtil.OK(beans));
        } else {
            // 读取失败，需要返回错误
            File rtnFile = pr.getValidateResultsInFile(pr.getFileName());
            SRoleVo errorInfo = super.uploadFile(rtnFile.getAbsolutePath(), SRoleVo.class);
            pr.closeAll();
            return ResponseEntity.ok().body(ResultUtil.OK(errorInfo, ResultEnum.IMPORT_DATA_ERROR));
        }
    }

    /**
     * 读取本页导出弹窗上次保存的偏好。
     *
     * 页面第一次打开时用来还原导出范围、字段勾选、子数据集方式与磨砂开关；
     * 库里还没存过时返回空对象，前端按默认值显示。
     */
    @SysLogAnnotion("读取字典类型页的导出偏好")
    @PostMapping("/setting/get")
    @ResponseBody
    public ResponseEntity<JsonResultAo<SDictTypeSettingVo>> getSetting() {
        SDictTypeSettingVo setting = new SDictTypeSettingVo();
        try {
            String json = isPagesService.selectSettingJson(PAGE_CODE, SETTING_READ_PATH);
            if (StringUtils.isNotEmpty(json) && !"null".equals(json)) {
                SDictTypeSettingVo saved = JSON.parseObject(json, SDictTypeSettingVo.class);
                if (saved != null) {
                    setting = saved;
                }
            }
        } catch (Exception e) {
            // 库里存的内容不完整或格式变了，按没存过处理，让页面用默认值打开
            log.warn("读取字典类型页导出偏好失败，按默认值处理", e);
        }
        return ResponseEntity.ok().body(ResultUtil.OK(setting));
    }

    /**
     * 按弹窗里选好的范围、字段与子数据集方式生成导出文件。
     *
     * 导出前先把这次的偏好记到页面设置里，记录失败不影响文件生成。
     */
    @SysLogAnnotion("按配置导出字典类型数据")
    @PostMapping("/export_config")
    public void exportConfig(@RequestBody(required = false) SDictTypeExportConfigVo config, HttpServletResponse response) throws IOException {
        SDictTypeExportConfigVo exportConfig = config == null ? new SDictTypeExportConfigVo() : config;
        String scope = StringUtils.isEmpty(exportConfig.getScope()) ? "search" : exportConfig.getScope();
        if (!"search".equals(scope) && !"page".equals(scope) && !"all".equals(scope)) {
            throw new BusinessException("导出范围只能是当前检索结果、当前视窗页或系统全部字典");
        }
        String strategy = StringUtils.isEmpty(exportConfig.getStrategy()) ? "multiSheet" : exportConfig.getStrategy();
        if (!"multiSheet".equals(strategy) && !"flat".equals(strategy)) {
            throw new BusinessException("子数据集导出方式只能是多 Sheet 工作簿或平铺宽表");
        }
        List<String> mainFieldKeys = filterFieldKeys(exportConfig.getMainFields(), MAIN_FIELD_KEYS);
        List<String> itemFieldKeys = filterFieldKeys(exportConfig.getItemFields(), ITEM_FIELD_KEYS);
        if (mainFieldKeys.isEmpty() && itemFieldKeys.isEmpty()) {
            throw new BusinessException("请至少选择一个导出字段");
        }

        // 1、按范围取主表数据。全部字典类型不沿用列表筛选条件。
        SDictTypeVo condition = exportConfig.getSearchCondition() == null
                ? new SDictTypeVo() : exportConfig.getSearchCondition();
        List<SDictTypeVo> mainList;
        if ("all".equals(scope)) {
            mainList = isDictTypeService.selectForExport(new SDictTypeVo());
        } else if ("page".equals(scope)) {
            IPage<SDictTypeVo> page = isDictTypeService.selectPage(condition);
            mainList = page.getRecords();
        } else {
            mainList = isDictTypeService.selectForExport(condition);
        }

        // 2、按字典类型 id 一次取回全部键值明细，避免逐个类型查询。
        List<Long> idList = new ArrayList<>();
        for (SDictTypeVo row : mainList) {
            if (row.getId() != null) {
                idList.add(row.getId());
            }
        }
        List<SDictDataVo> dataList = idList.isEmpty()
                ? new ArrayList<>() : isDictDataService.selectItemsByDictTypeIds(idList);
        Map<Long, List<SDictDataVo>> dataMap = new LinkedHashMap<>();
        for (SDictDataVo item : dataList) {
            dataMap.computeIfAbsent(item.getDict_type_id(), key -> new ArrayList<>()).add(item);
        }

        // 3、按勾选的字段组装表头与数据行。未识别的字段键跳过，不输出该列。
        List<String> sheetNames = new ArrayList<>();
        List<List<String>> headers = new ArrayList<>();
        List<List<List<Object>>> sheetRows = new ArrayList<>();

        List<String> mainTitles = new ArrayList<>();
        for (String key : mainFieldKeys) {
            String title = mainFieldTitle(key);
            if (title != null) {
                mainTitles.add(title);
            }
        }
        List<String> itemTitles = new ArrayList<>();
        for (String key : itemFieldKeys) {
            String title = itemFieldTitle(key);
            if (title != null) {
                itemTitles.add(title);
            }
        }

        if ("flat".equals(strategy)) {
            // 平铺宽表：每行一条键值明细，主表信息重复展开；没有明细的字典类型也留一行。
            List<String> flatTitles = new ArrayList<>(mainTitles);
            for (String title : itemTitles) {
                flatTitles.add("明细·" + title);
            }
            List<List<Object>> flatRows = new ArrayList<>();
            for (SDictTypeVo row : mainList) {
                List<Object> mainCells = mainFieldCells(row, mainFieldKeys, dataMap);
                List<SDictDataVo> items = dataMap.get(row.getId());
                if (items == null || items.isEmpty()) {
                    List<Object> flatRow = new ArrayList<>(mainCells);
                    for (int i = 0; i < itemTitles.size(); i++) {
                        flatRow.add("");
                    }
                    flatRows.add(flatRow);
                } else {
                    for (SDictDataVo item : items) {
                        List<Object> flatRow = new ArrayList<>(mainCells);
                        flatRow.addAll(itemFieldCells(item, itemFieldKeys));
                        flatRows.add(flatRow);
                    }
                }
            }
            sheetNames.add("字典类型明细平铺");
            headers.add(flatTitles);
            sheetRows.add(flatRows);
        } else {
            // 多 Sheet：第一张放主表，第二张放全部键值明细，首列用字典类型编码关联。
            List<List<Object>> mainRows = new ArrayList<>();
            for (SDictTypeVo row : mainList) {
                mainRows.add(mainFieldCells(row, mainFieldKeys, dataMap));
            }
            sheetNames.add("字典类型主表");
            headers.add(mainTitles);
            sheetRows.add(mainRows);

            // 明细字段一个都没勾选时不建第二张表，否则会多出一张只有关联编码列的页签。
            if (!itemTitles.isEmpty()) {
                List<String> detailTitles = new ArrayList<>();
                detailTitles.add("字典类型编码");
                detailTitles.addAll(itemTitles);
                List<List<Object>> detailRows = new ArrayList<>();
                for (SDictTypeVo row : mainList) {
                    List<SDictDataVo> items = dataMap.get(row.getId());
                    if (items == null) {
                        continue;
                    }
                    for (SDictDataVo item : items) {
                        List<Object> detailRow = new ArrayList<>();
                        detailRow.add(row.getCode() == null ? "" : row.getCode());
                        detailRow.addAll(itemFieldCells(item, itemFieldKeys));
                        detailRows.add(detailRow);
                    }
                }
                sheetNames.add("字典键值明细");
                headers.add(detailTitles);
                sheetRows.add(detailRows);
            }
        }

        // 4、记下这次的偏好，失败只记日志，文件照常生成。
        saveExportSetting(exportConfig, scope, strategy, mainFieldKeys, itemFieldKeys);

        ExcelUtil.writeSheets("字典主表数据导出", sheetNames, headers, sheetRows, response);
    }

    /**
     * 主表字段键对应的中文表头；返回空表示不认识的键，导出时跳过。
     */
    private String mainFieldTitle(String key) {
        switch (key) {
            case "code":
                return "字典类型编码";
            case "name":
                return "字典类型名称";
            case "items":
                return "键值预览";
            case "descr":
                return "说明";
            case "status":
                return "启用状态";
            case "uTime":
                return "更新时间";
            default:
                return null;
        }
    }

    /**
     * 明细字段键对应的中文表头；返回空表示不认识的键，导出时跳过。
     */
    private String itemFieldTitle(String key) {
        switch (key) {
            case "dictValue":
                return "字典键值";
            case "label":
                return "字典标签";
            case "sort":
                return "排序";
            case "detailDescr":
                return "说明";
            case "detailStatus":
                return "状态";
            case "detailUTime":
                return "更新时间";
            default:
                return null;
        }
    }

    /**
     * 取一行主表的单元格值，顺序与勾选的字段键一致。
     */
    private List<Object> mainFieldCells(SDictTypeVo row, List<String> keys, Map<Long, List<SDictDataVo>> dataMap) {
        List<Object> cells = new ArrayList<>();
        for (String key : keys) {
            switch (key) {
                case "code":
                    cells.add(row.getCode() == null ? "" : row.getCode());
                    break;
                case "name":
                    cells.add(row.getName() == null ? "" : row.getName());
                    break;
                case "items":
                    cells.add(buildItemsText(dataMap.get(row.getId())));
                    break;
                case "descr":
                    cells.add(row.getDescr() == null ? "" : row.getDescr());
                    break;
                case "status":
                    cells.add(Boolean.TRUE.equals(row.getIs_del()) ? "已停用" : "正常生效");
                    break;
                case "uTime":
                    cells.add(formatTime(row.getU_time()));
                    break;
                default:
                    break;
            }
        }
        return cells;
    }

    /**
     * 取一行键值明细的单元格值，顺序与勾选的字段键一致。
     */
    private List<Object> itemFieldCells(SDictDataVo item, List<String> keys) {
        List<Object> cells = new ArrayList<>();
        for (String key : keys) {
            switch (key) {
                case "dictValue":
                    cells.add(item.getDict_value() == null ? "" : item.getDict_value());
                    break;
                case "label":
                    cells.add(item.getLabel() == null ? "" : item.getLabel());
                    break;
                case "sort":
                    cells.add(item.getSort() == null ? "" : item.getSort());
                    break;
                case "detailDescr":
                    cells.add(item.getDescr() == null ? "" : item.getDescr());
                    break;
                case "detailStatus":
                    cells.add(Boolean.TRUE.equals(item.getIs_del()) ? "已删除" : "正常");
                    break;
                case "detailUTime":
                    cells.add(formatTime(item.getU_time()));
                    break;
                default:
                    break;
            }
        }
        return cells;
    }

    /**
     * 把字典类型的键值明细拼成预览文字，格式为「值:标签」。
     * 多个键值之间换行，导出后同一个单元格里每条占一行；已删除的键值在末尾标注。
     */
    private String buildItemsText(List<SDictDataVo> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SDictDataVo item : items) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(item.getDict_value() == null ? "" : item.getDict_value());
            builder.append(":");
            builder.append(item.getLabel() == null ? "" : item.getLabel());
            if (Boolean.TRUE.equals(item.getIs_del())) {
                builder.append("（已删除）");
            }
        }
        return builder.toString();
    }

    /**
     * 时间列按固定格式输出，空值输出空串。
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FORMATTER.format(time);
    }

    /**
     * 按白名单挑出认识的字段键，顺序保持调用方给的顺序。
     *
     * 调用方多传或不认识的键直接丢掉，避免把清单以外的列写进文件或偏好。
     */
    private List<String> filterFieldKeys(List<String> keys, Set<String> allowed) {
        List<String> result = new ArrayList<>();
        if (keys == null) {
            return result;
        }
        for (String key : keys) {
            if (key != null && allowed.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * 把本次导出偏好写进页面设置，失败只记日志，不影响本次导出。
     *
     * 写的是校验与过滤之后的值：范围与子数据集方式此时已经补过缺省值、字段键只保留认识的，
     * 直接写入参原值会把空串和不认识的键也存进去。
     */
    private void saveExportSetting(SDictTypeExportConfigVo config, String scope, String strategy,
                                   List<String> mainFieldKeys, List<String> itemFieldKeys) {
        try {
            SDictTypeSettingVo setting = new SDictTypeSettingVo();
            setting.setGlass(config.getGlass());
            setting.setScope(scope);
            setting.setStrategy(strategy);
            setting.setMainFields(mainFieldKeys);
            setting.setItemFields(itemFieldKeys);
            isPagesService.saveSettingJson(PAGE_CODE, SETTING_WRITE_PATH, JSON.toJSONString(setting));
        } catch (Exception e) {
            log.warn("保存字典类型页导出偏好失败，本次导出不受影响", e);
        }
    }
}

