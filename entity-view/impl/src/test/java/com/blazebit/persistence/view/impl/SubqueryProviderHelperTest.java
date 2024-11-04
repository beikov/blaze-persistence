/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */
package com.blazebit.persistence.view.impl;

import com.blazebit.persistence.SubqueryInitiator;
import com.blazebit.persistence.view.MappingParameter;
import com.blazebit.persistence.view.SubqueryProviderFactory;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertTrue;

/**
 * @author Moritz Becker
 * @since 1.6.0
 */
public class SubqueryProviderHelperTest {

    @Test
    public void subqueryProviderValidMappingParameterConstructor() {
        SubqueryProviderFactory subqueryProviderFactory = SubqueryProviderHelper.getFactory(SubqueryProviderValidMappingParameterConstructor.class);
        assertTrue(subqueryProviderFactory instanceof ParameterizedSubqueryProviderFactory);
    }

    @Test
    public void subqueryProviderInvalidMappingParameterConstructor() {
        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
               SubqueryProviderHelper.getFactory(SubqueryProviderInvalidMappingParameterConstructor.class);
            }
        }).hasMessageStartingWith("Could not find any parameter mapping annotations on constructor parameter at index 0");
    }

    @Test
    public void subqueryProviderNoArgsAndArgsConstructor() {
        SubqueryProviderFactory subqueryProviderFactory = SubqueryProviderHelper.getFactory(SubqueryProviderNoArgsAndArgsConstructor.class);
        assertTrue(subqueryProviderFactory instanceof SimpleSubqueryProviderFactory);
    }

    @Test
    public void subqueryProviderNoArgsAndArgsAndMappingParameterConstructor() {
        SubqueryProviderFactory subqueryProviderFactory = SubqueryProviderHelper.getFactory(SubqueryProviderNoArgsAndArgsAndMappingParameterConstructor.class);
        assertTrue(subqueryProviderFactory instanceof ParameterizedSubqueryProviderFactory);
    }

    static class SubqueryProviderValidMappingParameterConstructor extends SubqueryProviderAdapter {
        public SubqueryProviderValidMappingParameterConstructor(@MappingParameter("test") int p1) { }
    }

    static class SubqueryProviderInvalidMappingParameterConstructor extends SubqueryProviderAdapter {
        public SubqueryProviderInvalidMappingParameterConstructor(int p1, @MappingParameter("test") int p2) { }
    }

    static class SubqueryProviderNoArgsAndArgsConstructor extends SubqueryProviderAdapter {
        public SubqueryProviderNoArgsAndArgsConstructor() { }
        public SubqueryProviderNoArgsAndArgsConstructor(int p1) { }
    }

    static class SubqueryProviderNoArgsAndArgsAndMappingParameterConstructor extends SubqueryProviderAdapter {
        public SubqueryProviderNoArgsAndArgsAndMappingParameterConstructor() { }
        public SubqueryProviderNoArgsAndArgsAndMappingParameterConstructor(int p1, int p2) { }
        public SubqueryProviderNoArgsAndArgsAndMappingParameterConstructor(@MappingParameter("test") int p1) { }
    }

    static abstract class SubqueryProviderAdapter implements com.blazebit.persistence.view.SubqueryProvider {

        @Override
        public <T> T createSubquery(SubqueryInitiator<T> subqueryInitiator) {
            return null;
        }
    }
}
