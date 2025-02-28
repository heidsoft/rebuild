/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.web.admin.metadata;

import cn.devezhao.commons.web.ServletUtils;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.api.RespBody;
import com.rebuild.core.Application;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.MetadataSorter;
import com.rebuild.core.metadata.easymeta.EasyEntity;
import com.rebuild.core.metadata.easymeta.EasyMetaFactory;
import com.rebuild.core.metadata.impl.Entity2Schema;
import com.rebuild.core.metadata.impl.MetaEntityService;
import com.rebuild.core.privileges.UserHelper;
import com.rebuild.core.rbstore.MetaSchemaGenerator;
import com.rebuild.core.service.general.QuickCodeReindexTask;
import com.rebuild.core.support.RebuildConfiguration;
import com.rebuild.core.support.task.TaskExecutors;
import com.rebuild.utils.JSONUtils;
import com.rebuild.web.BaseController;
import com.rebuild.web.commons.FileDownloader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhaofang123@gmail.com
 * @since 08/03/2018
 */
@Slf4j
@RestController
@RequestMapping("/admin/")
public class MetaEntityController extends BaseController {

    @GetMapping("entities")
    public ModelAndView page(HttpServletRequest request) {
        ModelAndView mv = createModelAndView("/admin/metadata/entities");
        mv.getModel().put("isSuperAdmin", UserHelper.isSuperAdmin(getRequestUser(request)));
        return mv;
    }

    @GetMapping("entity/{entity}/base")
    public ModelAndView pageBase(@PathVariable String entity, HttpServletResponse response) throws IOException {
        Entity metaEntity = MetadataHelper.getEntity(entity);
        if (!(MetadataHelper.isBusinessEntity(metaEntity) || MetadataHelper.isBizzEntity(metaEntity))) {
            response.sendError(403);
            return null;
        }

        ModelAndView mv = createModelAndView("/admin/metadata/entity-edit");
        setEntityBase(mv, entity);

        mv.getModel().put("nameField", metaEntity.getNameField().getName());

        if (metaEntity.getMainEntity() != null) {
            mv.getModel().put("mainEntity", metaEntity.getMainEntity().getName());
            mv.getModel().put("detailEntity", metaEntity.getName());
        } else if (metaEntity.getDetailEntity() != null) {
            mv.getModel().put("mainEntity", metaEntity.getName());
            mv.getModel().put("detailEntity", metaEntity.getDetailEntity().getName());
        }

        // 扩展配置
        mv.getModel().put("entityExtConfig", EasyMetaFactory.valueOf(metaEntity).getExtraAttrs(true));

        return mv;
    }

    @GetMapping("entity/{entity}/advanced")
    public ModelAndView pageAdvanced(@PathVariable String entity, HttpServletRequest request) {
        ModelAndView mv = createModelAndView("/admin/metadata/entity-advanced");
        mv.getModel().put("isSuperAdmin", UserHelper.isSuperAdmin(getRequestUser(request)));
        setEntityBase(mv, entity);
        return mv;
    }

    @RequestMapping("entity/entity-list")
    public RespBody listEntity(HttpServletRequest request) {
        // 默认无BIZZ实体
        final boolean usesBizz = getBoolParameter(request, "bizz", false);
        // 默认无明细实体
        final boolean usesDetail = getBoolParameter(request, "detail", false);

        List<Map<String, Object>> data = new ArrayList<>();
        for (Entity entity : MetadataSorter.sortEntities(null, usesBizz, usesDetail)) {
            EasyEntity easyMeta = EasyMetaFactory.valueOf(entity);
            Map<String, Object> map = new HashMap<>();
            map.put("entityName", easyMeta.getName());
            map.put("entityLabel", easyMeta.getLabel());
            map.put("comments", easyMeta.getComments());
            map.put("icon", easyMeta.getIcon());
            map.put("builtin", easyMeta.isBuiltin());
            if (entity.getDetailEntity() != null) {
                map.put("detailEntity", entity.getDetailEntity().getName());
            }
            if (entity.getMainEntity() != null) {
                map.put("mainEntity", entity.getMainEntity().getName());
            }
            data.add(map);
        }
        return RespBody.ok(data);
    }

    @PostMapping("entity/entity-new")
    public RespBody entityNew(HttpServletRequest request) {
        final ID user = getRequestUser(request);
        final JSONObject reqJson = (JSONObject) ServletUtils.getRequestJson(request);

        String label = reqJson.getString("label");
        String comments = reqJson.getString("comments");
        String mainEntity = reqJson.getString("mainEntity");
        if (StringUtils.isNotBlank(mainEntity)) {
            if (!MetadataHelper.containsEntity(mainEntity)) {
                return RespBody.errorl("无效主实体 : %s", mainEntity);
            }

            Entity useMain = MetadataHelper.getEntity(mainEntity);
            if (useMain.getMainEntity() != null) {
                return RespBody.errorl("明细实体不能作为主实体");
            } else if (useMain.getDetailEntity() != null) {
                return RespBody.errorl("选择的主实体已被 [%s] 使用", useMain.getDetailEntity());
            }
        }

        try {
            String entityName = new Entity2Schema(user)
                    .createEntity(label, comments, mainEntity, getBoolParameter(request, "nameField"));
            return RespBody.ok(entityName);
        } catch (Exception ex) {
            log.error("entity-new", ex);
            return RespBody.error(ex.getLocalizedMessage());
        }
    }

    @PostMapping("entity/entity-update")
    public RespBody entityUpdate(HttpServletRequest request) {
        final ID user = getRequestUser(request);
        final JSON formJson = ServletUtils.getRequestJson(request);

        Record record = EntityHelper.parse((JSONObject) formJson, user);

        // 修改了名称字段
        String needReindex = null;
        String nameField = record.getString("nameField");
        if (nameField != null) {
            Object[] nameFieldOld = Application.createQueryNoFilter(
                    "select nameField,entityName from MetaEntity where entityId = ?")
                    .setParameter(1, record.getPrimary())
                    .unique();
            if (!nameField.equalsIgnoreCase((String) nameFieldOld[0])) {
                needReindex = (String) nameFieldOld[1];
            }
        }

        Application.getBean(MetaEntityService.class).update(record);

        if (needReindex != null) {
            Entity entity = MetadataHelper.getEntity(needReindex);
            if (entity.containsField(EntityHelper.QuickCode)) {
                QuickCodeReindexTask reindexTask = new QuickCodeReindexTask(entity);
                TaskExecutors.submit(reindexTask, user);
            }
        }

        return RespBody.ok();
    }

    @RequestMapping("entity/entity-drop")
    public RespBody entityDrop(HttpServletRequest request) {
        final ID user = getRequestUser(request);
        final Entity entity = getEntityById(getIdParameterNotNull(request, "id"));
        final boolean force = getBoolParameter(request, "force", false);

        try {
            boolean drop = new Entity2Schema(user).dropEntity(entity, force);
            return drop ? RespBody.ok() : RespBody.error();

        } catch (Exception ex) {
            log.error("entity-drop", ex);
            return RespBody.error(ex.getLocalizedMessage());
        }
    }

    @GetMapping("entity/entity-export")
    public void entityExport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final Entity entity = getEntityById(getIdParameterNotNull(request, "id"));

        File dest = RebuildConfiguration.getFileOfTemp("schema-" + entity.getName() + ".json");
        if (dest.exists()) FileUtils.deleteQuietly(dest);

        new MetaSchemaGenerator(entity).generate(dest);

        if (ServletUtils.isAjaxRequest(request)) {
            writeSuccess(response, JSONUtils.toJSONObject("file", dest.getName()));
        } else {
            FileDownloader.setDownloadHeaders(request, response, dest.getName());
            FileDownloader.writeLocalFile(dest.getName(), true, response);
        }
    }

    private Entity getEntityById(ID metaId) {
        Object[] entityRecord = Application.createQueryNoFilter(
                "select entityName from MetaEntity where entityId = ?")
                .setParameter(1, metaId)
                .unique();
        String entityName = (String) entityRecord[0];
        return MetadataHelper.getEntity(entityName);
    }

    /**
     * 设置实体信息
     *
     * @param mv
     * @param entity
     * @return
     */
    protected static EasyEntity setEntityBase(ModelAndView mv, String entity) {
        EasyEntity entityMeta = EasyMetaFactory.valueOf(entity);
        mv.getModel().put("entityMetaId", entityMeta.getMetaId());
        mv.getModel().put("entityName", entityMeta.getName());
        mv.getModel().put("entityLabel", entityMeta.getLabel());
        mv.getModel().put("icon", entityMeta.getIcon());
        mv.getModel().put("comments", entityMeta.getComments());
        mv.getModel().put("entityCode", entityMeta.getRawMeta().getEntityCode());
        return entityMeta;
    }
}
