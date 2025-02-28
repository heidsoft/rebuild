/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.metadata.easymeta;

import cn.devezhao.commons.RegexUtils;
import cn.devezhao.persist4j.Field;

import java.util.regex.Pattern;

/**
 * @author devezhao
 * @since 2020/11/17
 */
public class EasyPhone extends EasyText {
    private static final long serialVersionUID = 347745040979570855L;

    protected EasyPhone(Field field, DisplayType displayType) {
        super(field, displayType);
    }

    @Override
    public Pattern getPattern() {
        Pattern patt = super.getPattern();
        return patt == null ? RegexUtils.TEL_PATTERN : patt;
    }
}
