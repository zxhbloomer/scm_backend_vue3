package com.xinyirun.scm.core.system.service.master.rbac.permission.user;

import com.xinyirun.scm.bean.system.bo.session.user.rbac.PermissionMenuBo;
import com.xinyirun.scm.bean.system.bo.session.user.rbac.PermissionOperationBo;

import java.util.List;

/**
 * 用户权限数据缓存
 *
 * 单独抽出接口的原因：菜单权限的三块取数在原实现类里都是同类内部调用，
 * 缓存注解挂在原实现类上不会经过 Spring 代理，注解不生效。
 * 这里由外部 Bean 调用，缓存注解才会命中。
 */
public interface IMUserPermissionRbacCacheService {

    /**
     * 菜单路由数据
     *
     * @param staff_id 员工 id
     * @param menu_root_id 菜单根节点 id
     */
    List<PermissionMenuBo> getAllRouters(Long staff_id, Long menu_root_id);

    /**
     * 菜单节点 id，页面上展开全部菜单用
     *
     * @param menu_root_id 菜单根节点 id
     */
    List<String> getAllNodesId(Long menu_root_id);

    /**
     * 按钮操作权限码
     *
     * @param staff_id 员工 id
     */
    List<PermissionOperationBo> getPermissionOperation(Long staff_id);

    /**
     * 清空用户权限数据缓存
     *
     * 员工岗位、岗位角色、角色权限、权限页面与操作这几类关系发生变化时必须调用，
     * 否则菜单和按钮权限最长要等一个缓存周期（60 分钟）才更新。
     */
    void clearUserPermissionCache();
}
