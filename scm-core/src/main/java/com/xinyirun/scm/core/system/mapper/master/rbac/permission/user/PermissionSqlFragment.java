package com.xinyirun.scm.core.system.mapper.master.rbac.permission.user;

import com.xinyirun.scm.common.constant.DictConstant;

/**
 * 员工权限判定链的公共 SQL 片段
 *
 * 这段连接回答同一个问题：某个员工通过岗位、角色关联到哪些权限。
 * 原先在 MUserPermissionRbacMapper 里复制了 6 份，改一处要同步 6 处，容易漏。
 *
 * 别名固定：ps 员工-岗位、pp 岗位-角色、pr 角色、prp 角色-权限、pm 权限。
 * 这几个别名与各查询原有的外层别名（t0、t1、t2、t3、tab、tab1、subt0）不冲突。
 */
public class PermissionSqlFragment {

    /**
     * 员工权限判定链，不含 WHERE，调用方在链后继续接自己的关联或过滤条件
     */
    public static final String STAFF_PERMISSION_JOINS =
        " FROM m_staff_org ps "
        + " INNER JOIN m_role_position pp on ps.serial_id = pp.position_id "
        + " INNER JOIN s_role pr on pr.id = pp.role_id and pr.is_del = " + DictConstant.DICT_SYS_DELETE_MAP_NO + " "
        + " INNER JOIN m_permission_role prp on pp.role_id = prp.role_id "
        + " INNER JOIN m_permission pm on pm.id = prp.permission_id and pm.is_del = " + DictConstant.DICT_SYS_DELETE_MAP_NO + " ";

    /**
     * 员工权限判定链的公共过滤条件：只取员工与岗位的关系
     */
    public static final String STAFF_PERMISSION_WHERE =
        " WHERE ps.serial_type = '" + DictConstant.DICT_SYS_CODE_TYPE_M_POSITION + "' ";
}
