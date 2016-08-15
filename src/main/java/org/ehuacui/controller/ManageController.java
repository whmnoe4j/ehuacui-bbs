package org.ehuacui.controller;

import org.ehuacui.common.BaseController;
import org.ehuacui.common.Constants;
import org.ehuacui.common.Constants.CacheEnum;
import org.ehuacui.common.ServiceHolder;
import org.ehuacui.interceptor.PermissionInterceptor;
import org.ehuacui.interceptor.UserInterceptor;
import org.ehuacui.module.Permission;
import org.ehuacui.module.Role;
import org.ehuacui.module.RolePermission;
import org.ehuacui.module.UserRole;
import org.ehuacui.module.User;
import org.ehuacui.ext.route.ControllerBind;
import com.jfinal.aop.Before;
import com.jfinal.kit.PropKit;
import com.jfinal.plugin.activerecord.tx.Tx;

import java.util.List;

/**
 * Created by ehuacui.
 * Copyright (c) 2016, All Rights Reserved.
 * http://www.ehuacui.org
 */
@Before({
        UserInterceptor.class,
        PermissionInterceptor.class
})
@ControllerBind(controllerKey = "/manage", viewPath = "WEB-INF/page")
public class ManageController extends BaseController {

    /**
     * 用户列表
     */
    public void users() {
        setAttr("page", ServiceHolder.userService.page(getParaToInt("p", 1), PropKit.getInt("pageSize")));
        render("system/users.ftl");
    }

    /**
     * 删除用户
     */
    @Before({
            UserInterceptor.class,
            PermissionInterceptor.class
    })
    public void deleteuser() {
        Integer id = getParaToInt("id");
        //删除与用户关联的角色
        ServiceHolder.userRoleService.deleteByUserId(id);
        //删除用户
        ServiceHolder.userService.deleteById(id);
        redirect("/manage/users");
    }

    /**
     * 角色列表
     */
    public void roles() {
        setAttr("roles", ServiceHolder.roleService.findAll());
        render("system/roles.ftl");
    }

    /**
     * 权限列表
     */
    public void permissions() {
        Integer pid = getParaToInt("pid");
        if(pid == null) {
            setAttr("permissions", ServiceHolder.permissionService.findByPid(0));
            setAttr("childPermissions", ServiceHolder.permissionService.findAll());
        } else {
            setAttr("permissions", ServiceHolder.permissionService.findByPid(0));
            setAttr("childPermissions", ServiceHolder.permissionService.findByPid(pid));
            setAttr("pid", pid);
        }
        render("system/permissions.ftl");
    }

    /**
     * 处理用户与角色关联
     */
    public void userrole() {
        Integer id = getParaToInt("id");
        String method = getRequest().getMethod();
        if(method.equals("GET")) {
            setAttr("user", ServiceHolder.userService.findById(id));
            //查询所有的权限
            setAttr("roles", ServiceHolder.roleService.findAll());
            //当前用户已经存在的角色
            setAttr("_roles", ServiceHolder.userRoleService.findByUserId(id));
            render("system/userrole.ftl");
        } else if(method.equals("POST")) {
            Integer[] roles = getParaValuesToInt("roles");
            ServiceHolder.userService.correlationRole(id, roles);
            //清除缓存
            clearCache(CacheEnum.userpermissions.name() + id);
            redirect("/manage/users");
        }
    }

    /**
     * 禁用账户
     */
    public void userblock() {
        Integer id = getParaToInt("id");
        User user = ServiceHolder.userService.findById(id);
        user.set("is_block", !user.getBoolean("is_block")).update();
        clearCache(CacheEnum.usernickname.name() + user.getStr("nickname"));
        clearCache(CacheEnum.useraccesstoken.name() + user.getStr("access_token"));
        redirect("/manage/users");
    }

    /**
     * 添加角色
     */
    public void addrole() {
        String method = getRequest().getMethod();
        if(method.equals("GET")) {
            //查询所有的权限
            setAttr("permissions", ServiceHolder.permissionService.findWithChild());
            render("system/addrole.ftl");
        } else if(method.equals("POST")) {
            String name = getPara("name");
            String description = getPara("description");
            Role role = new Role();
            role.set("name", name)
                    .set("description", description)
                    .save();
            //保存关联数据
            Integer[] roles = getParaValuesToInt("roles");
            ServiceHolder.roleService.correlationPermission(role.getInt("id"), roles);
            redirect("/manage/roles");
        }
    }

    /**
     * 添加权限
     */
    public void addpermission() {
        Integer pid = getParaToInt("pid");
        String method = getRequest().getMethod();
        if(method.equals("GET")) {
            setAttr("pid", pid);
            setAttr("permissions", ServiceHolder.permissionService.findByPid(0));
            render("system/addpermission.ftl");
        } else if(method.equals("POST")) {
            String name = getPara("name");
            String url = getPara("url");
            String description = getPara("description");
            Permission permission = new Permission();
            permission.set("name", name)
                    .set("url", pid == 0 ? "" : url)
                    .set("description", description)
                    .set("pid", pid)
                    .save();
            String _url = "/manage/permissions?pid=" + pid;
            if(pid == 0) {
                _url = "/manage/permissions";
            }
            redirect(_url);
        }
    }

    /**
     * 编辑权限
     */
    public void editpermission() {
        Integer id = getParaToInt("id");
        String method = getRequest().getMethod();
        if(method.equals("GET")) {
            setAttr("_permission", ServiceHolder.permissionService.findById(id));
            setAttr("permissions", ServiceHolder.permissionService.findByPid(0));
            render("system/editpermission.ftl");
        } else if(method.equals("POST")) {
            Integer pid = getParaToInt("pid");
            String name = getPara("name");
            String url = getPara("url");
            String description = getPara("description");
            Permission permission = ServiceHolder.permissionService.findById(id);
            permission.set("name", name)
                    .set("url", url)
                    .set("description", description)
                    .set("pid", pid)
                    .update();
            //清除缓存
            List<User> userpermissions = ServiceHolder.userService.findByPermissionId(id);
            for(User u: userpermissions) {
                clearCache(CacheEnum.userpermissions.name() + u.getInt("id"));
            }
            redirect("/manage/permissions?pid=" + pid);
        }
    }

    /**
     * 处理角色与权限关系
     */
    public void rolepermission() {
        Integer roleId = getParaToInt("id");
        String method = getRequest().getMethod();
        Role role = ServiceHolder.roleService.findById(roleId);
        if(method.equals("GET")) {
            setAttr("role", role);
            //查询所有的权限
            setAttr("permissions", ServiceHolder.permissionService.findWithChild());
            //查询角色已经配置的权限
            setAttr("_permissions", ServiceHolder.rolePermissionService.findByRoleId(roleId));
            render("system/rolepermission.ftl");
        } else if(method.equals("POST")) {
            String name = getPara("name");
            String description = getPara("description");
            role.set("name", name)
                    .set("description", description)
                    .update();
            Integer[] permissions = getParaValuesToInt("permissions");
            ServiceHolder.roleService.correlationPermission(roleId, permissions);
            //清除缓存
            List<UserRole> userRoles = ServiceHolder.userRoleService.findByRoleId(roleId);
            for(UserRole ur: userRoles) {
                clearCache(CacheEnum.userpermissions.name() + ur.getInt("uid"));
            }
            redirect("/manage/roles");
        }
    }

    /**
     * 删除角色
     */
    @Before(Tx.class)
    public void deleterole() {
        Integer id = getParaToInt("id");
        if(id == null) {
            renderText(Constants.OP_ERROR_MESSAGE);
        } else {
            ServiceHolder.userRoleService.deleteByRoleId(id);
            ServiceHolder.rolePermissionService.deleteByRoleId(id);
            ServiceHolder.roleService.deleteById(id);
            //清除缓存
            List<UserRole> userRoles = ServiceHolder.userRoleService.findByRoleId(id);
            for(UserRole ur: userRoles) {
                clearCache(CacheEnum.userpermissions.name() + ur.getInt("uid"));
            }
            redirect("/manage/roles");
        }
    }

    /**
     * 删除权限
     */
    @Before(Tx.class)
    public void deletepermission() {
        Integer id = getParaToInt("id");
        if(id == null) {
            renderText(Constants.OP_ERROR_MESSAGE);
        } else {
            Permission permission = ServiceHolder.permissionService.findById(id);
            Integer pid = permission.getInt("pid");
            String url = "/manage/permissions?pid=" + pid;
            //如果是父节点，就删除父节点下的所有权限
            if(pid == 0) {
                ServiceHolder.permissionService.deleteByPid(id);
                url = "/manage/permissions";
            }
            //删除与角色关联的数据
            ServiceHolder.rolePermissionService.deleteByPermissionId(id);
            ServiceHolder.permissionService.deleteById(id);
            //清除缓存
            List<User> userpermissions = ServiceHolder.userService.findByPermissionId(id);
            for(User u: userpermissions) {
                clearCache(CacheEnum.userpermissions.name() + u.getInt("id"));
            }
            redirect(url);
        }
    }
}
