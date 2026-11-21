package com.xinyirun.scm.core.system.mapper.sys.pages;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyirun.scm.bean.entity.sys.pages.SPagesEntity;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesExportVo;
import com.xinyirun.scm.bean.system.vo.sys.pages.SPagesVo;
import com.xinyirun.scm.bean.system.vo.sys.pages.setting.P00000068Vo;
import com.xinyirun.scm.bean.system.vo.sys.pages.setting.P00000128Vo;
import com.xinyirun.scm.bean.system.vo.sys.pages.setting.P00000158Vo;
import com.xinyirun.scm.common.constant.PageCodeConstant;
import com.xinyirun.scm.core.system.config.mybatis.typehandlers.pagesetting.RP00000128TypeHandler;
import com.xinyirun.scm.core.system.config.mybatis.typehandlers.pagesetting.RP0000068TypeHandler;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <p>
 * 页面表 Mapper 接口
 * </p>
 *
 * @author zxh
 * @since 2020-06-05
 */
@Repository
public interface SPagesMapper extends BaseMapper<SPagesEntity> {

    /**
     * 页面查询列表
     * @param page
     * @param searchCondition
     * @return
     */
    @Select("                                                                                                            "
        + "  SELECT                                                                                                      "
        + "         t.*,                                                                                                 "
        + "         c_staff.name as c_name,                                                                              "
        + "         u_staff.name as u_name                                                                               "
        + "    FROM                                                                                                      "
        + "  	    s_pages t                                                                                            "
        + "  LEFT JOIN m_staff c_staff ON t.c_id = c_staff.id                                                            "
        + "  LEFT JOIN m_staff u_staff ON t.u_id = u_staff.id                                                            "
        + "  where true                                                                                                  "
        + "    and (t.name like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)                      "
        + "    and (t.meta_title like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)                "
        + "    and (t.component like CONCAT ('%',#{p1.component,jdbcType=VARCHAR},'%') or #{p1.component,jdbcType=VARCHAR} is null)       "
        + "    and (t.code like CONCAT ('%',#{p1.code,jdbcType=VARCHAR},'%') or #{p1.code,jdbcType=VARCHAR} is null)                      "
        + "                                                                                                              ")
    IPage<SPagesVo> selectPage(Page page, @Param("p1") SPagesVo searchCondition);

    /**
     * 取某个页面设置里的一支配置
     *
     * setting_json 整列存的是这个页面所有个性设置的集合，这里只取其中一支，
     * 例如 .pop.dicttypeExport。整列为空时按空对象取，取不到就返回 null。
     *
     * @param code 页面编码
     * @param jsonPath 配置在 setting_json 里的位置，形如 .pop.dicttypeExport
     * @return 这一支配置的 json 文本，没有时返回 null
     */
    @Select("                                                                                       "
        + "  select JSON_UNQUOTE(JSON_EXTRACT(coalesce(setting_json, JSON_OBJECT()), CONCAT('$', #{p2}))) "
        + "    from s_pages                                                                        "
        + "   where code = #{p1}                                                                   "
        + "   limit 1                                                                              ")
    String selectSettingJsonByCode(@Param("p1") String code, @Param("p2") String jsonPath);

    /**
     * 写入某个页面设置里的一支配置
     *
     * 整列为空时先按空对象处理，只覆盖指定的这一支，同页面的其它设置不受影响。
     *
     * @param code 页面编码
     * @param jsonPath 配置在 setting_json 中 pop 下面的一级键，形如 .dicttypeExport
     *                 多级路径在赋值时不会自动创建中间的 pop 一层，所以外层单独写一次。
     * @param jsonValue 这一支的 json 文本
     * @return 更新行数
     */
    @Update("                                                                                                        "
        + "  update s_pages                                                                                    "
        + "     set setting_json = JSON_SET(coalesce(setting_json, JSON_OBJECT()), '$.pop',                     "
        + "                         JSON_SET(coalesce(JSON_EXTRACT(setting_json, '$.pop'), JSON_OBJECT()),       "
        + "                                  CONCAT('$', #{p2}), CAST(#{p3} AS JSON)))                         "
        + "   where code = #{p1}                                                                               ")
    int updateSettingJsonByCode(@Param("p1") String code, @Param("p2") String jsonPath, @Param("p3") String jsonValue);

    /**
     * 根据code获取明细
     * @param searchCondition
     * @return
     */
    @Select("                                                                                                                "
            + "  SELECT                                                                                                      "
            + "         t.*,                                                                                                 "
            + "    (select JSON_EXTRACT(s.page_json, '$.config.value') from s_pages s where s.code = '"+ PageCodeConstant.PAGE_PRODUCT_DAILY +"' and t.id = s.id) p128,"
            + "    (select JSON_EXTRACT(s.page_json, '$.config.value') from s_pages s where s.code = '"+ PageCodeConstant.PAGE_MONITOR +"' and t.id = s.id) p68,"
            + "    (select JSON_EXTRACT(s.page_json, '$.config.value') from s_pages s where s.code = '"+ PageCodeConstant.P_MONITOR_DIRECT +"' and t.id = s.id) p158,"
            + "         c_staff.name as c_name,                                                                              "
            + "         u_staff.name as u_name                                                                               "
            + "    FROM                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + "  LEFT JOIN m_staff c_staff ON t.c_id = c_staff.id                                                            "
            + "  LEFT JOIN m_staff u_staff ON t.u_id = u_staff.id                                                            "
            + "  where true                                                                                                  "
            + "    and (t.code like CONCAT ('%',#{p1.code,jdbcType=VARCHAR},'%'))                                            "
            + "                                                                                                              ")
    @Results({
            @Result(property = "p00000128Vo", column = "p128", javaType = P00000128Vo.class, typeHandler = RP00000128TypeHandler.class),
            @Result(property = "p00000068Vo", column = "p68", javaType = P00000068Vo.class, typeHandler = RP0000068TypeHandler.class),
            @Result(property = "p00000158Vo", column = "p158", javaType = P00000158Vo.class, typeHandler = RP0000068TypeHandler.class),
    })
    SPagesVo get(@Param("p1") SPagesVo searchCondition);

    /**
     * 按条件获取所有数据，没有分页
     * @param searchCondition
     * @return
     */
    @Select("                                                                                                            "
        + "  SELECT                                                                                                      "
        + "         t.*,                                                                                                 "
        + "         c_staff.name as c_name,                                                                              "
        + "         u_staff.name as u_name                                                                               "
        + "  FROM                                                                                                        "
        + "  	s_pages t                                                                                                "
        + "  LEFT JOIN m_staff c_staff ON t.c_id = c_staff.id                                                            "
        + "  LEFT JOIN m_staff u_staff ON t.u_id = u_staff.id                                                            "
        + "  where true                                                                                                  "
        + "    and (t.name like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)     "
        + "    and (t.meta_title  like CONCAT ('%',#{p1.meta_title,jdbcType=VARCHAR},'%') or #{p1.meta_title,jdbcType=VARCHAR} is null) "
        + "    and (t.code  like CONCAT ('%',#{p1.code,jdbcType=VARCHAR},'%') or #{p1.code,jdbcType=VARCHAR} is null)    "
        + "                                                                                                              ")
    List<SPagesVo> select(@Param("p1") SPagesVo searchCondition);

    /**
     * 没有分页，按id筛选条件
     * @param searchCondition
     * @return
     */
    @Select("   <script>   "
        + "  SELECT                                                                                        "
        + "       *                                                                                        "
        + "  FROM                                                                                          "
        + "  	s_pages t                                                                                  "
        + "  where t.id in                                                                                 "
        + "        <foreach collection='p1' item='item' index='index' open='(' separator=',' close=')'>    "
        + "         #{item.id}  "
        + "        </foreach>    "
        + "  </script>    ")
    List<SPagesVo> selectIdsIn(@Param("p1") List<SPagesVo> searchCondition);


    /**
     * 按条件获取数据
     * @param id
     * @return
     */
    @Select("    "
        + "  SELECT                                                                                                      "
        + "         t.*,                                                                                                 "
        + "         c_staff.name as c_name,                                                                              "
        + "         u_staff.name as u_name                                                                               "
        + "    FROM                                                                                                      "
        + "  	    s_pages t                                                                                            "
        + "  LEFT JOIN m_staff c_staff ON t.c_id = c_staff.id                                                            "
        + "  LEFT JOIN m_staff u_staff ON t.u_id = u_staff.id                                                            "
        + "  where t.id =  #{p1}                                                                                         "
        + "      ")
    SPagesVo selectId(@Param("p1") Long id);

    /**
     * 按条件获取所有数据，没有分页
     * @param code
     * @return
     */
    @Select("    "
            + "  SELECT                                                                                                      "
            + "         t.*                                                                                                  "
            + "    FROM                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + "  where t.code =  #{p1,jdbcType=VARCHAR}                                                                      "
            + "      ")
    SPagesVo selectByCode(@Param("p1") String code);

    /**
     * 更新数据
     */
    @Update("    "
            + "  UPDATE                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + " SET t.import_processing = 0                                                                                  "
            + "  where t.id =  #{p1}                                                                                         "
            + "      ")
    void updateImportProcessingFalse(@Param("p1") long id);

    /**
     * 更新数据
     */
    @Update("    "
            + "  UPDATE                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + " SET t.import_processing = 1                                                                                  "
            + "  where t.id =  #{p1}                                                                                         "
            + "      ")
    void updateImportProcessingTrue(@Param("p1") long id);

    /**
     * 更新数据
     */
    @Update("    "
            + "  UPDATE                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + " SET t.export_processing = 0                                                                                  "
            + "  where t.id =  #{p1}                                                                                         "
            + "      ")
    void updateExportProcessingFalse(@Param("p1") long id);

    /**
     * 更新数据
     */
    @Update("    "
            + "  UPDATE                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + " SET t.export_processing = 1                                                                                  "
            + "  where t.id =  #{p1}                                                                                         "
            + "      ")
    void updateExportProcessingTrue(@Param("p1") long id);

    /**
     * 查询导出条数
     * @param searchCondition
     * @return
     */
    @Select("                                                                                                           "
            + "  SELECT                                                                                                 "
            + "         count(1)                                                                                        "
            + "    FROM                                                                                                 "
            + "  	    s_pages t                                                                                       "
            + "  where true                                                                                             "
            + "    and (t.name like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)                      "
            + "    and (t.meta_title like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)                "
            + "    and (t.component like CONCAT ('%',#{p1.component,jdbcType=VARCHAR},'%') or #{p1.component,jdbcType=VARCHAR} is null)       "
            + "    and (t.code like CONCAT ('%',#{p1.code,jdbcType=VARCHAR},'%') or #{p1.code,jdbcType=VARCHAR} is null)                      "
    )
    int selectExportNum(@Param("p1") SPagesVo searchCondition);

    /**
     * 导出查询
     * @param searchCondition
     * @return
     */
    @Select("<script>                                                                                                   "
            + "  SELECT                                                                                                 "
            + "         t.code,                                                                                         "
            + "         t.name,                                                                                         "
            + "         t.component,                                                                                    "
            + "         t.perms,                                                                                        "
            + "         t.u_time,                                                                                       "
            + "         u_staff.name as u_name,                                                                         "
            + "         @row_num:= @row_num+ 1 as no                                                                    "
            + "    FROM                                                                                                      "
            + "  	    s_pages t                                                                                            "
            + "  LEFT JOIN m_staff u_staff ON t.u_id = u_staff.id                                                            "
            + "   ,(select @row_num:=0) t22                                                                                  "
            + "  where true                                                                                                  "
            + "    and (t.name like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)                      "
            + "    and (t.meta_title like CONCAT ('%',#{p1.name,jdbcType=VARCHAR},'%') or #{p1.name,jdbcType=VARCHAR} is null)                "
            + "    and (t.component like CONCAT ('%',#{p1.component,jdbcType=VARCHAR},'%') or #{p1.component,jdbcType=VARCHAR} is null)       "
            + "    and (t.code like CONCAT ('%',#{p1.code,jdbcType=VARCHAR},'%') or #{p1.code,jdbcType=VARCHAR} is null)                      "
            + "  <if test='p1.ids != null and p1.ids.length != 0'>                                                       "
            + "    and t.id in                                                                                         "
            + "      <foreach collection ='p1.ids' item='item' index='index' open='(' close=')' separator=','>         "
            + "          #{item}                                                                                       "
            + "       </foreach>                                                                                       "
            + "   </if>                                                                                                "
            + "  </script>                                                                                             "
    )
    List<SPagesExportVo> selectExportList(@Param("p1") SPagesVo searchCondition);
}
