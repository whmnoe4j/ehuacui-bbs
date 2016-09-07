package org.ehuacui.bbs.controller;

import com.jfinal.kit.HttpKit;
import com.jfinal.kit.LogKit;
import com.jfinal.kit.PropKit;
import org.ehuacui.bbs.model.Role;
import org.ehuacui.bbs.model.User;
import org.ehuacui.bbs.common.BaseController;
import org.ehuacui.bbs.common.Constants;
import org.ehuacui.bbs.common.ServiceHolder;
import org.ehuacui.bbs.ext.route.ControllerBind;
import org.ehuacui.bbs.model.UserRole;
import org.ehuacui.bbs.utils.DateUtil;
import org.ehuacui.bbs.utils.StrUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehuacui.
 * Copyright (c) 2016, All Rights Reserved.
 * http://www.ehuacui.org
 */
@ControllerBind(controllerKey = "/oauth", viewPath = "WEB-INF/page")
public class OauthController extends BaseController {

    private static final String STATE = "thirdlogin_state";

    /**
     * github登录
     *
     * @throws UnsupportedEncodingException
     */
    public void githublogin() throws UnsupportedEncodingException {
        LogKit.info("githublogin");
        String state = StrUtil.randomString(6);
        setCookie(STATE, state, 5 * 60, "/", PropKit.get("cookie.domain"), true);
        StringBuffer sb = new StringBuffer();
        sb.append("https://github.com/login/oauth/authorize")
                .append("?")
                .append("client_id")
                .append("=")
                .append(PropKit.get("github.client_id"))
                .append("&")
                .append("state")
                .append("=")
                .append(state)
                .append("&")
                .append("scope")
                .append("=")
                .append("user");
        redirect(sb.toString());
    }

    /**
     * github登录成功后回调
     *
     * @throws UnsupportedEncodingException
     */
    public void githubcallback() throws UnsupportedEncodingException {
        String code = getPara("code");
        String state = getPara("state");
        String cookieState = getCookie(STATE);
        if (state.equalsIgnoreCase(cookieState)) {
//            请求access_token
            Map<String, String> map1 = new HashMap<String, String>();
            map1.put("client_id", PropKit.get("github.client_id"));
            map1.put("client_secret", PropKit.get("github.client_secret"));
            map1.put("code", code);
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Accept", "application/json");
            String resp1 = HttpKit.post("https://github.com/login/oauth/access_token", map1, "", headers);
            Map respMap1 = StrUtil.parseToMap(resp1);
            //access_token, scope, token_type
            String github_access_token = (String) respMap1.get("access_token");
            //获取用户信息
            Map<String, String> map2 = new HashMap<String, String>();
            map2.put("access_token", github_access_token);
            String resp2 = HttpKit.get("https://api.github.com/user", map2);
            Map respMap2 = StrUtil.parseToMap(resp2);
            Double githubId = (Double) respMap2.get("id");
            String login = (String) respMap2.get("login");
            String avatar_url = (String) respMap2.get("avatar_url");
            String email = (String) respMap2.get("email");
            String html_url = (String) respMap2.get("html_url");

            Date now = new Date();
            String access_token = StrUtil.getUUID();
            User user = ServiceHolder.userService.findByThirdId(String.valueOf(githubId));
            boolean flag = true;
            if (user == null) {
                user = new User();
                user.setInTime(now);
                user.setAccessToken(access_token);
                user.setScore(0);
                user.setThirdId(String.valueOf(githubId));
                user.setIsBlock(false);
                user.setChannel(Constants.LoginEnum.Github.name());
                user.setReceiveMsg(true);//邮箱接收社区消息
                flag = false;
            }
            user.setNickname(login);
            user.setAvatar(avatar_url);
            user.setEmail(email);
            user.setUrl(html_url);
            user.setExpireTime(DateUtil.getDateAfter(now, 30));//30天后过期,要重新认证
            if (flag) {
                ServiceHolder.userService.update(user);
            } else {
                ServiceHolder.userService.save(user);
                //新注册的用户角色都是普通用户
                Role role = ServiceHolder.roleService.findByName("user");
                if (role != null) {
                    UserRole userRole = new UserRole();
                    userRole.setUid(user.getId());
                    userRole.setRid(role.getId());
                    ServiceHolder.userRoleService.save(userRole);
                }
            }
            setCookie(Constants.USER_ACCESS_TOKEN,
                    StrUtil.getEncryptionToken(user.getAccessToken()),
                    30 * 24 * 60 * 60, "/",
                    PropKit.get("cookie.domain"),
                    true);
            String callback = getPara("callback");
            if (StrUtil.notBlank(callback)) {
                callback = URLDecoder.decode(callback, "UTF-8");
            }
            redirect(StrUtil.notBlank(callback) ? callback : "/");
        } else {
            renderText(Constants.OP_ERROR_MESSAGE);
        }
    }

    /**
     * 微博登录
     */
    public void weibologin() {
        LogKit.info("weibologin");
        String state = StrUtil.randomString(6);
        setCookie(STATE, state, 5 * 60, "/", PropKit.get("cookie.domain"), true);
        StringBuffer sb = new StringBuffer();
        sb.append("https://api.weibo.com/oauth2/authorize")
                .append("?")
                .append("client_id")
                .append("=")
                .append(PropKit.get("weibo.appkey"))
                .append("&")
                .append("state")
                .append("=")
                .append(state)
                .append("&")
                .append("scope")
                .append("=")
                .append("email")
                .append("&")
                .append("redirect_uri")
                .append("=")
                .append(PropKit.get("weibo.redirecturi"));
        redirect(sb.toString());
    }

    /**
     * 微博登录成功回调
     *
     * @throws UnsupportedEncodingException
     */
    public void weibocallback() throws UnsupportedEncodingException {
        String code = getPara("code");
        String state = getPara("state");
        String cookieState = getCookie(STATE);
        if (state.equalsIgnoreCase(cookieState)) {
//            请求access_token
            Map<String, String> map1 = new HashMap<String, String>();
            map1.put("client_id", PropKit.get("weibo.appkey"));
            map1.put("client_secret", PropKit.get("weibo.appsecret"));
            map1.put("grant_type", "authorization_code");
            map1.put("code", code);
            map1.put("redirect_uri", PropKit.get("weibo.redirecturi"));
            String resp1 = HttpKit.post("https://api.weibo.com/oauth2/access_token", map1, "");
            Map respMap1 = StrUtil.parseToMap(resp1);
            //access_token, expires_in, remind_in, uid
            String weibo_access_token = (String) respMap1.get("access_token");
//            Double expires_in = (Double) respMap1.get("expires_in");
//            Date expires_date = new Date(expires_in.longValue());//获取的逾期时间一直是1970年,暂没找到原因
            //获取用户信息
            Map<String, String> map2 = new HashMap<String, String>();
            map2.put("access_token", weibo_access_token);
            map2.put("uid", (String) respMap1.get("uid"));
            String resp2 = HttpKit.get("https://api.weibo.com/2/users/show.json", map2);
            Map respMap2 = StrUtil.parseToMap(resp2);
            System.out.println(respMap2);
            Double weiboId = (Double) respMap2.get("id");
            String name = (String) respMap2.get("name");
            String avatar_large = (String) respMap2.get("avatar_large");
            String description = (String) respMap2.get("description");
            String url = "http://weibo.com/" + respMap2.get("domain");

            Date now = new Date();
            String access_token = StrUtil.getUUID();
            User user = ServiceHolder.userService.findByThirdId(String.valueOf(weiboId));
            boolean flag = true;
            if (user == null) {
                user = new User();
                user.setInTime(now);
                user.setAccessToken(access_token);
                user.setScore(0);
                user.setThirdId(String.valueOf(weiboId));
                user.setIsBlock(false);
                user.setSignature(description);
                user.setChannel(Constants.LoginEnum.Weibo.name());
                user.setReceiveMsg(true);//邮箱接收社区消息
                flag = false;
            }
            user.setNickname(name);
            user.setAvatar(avatar_large);
            user.setThirdAccessToken(weibo_access_token);
            user.setExpireTime(DateUtil.getDateAfter(now, 30));//30天后过期,要重新认证
            if (flag) {
                ServiceHolder.userService.update(user);
            } else {
                ServiceHolder.userService.save(user);
                //新注册的用户角色都是普通用户
                Role role = ServiceHolder.roleService.findByName("user");
                if (role != null) {
                    UserRole userRole = new UserRole();
                    userRole.setUid(user.getId());
                    userRole.setRid(role.getId());
                    ServiceHolder.userRoleService.save(userRole);
                }
            }
            setCookie(Constants.USER_ACCESS_TOKEN,
                    StrUtil.getEncryptionToken(user.getAccessToken()),
                    30 * 24 * 60 * 60, "/",
                    PropKit.get("cookie.domain"),
                    true);
            String callback = getPara("callback");
            if (StrUtil.notBlank(callback)) {
                callback = URLDecoder.decode(callback, "UTF-8");
            }
            redirect(StrUtil.notBlank(callback) ? callback : "/");
        } else {
            renderText(Constants.OP_ERROR_MESSAGE);
        }
    }

}