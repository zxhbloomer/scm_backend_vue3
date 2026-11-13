package com.xinyirun.scm.core.system.serviceimpl.master.rbac.permission.user;

import com.xinyirun.scm.bean.system.bo.session.user.rbac.PermissionMenuBo;
import com.xinyirun.scm.bean.system.bo.session.user.rbac.PermissionOperationBo;
import com.xinyirun.scm.common.constant.SystemConstants;
import com.xinyirun.scm.core.system.mapper.master.rbac.permission.user.MUserPermissionRbacMapper;
import com.xinyirun.scm.core.system.service.master.rbac.permission.user.IMUserPermissionRbacCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户权限数据缓存
 *
 * 缓存 key 一律以数据源名开头，多租户之间不会互相覆盖：
 * 数据源名取当前线程的数据源，主库是 master，租户库是租户 id。
 * 有效期与其它缓存一致，由 scm.config.redis-cache-expired-min 统一控制（dev 环境 60 分钟）。
 */
@Service
@Slf4j
public class MUserPermissionRbacCacheServiceImpl implements IMUserPermissionRbacCacheService {

    @Autowired
    private MUserPermissionRbacMapper mapper;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 菜单路由数据
     */
    @Override
    @Cacheable(value = SystemConstants.CACHE_PC.CACHE_USER_PERMISSION,
            key = "T(com.xinyirun.scm.common.utils.datasource.DataSourceHelper).getCurrentDataSourceName() + '::routers::' + #staff_id + '::' + #menu_root_id")
    public List<PermissionMenuBo> getAllRouters(Long staff_id, Long menu_root_id) {
        return mapper.getAllRouters(staff_id, menu_root_id);
    }

    /**
     * 菜单节点 id
     */
    @Override
    @Cacheable(value = SystemConstants.CACHE_PC.CACHE_USER_PERMISSION,
            key = "T(com.xinyirun.scm.common.utils.datasource.DataSourceHelper).getCurrentDataSourceName() + '::nodes::' + #menu_root_id")
    public List<String> getAllNodesId(Long menu_root_id) {
        return mapper.getAllNodesId(menu_root_id);
    }

    /**
     * 按钮操作权限码
     */
    @Override
    @Cacheable(value = SystemConstants.CACHE_PC.CACHE_USER_PERMISSION,
            key = "T(com.xinyirun.scm.common.utils.datasource.DataSourceHelper).getCurrentDataSourceName() + '::operations::' + #staff_id")
    public List<PermissionOperationBo> getPermissionOperation(Long staff_id) {
        return mapper.getPermissionOperation(staff_id);
    }

    /**
     * 清空用户权限数据缓存
     */
    @Override
    public void clearUserPermissionCache() {
        Cache cache = cacheManager.getCache(SystemConstants.CACHE_PC.CACHE_USER_PERMISSION);
        if (cache != null) {
            cache.clear();
            log.info("清空用户权限数据缓存：{}", SystemConstants.CACHE_PC.CACHE_USER_PERMISSION);
        }
    }
}
