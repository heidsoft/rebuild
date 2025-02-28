/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.service.dataimport;

import cn.devezhao.persist4j.Field;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.RebuildException;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.easymeta.DisplayType;
import com.rebuild.core.metadata.easymeta.EasyField;
import com.rebuild.core.metadata.easymeta.EasyMetaFactory;
import com.rebuild.core.metadata.easymeta.MixValue;
import com.rebuild.core.support.RebuildConfiguration;
import com.rebuild.core.support.SetUser;
import com.rebuild.core.support.general.DataListBuilderImpl;
import com.rebuild.core.support.general.FieldValueHelper;
import com.rebuild.core.support.i18n.Language;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据导出
 *
 * @author ZHAO
 * @see DataListBuilderImpl
 * @since 2019/11/18
 */
public class DataExporter extends SetUser {

    /**
     * 最大行数
     */
    public static final int MAX_ROWS = 65535 - 1;

    final private JSONObject queryData;
    // 字段
    private List<Field> headFields = new ArrayList<>();

    /**
     * @param queryData
     */
    public DataExporter(JSONObject queryData) {
        this.queryData = queryData;
    }

    /**
     * 导出
     *
     * @return
     */
    public File export() {
        File tmp = RebuildConfiguration.getFileOfTemp(
                String.format("EXPORT-%d.csv", System.currentTimeMillis()));
        export(tmp);
        return tmp;
    }

    /**
     * 导出到指定文件
     *
     * @param dest
     */
    public void export(File dest) {
        DataListBuilderImpl control = new DataListBuilderImpl(queryData, getUser());

        List<String> head = this.buildHead(control);

        try (FileOutputStream fos = new FileOutputStream(dest, true)) {
            try (OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                try (BufferedWriter writer = new BufferedWriter(osw)) {
                    writer.write("\ufeff");
                    writer.write(mergeLine(head));

                    for (List<String> row : this.buildData(control)) {
                        writer.newLine();
                        writer.write(mergeLine(row));
                    }

                    writer.flush();
                }
            }
        } catch (IOException e) {
            throw new RebuildException("Cannot write .csv file", e);
        }
    }

    private String mergeLine(List<String> line) {
        StringBuilder sb = new StringBuilder();
        boolean b = true;
        for (String s : line) {
            if (b) b = false;
            else sb.append(", ");

            if (s.contains(",")) {
                s = s.replace(",", "，");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * 表头
     *
     * @param control
     * @return
     */
    protected List<String> buildHead(DataListBuilderImpl control) {
        List<String> headList = new ArrayList<>();
        for (String field : control.getQueryParser().getQueryFields()) {
            headFields.add(MetadataHelper.getLastJoinField(control.getEntity(), field));
            String fieldLabel = EasyMetaFactory.getLabel(control.getEntity(), field);
            headList.add(fieldLabel);
        }
        return headList;
    }

    /**
     * 数据
     *
     * @param control
     * @return
     */
    protected List<List<String>> buildData(DataListBuilderImpl control) {
        JSONArray data = ((JSONObject) control.getJSONResult()).getJSONArray("data");

        List<List<String>> into = new ArrayList<>();
        for (Object row : data) {
            JSONArray rowJson = (JSONArray) row;

            int cellIndex = 0;
            List<String> cellVals = new ArrayList<>();
            for (Object cellVal : rowJson) {
                // 最后添加的记录 ID
                // 详情可见 QueryParser#doParseIfNeed (L171)
                if (cellIndex >= headFields.size()) {
                    break;
                }

                Field field = headFields.get(cellIndex++);
                EasyField easyField = EasyMetaFactory.valueOf(field);
                DisplayType dt = easyField.getDisplayType();

                if (cellVal == null) {
                    cellVal = StringUtils.EMPTY;
                }

                if (cellVal.toString().equals(FieldValueHelper.NO_READ_PRIVILEGES)) {
                    cellVal = Language.L("[无权限]");

                } else if (dt == DisplayType.FILE
                        || dt == DisplayType.IMAGE
                        || dt == DisplayType.AVATAR
                        || dt == DisplayType.BARCODE) {
                    cellVal = Language.L("[暂不支持]");

                } else if (dt == DisplayType.DECIMAL || dt == DisplayType.NUMBER) {
                    cellVal = cellVal.toString().replace(",", "");  // 移除千分位

                } else if (dt == DisplayType.ID) {
                    cellVal = ((JSONObject) cellVal).getString("id");

                }

                if (easyField instanceof MixValue &&
                        (cellVal instanceof JSONObject || cellVal instanceof JSONArray)) {
                    cellVal = ((MixValue) easyField).unpackWrapValue(cellVal);
                }

                cellVals.add(cellVal.toString());
            }
            into.add(cellVals);
        }
        return into;
    }
}
