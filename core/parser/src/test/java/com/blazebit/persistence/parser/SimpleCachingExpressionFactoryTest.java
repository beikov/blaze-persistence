/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.parser;

import com.blazebit.persistence.parser.expression.ArrayExpression;
import com.blazebit.persistence.parser.expression.Expression;
import com.blazebit.persistence.parser.expression.ExpressionFactory;
import com.blazebit.persistence.parser.expression.ExpressionFactoryImpl;
import com.blazebit.persistence.parser.expression.MacroConfiguration;
import com.blazebit.persistence.parser.expression.MacroFunction;
import com.blazebit.persistence.parser.expression.PathElementExpression;
import com.blazebit.persistence.parser.expression.PathExpression;
import com.blazebit.persistence.parser.expression.PropertyExpression;
import com.blazebit.persistence.parser.expression.SimpleCachingExpressionFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Moritz Becker
 * @since 1.0.0
 */
public class SimpleCachingExpressionFactoryTest {

    @Test
    public void testCreateSimpleExpressionCache() {
        ExpressionFactory ef = new SimpleCachingExpressionFactory(new ExpressionFactoryImpl(new HashMap<String, FunctionKind>(), true, true));
        String expressionString = "SIZE(Hello.world[:hahaha].criteria[1].api.lsls[a.b.c.d.e]) + SIZE(Hello.world[:hahaha].criteria[1].api.lsls[a.b.c.d.e])";
        
        Expression expr1 = ef.createSimpleExpression(expressionString, false, true, false, null, null);
        Expression expr2 = ef.createSimpleExpression(expressionString, false, true, false, null, null);
        
        Assert.assertFalse(expr1 == expr2);
        Assert.assertEquals(expr1, expr2);
    }

    @Test
    public void testCreateSimpleExpressionCacheWithMacros() {
        ExpressionFactory ef = new SimpleCachingExpressionFactory(new ExpressionFactoryImpl(new HashMap<String, FunctionKind>(), true, true));
        MacroConfiguration macroConfiguration = MacroConfiguration.of(Collections.singletonMap("my_macro", (MacroFunction) new MacroFunction() {
            @Override
            public Expression apply(List<Expression> expressions) {
                PathExpression p;
                if (expressions.get(0) instanceof PathExpression) {
                    p = (PathExpression) expressions.get(0);
                } else {
                    p = new PathExpression(new ArrayList<>(Arrays.asList((PathElementExpression) expressions.get(0))));
                }
                p.getExpressions().add(new ArrayExpression(new PropertyExpression("lsls"), new PathExpression(Arrays.<PathElementExpression>asList(
                        new PropertyExpression("a"),
                        new PropertyExpression("b"),
                        new PropertyExpression("c"),
                        new PropertyExpression("d"),
                        new PropertyExpression("e")
                ))));
                return p;
            }

            @Override
            public Object[] getState() {
                return new Object[0];
            }

            @Override
            public boolean supportsCaching() {
                return true;
            }

            @Override
            public int hashCode() {
                return getClass().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return obj.getClass() == getClass();
            }
        }));
        String expressionString = "SIZE(my_macro(Hello.world[:hahaha].criteria[1].api)) + SIZE(my_macro(Hello.world[:hahaha].criteria[1].api))";

        Expression expr1 = ef.createSimpleExpression(expressionString, false, true, false, macroConfiguration, null);
        Expression expr2 = ef.createSimpleExpression(expressionString, false, true, false, macroConfiguration, null);

        Assert.assertFalse(expr1 == expr2);
        Assert.assertEquals(expr1, expr2);
    }
}
