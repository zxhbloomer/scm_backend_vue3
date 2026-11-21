package com.xinyirun.scm.core.system.mapper.sys.config.dict;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyirun.scm.bean.entity.sys.config.dict.SDictTypeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeExportVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeStatisticsVo;
import com.xinyirun.scm.bean.system.vo.sys.config.dict.SDictTypeVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <p>
 * 字典类型表、字典主表 Mapper 接口
 * </p>
 *
 * @author zxh
 * @since 2019-08-23
 */
@Repository
public interface SDictTypeMapper extends BaseMapper<SDictTypeEntity> {
    /**
     * 页面查询列表
     *
     * 连接员工表取最后修改人的姓名，让列表可以直接显示是谁改的，不必再单独查一次员工信息。
     * 编码、名称、启用状态三个条件都是传了才参与过滤，不传就当没有这个条件。
     *
     * @param page 分页参数
     * @param searchCondition 查询条件
     * @return 分页后的字典类型数据
     */
    @Select("   <script>   "
        + " select t.*, ms.name as u_name "
        + "   from s_dict_type t "
        + "   left join m_staff ms on ms.id = t.u_id "
        + "  where true "
        + "        <if test=\"p1.code != null and p1.code != ''\"> and t.code like CONCAT('%', #{p1.code}, '%') </if> "
        + "        <if test=\"p1.name != null and p1.name != ''\"> and t.name like CONCAT('%', #{p1.name}, '%') </if> "
        + "        <if test=\"p1.is_del != null\"> and t.is_del = #{p1.is_del} </if> "
        + "  </script>    ")
    IPage<SDictTypeVo> selectPage(Page page, @Param("p1") SDictTypeVo searchCondition );

    /**
     * 按条件获取所有数据，没有分页
     *
     * 与分页查询用同一套条件，同样带出最后修改人的姓名。
     *
     * @param searchCondition 查询条件
     * @return 字典类型数据
     */
    @Select("   <script>   "
        + " select t.*, ms.name as u_name "
        + "   from s_dict_type t "
        + "   left join m_staff ms on ms.id = t.u_id "
        + "  where true "
        + "        <if test=\"p1.code != null and p1.code != ''\"> and t.code like CONCAT('%', #{p1.code}, '%') </if> "
        + "        <if test=\"p1.name != null and p1.name != ''\"> and t.name like CONCAT('%', #{p1.name}, '%') </if> "
        + "        <if test=\"p1.is_del != null\"> and t.is_del = #{p1.is_del} </if> "
        + "  </script>    ")
    List<SDictTypeVo> select(@Param("p1") SDictTypeVo searchCondition );

    /**
     * 按查询条件统计总条数、正常使用条数、已停用条数
     *
     * 编码、名称、启用状态三个条件都是传了才参与过滤，与分页查询用同一套条件，
     * 这样页面上的统计数字对应当前的查询结果，不会一直是全部数据的汇总。
     * 一行数据都没有时合计值为 null，用 coalesce 转成零，避免页面显示空白。
     *
     * @param searchCondition 查询条件
     * @return 三个统计数字
     */
    @Select("   <script>   "
        + " select count(*) as total, "
        + "        coalesce(sum(case when t.is_del = 0 then 1 else 0 end), 0) as normal, "
        + "        coalesce(sum(case when t.is_del = 1 then 1 else 0 end), 0) as disabled "
        + "   from s_dict_type t "
        + "  where true "
        + "        <if test=\"p1.code != null and p1.code != ''\"> and t.code like CONCAT('%', #{p1.code}, '%') </if> "
        + "        <if test=\"p1.name != null and p1.name != ''\"> and t.name like CONCAT('%', #{p1.name}, '%') </if> "
        + "        <if test=\"p1.is_del != null\"> and t.is_del = #{p1.is_del} </if> "
        + "  </script>    ")
    SDictTypeStatisticsVo selectStatistics(@Param("p1") SDictTypeVo searchCondition );

    /**
     * 没有分页，按id筛选条件
     * @param searchCondition
     * @return
     */
    @Select("   <script>   "
        + " select t.* "
        + "   from s_dict_type t "
        + "  where t.id in "
        + "        <foreach collection='p1' item='item' index='index' open='(' separator=',' close=')'>    "
        + "         #{item.id}  "
        + "        </foreach>    "
        + "  </script>    ")
    List<SDictTypeVo> selectIdsIn(@Param("p1") List<SDictTypeVo> searchCondition );

    /**
     * 按条件获取所有数据，没有分页
     * @param code
     * @return
     */
    @Select("    "
        + " select t.* "
        + "   from s_dict_type t "
        + "  where true "
        + "    and t.code =  #{p1}"
        + "      ")
    List<SDictTypeVo> selectByCode(@Param("p1") String code);

    /**
     * 部分导出
     * @param searchConditionList
     * @return
     */
    @Select("   <script>   "
            + " select t.name,                                                                                          "
            + "        t.code,                                                                                          "
            + "        t.descr,                                                                                         "
            + "        if(t.is_del, '已删除', '未删除') is_del,                                                            "
            + "        t.u_time,                                                                                         "
            + "       @row_num:= @row_num+ 1 as no                                      "
            + "   from s_dict_type t "
            + "   ,(select @row_num:=0) t3                                              "
            + "  where t.id in "
            + "        <foreach collection='p1' item='item' index='index' open='(' separator=',' close=')'>    "
            + "         #{item.id}  "
            + "        </foreach>    "
            + "  </script>    ")
    List<SDictTypeExportVo> selectListExport(@Param("p1") List<SDictTypeVo> searchConditionList);


    /**
     * 全部导出
     *
     * 编码、名称、启用状态三个条件都是传了才参与过滤，与列表用同一套条件。
     *
     * @param searchCondition 查询条件
     * @return 导出用的字典类型数据
     */
    @Select("   <script>   "
            + " select t.name,                                                                                          "
            + "        t.code,                                                                                          "
            + "        t.descr,                                                                                         "
            + "        if(t.is_del, '已删除', '未删除') is_del,                                                            "
            + "        t.u_time,                                                                                         "
            + "       @row_num:= @row_num+ 1 as no                                                                      "
            + "   from s_dict_type t "
            + "   ,(select @row_num:=0) t3                                              "
            + "  where true "
            + "        <if test='p1.code != null and p1.code.length() > 0'> and t.code like CONCAT('%', #{p1.code}, '%') </if> "
            + "        <if test='p1.name != null and p1.name.length() > 0'> and t.name like CONCAT('%', #{p1.name}, '%') </if> "
            + "        <if test='p1.is_del != null'> and t.is_del = #{p1.is_del} </if> "
            + "  </script>    ")
    List<SDictTypeExportVo> selectAllExport(@Param("p1") SDictTypeVo searchCondition);

    /**
     * 按查询条件取导出用的主表数据
     *
     * 与列表用同一套筛选条件，排序按更新时间倒序，与列表默认顺序一致。
     *
     * @param searchCondition 查询条件
     * @return 字典类型数据
     */
    @Select("   <script>   "
            + " select t.id, t.code, t.name, t.descr, t.is_del, t.u_time, ms.name as u_name "
            + "   from s_dict_type t "
            + "   left join m_staff ms on ms.id = t.u_id "
            + "  where true "
            + "        <if test='p1.code != null and p1.code.length() > 0'> and t.code like CONCAT('%', #{p1.code}, '%') </if> "
            + "        <if test='p1.name != null and p1.name.length() > 0'> and t.name like CONCAT('%', #{p1.name}, '%') </if> "
            + "        <if test='p1.is_del != null'> and t.is_del = #{p1.is_del} </if> "
            + "  order by t.u_time desc, t.id desc "
            + "  </script>    ")
    List<SDictTypeVo> selectForExport(@Param("p1") SDictTypeVo searchCondition);
}
