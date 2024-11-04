/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.deltaspike.data.base.builder.part;

import com.blazebit.persistence.CriteriaBuilder;

import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;

import static org.apache.deltaspike.core.util.StringUtils.isNotEmpty;
import static org.apache.deltaspike.data.impl.util.QueryUtils.uncapitalize;

/**
 * @author Moritz Becker
 * @since 1.2.0
 */
public class OrderByQueryAttribute {
    static final String KEYWORD_ASC = "Asc";
    static final String KEYWORD_DESC = "Desc";

    private final String attribute;
    private final Direction direction;

    OrderByQueryAttribute(String attribute, Direction direction) {
        this.attribute = attribute;
        this.direction = direction;
    }

    public void buildQuery(CriteriaBuilder<?> cb) {
        switch (direction) {
            case DESC:
                cb.orderByDesc(EntityViewBasePropertyQueryPart.rewriteSeparator(attribute));
                break;
            case DEFAULT:
            case ASC:
            default:
                cb.orderByAsc(EntityViewBasePropertyQueryPart.rewriteSeparator(attribute));
                break;
        }
    }

    public Order buildOrder(Root<?> root, javax.persistence.criteria.CriteriaBuilder cb) {
        switch (direction) {
            case DESC:
                return cb.desc(root.get(EntityViewBasePropertyQueryPart.rewriteSeparator(attribute)));
            case DEFAULT:
            case ASC:
            default:
                return cb.asc(root.get(EntityViewBasePropertyQueryPart.rewriteSeparator(attribute)));
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.2.0
     */
    public enum Direction {
        ASC(KEYWORD_ASC),
        DESC(KEYWORD_DESC),
        DEFAULT("");

        private final String postfix;

        Direction(String postfix) {
            this.postfix = postfix;
        }

        public boolean endsWith(String queryPart) {
            return isNotEmpty(postfix) ? queryPart.endsWith(postfix) : false;
        }

        public String attribute(String queryPart) {
            String attribute = isNotEmpty(postfix) ?
                    queryPart.substring(0, queryPart.indexOf(postfix)) :
                    queryPart;
            return uncapitalize(attribute);
        }

        public String queryDirection() {
            return isNotEmpty(postfix) ? " " + postfix.toLowerCase() : "";
        }

        public static Direction fromQueryPart(String queryPart) {
            for (Direction dir : values()) {
                if (dir.endsWith(queryPart)) {
                    return dir;
                }
            }
            return DEFAULT;
        }
    }
}
