/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.web.project;

import cn.devezhao.commons.ObjectUtils;
import cn.devezhao.commons.web.ServletUtils;
import cn.devezhao.persist4j.engine.ID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.api.RespBody;
import com.rebuild.core.Application;
import com.rebuild.core.configuration.ConfigBean;
import com.rebuild.core.configuration.ConfigurationException;
import com.rebuild.core.privileges.UserHelper;
import com.rebuild.core.service.project.ProjectHelper;
import com.rebuild.core.service.project.ProjectManager;
import com.rebuild.core.service.query.AdvFilterParser;
import com.rebuild.core.support.general.FieldValueHelper;
import com.rebuild.core.support.i18n.I18nUtils;
import com.rebuild.core.support.i18n.Language;
import com.rebuild.utils.JSONUtils;
import com.rebuild.web.BaseController;
import com.rebuild.web.IdParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Set;

/**
 * 任务
 *
 * @author devezhao
 * @since 2020/6/29
 */
@Slf4j
@RestController
@RequestMapping("/project/")
public class ProjectTaskController extends BaseController {

    private static final JSONObject NO_TASKS = JSONUtils.toJSONObject(
            new String[] { "count", "tasks" },
            new Object[] { 0, new Object[0] });

    @GetMapping("task/{taskId}")
    public ModelAndView pageTask(@PathVariable String taskId,
                                 HttpServletRequest request, HttpServletResponse response) throws IOException {
        final ID taskId2 = ID.isId(taskId) ? ID.valueOf(taskId) : null;
        if (taskId2 == null) {
            response.sendError(404);
            return null;
        }

        final ID user = getRequestUser(request);
        if (!ProjectHelper.checkReadable(taskId2, user)) {
            response.sendError(403, Language.L("你无权查看此任务"));
            return null;
        }

        ConfigBean project = ProjectManager.instance.getProjectByTask(taskId2, user);

        ModelAndView mv = createModelAndView("/project/task-view");
        mv.getModel().put("id", taskId2.toLiteral());
        mv.getModel().put("projectIcon", project.getString("iconName"));
        mv.getModel().put("isMember", project.get("members", Set.class).contains(user));
        return mv;
    }

    @RequestMapping("tasks/list")
    public JSON taskList(@IdParam(name = "plan") ID planId, HttpServletRequest request) {
        final ID user = getRequestUser(request);
        String queryWhere = "projectPlanId = ?";

        // 关键词搜索
        String search = getParameter(request, "search");
        if (StringUtils.isNotBlank(search)) {
            queryWhere += " and taskName like '%" + StringEscapeUtils.escapeSql(search) + "%'";
        }

        // 高级查询
        JSON advFilter = ServletUtils.getRequestJson(request);
        if (advFilter != null) {
            String filterSql = new AdvFilterParser((JSONObject) advFilter).toSqlWhere();
            if (filterSql != null) {
                queryWhere += " and (" + filterSql + ")";
            }
        }

        int pageNo = getIntParameter(request, "pageNo", 1);
        int pageSize = getIntParameter(request, "pageSize", 40);

        int count = -1;
        if (pageNo == 1) {
            String countSql = "select count(taskId) from ProjectTask where " + queryWhere;
            Object[] count2 = Application.createQueryNoFilter(countSql)
                    .setParameter(1, planId)
                    .unique();
            count = ObjectUtils.toInt(count2[0]);

            if (count == 0) {
                return NO_TASKS;
            }
        }

        queryWhere += " order by " +  buildQuerySort(request);
        String querySql = "select " + BASE_FIELDS + " from ProjectTask where " + queryWhere;

        Object[][] tasks = Application.createQueryNoFilter(querySql)
                .setParameter(1, planId)
                .setLimit(pageSize, pageNo * pageSize - pageSize)
                .array();

        JSONArray alist = new JSONArray();
        for (Object[] o : tasks) {
            JSONObject item = formatTask(o, user);
            item.remove("planName");
            alist.add(item);
        }

        return JSONUtils.toJSONObject(
                new String[] { "count", "tasks" },
                new Object[] { count, alist });
    }

    @GetMapping("tasks/get")
    public JSON taskGet(@IdParam(name = "task") ID taskId) {
        Object[] task = Application.createQueryNoFilter(
                "select " + BASE_FIELDS + " from ProjectTask where taskId = ?")
                .setParameter(1, taskId)
                .unique();
        return formatTask(task, null);
    }

    @GetMapping("tasks/details")
    public JSON taskDetails(@IdParam(name = "task") ID taskId, HttpServletRequest request) {
        final ID user = getRequestUser(request);

        Object[] task = Application.createQueryNoFilter(
                "select " + BASE_FIELDS + ",description,attachments,relatedRecord from ProjectTask where taskId = ?")
                .setParameter(1, taskId)
                .unique();
        JSONObject details = formatTask(task, user);

        details.put("description", task[12]);
        String attachments = (String) task[13];
        details.put("attachments", JSON.parseArray(attachments));

        // 相关记录
        ID relatedRecord = (ID) task[14];
        if (relatedRecord != null) {
            details.put("relatedRecord", relatedRecord);
            String text = FieldValueHelper.getLabelNotry(relatedRecord);
            details.put("relatedRecordData", FieldValueHelper.wrapMixValue(relatedRecord, text));
        }

        return details;
    }

    private static final String BASE_FIELDS =
            "projectId,projectPlanId,taskNumber,taskId,taskName,createdOn,deadline,executor,status,seq,priority,endTime";

    private JSONObject formatTask(Object[] o, ID user) {
        final ConfigBean project =  ProjectManager.instance.getProject((ID) o[0], user);

        String taskNumber = String.format("%s-%s", project.getString("projectCode"), o[2]);
        String createdOn = I18nUtils.formatDate((Date) o[5]);
        String deadline = I18nUtils.formatDate((Date) o[6]);
        String endTime = I18nUtils.formatDate((Date) o[11]);

        Object[] executor = o[7] == null ? null : new Object[]{o[7], UserHelper.getName((ID) o[7])};

        JSONObject data = JSONUtils.toJSONObject(
                new String[] { "id", "taskNumber", "taskName", "createdOn", "deadline", "executor", "status", "seq", "priority", "endTime", "projectId" },
                new Object[] { o[3], taskNumber, o[4], createdOn, deadline, executor, o[8], o[9], o[10], endTime, o[0] });
        // 标签
        data.put("tags", TaskTagController.getTaskTags((ID) o[3]));

        if (user != null) {
            // 项目信息
            ConfigBean plan =  ProjectManager.instance.getPlanOfProject((ID) o[1], (ID) o[0]);
            data.put("planName", String.format("%s (%s)",
                    project.getString("projectName"), plan.getString("planName")));
            data.put("planFlow", plan.getInteger("flowStatus"));
            // 权限
            data.put("projectMember", project.get("members", Set.class).contains(user));
            data.put("isManageable", ProjectHelper.isManageable((ID) o[3], user));
        }

        return data;
    }

    private String buildQuerySort(HttpServletRequest request) {
        String sort = getParameter(request, "sort");
        if ("deadline".equalsIgnoreCase(sort)) sort = "deadline desc";
        else if ("modifiedOn".equalsIgnoreCase(sort)) sort = "modifiedOn desc";
        else sort = "seq asc";
        return sort;
    }

    // for View of Entity
    @GetMapping("alist")
    public RespBody getProjectAndPlans(HttpServletRequest request) {
        final ID user = getRequestUser(request);

        ConfigBean[] ps = ProjectManager.instance.getAvailable(user, true);
        JSONArray alist = new JSONArray();

        for (ConfigBean p : ps) {
            JSONObject item = (JSONObject) p.toJSON("id", "projectName");

            // 面板
            ConfigBean[] plans = ProjectManager.instance.getPlansOfProject(p.getID("id"));
            JSONArray plansList = new JSONArray();
            for (ConfigBean plan : plans) {
                plansList.add(plan.toJSON("id", "planName", "flowStatus"));
            }
            item.put("plans", plansList);

            alist.add(item);
        }

        return RespBody.ok(alist);
    }

    @RequestMapping("tasks/related-list")
    public JSON relatedTaskList(@IdParam(name = "related", required = false) ID relatedId,
                                @IdParam(name = "task", required = false) ID taskId,
                                HttpServletRequest request) {
        Assert.isTrue(relatedId != null || taskId != null, Language.L("无效请求参数"));

        final ID user = getRequestUser(request);
        String queryWhere = String.format("relatedRecord = '%s'", relatedId);

        // 关键词搜索
        String search = getParameter(request, "search");
        if (StringUtils.isNotBlank(search)) {
            queryWhere += " and taskName like '%" + StringEscapeUtils.escapeSql(search) + "%'";
        }

        int pageNo = getIntParameter(request, "pageNo", 1);
        int pageSize = getIntParameter(request, "pageSize", 40);

        queryWhere += " order by " + buildQuerySort(request);

        // 获取指定任务的（其他条件忽略）
        if (taskId != null) {
            queryWhere = String.format("taskId = '%s'", taskId);
        }

        String querySql = "select " + BASE_FIELDS + " from ProjectTask where " + queryWhere;

        Object[][] tasks = Application.createQueryNoFilter(querySql)
                .setLimit(pageSize, pageNo * pageSize - pageSize)
                .array();

        JSONArray array = new JSONArray();
        for (Object[] o : tasks) {
            try {
                array.add(formatTask(o, user));
            } catch (ConfigurationException ex) {
                // FIXME 无项目权限会报错（考虑任务在相关项中是否无权限也显示）
                log.warn(ex.getLocalizedMessage());
            }
        }
        return array;
    }
}
