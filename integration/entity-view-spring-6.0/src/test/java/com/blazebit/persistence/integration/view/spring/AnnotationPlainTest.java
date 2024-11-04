/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */
package com.blazebit.persistence.integration.view.spring;

import com.blazebit.persistence.integration.view.spring.views.sub1.TestView1;
import com.blazebit.persistence.integration.view.spring.views.sub2.TestView2;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import jakarta.inject.Inject;
import java.util.Set;

/**
 * @author Moritz Becker
 * @since 1.2.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AnnotationPlainTest.TestConfig.class)
public class AnnotationPlainTest {

    @Inject
    private EntityViewConfiguration entityViewConfiguration;

    @Test
    public void testInjection() {
        Set<Class<?>> entityViews = entityViewConfiguration.getEntityViews();
        Assert.assertEquals(2, entityViews.size());
        Assert.assertTrue(entityViewConfiguration.getEntityViews().contains(TestView1.class));
        Assert.assertTrue(entityViewConfiguration.getEntityViews().contains(TestView2.class));
    }

    @Configuration
    @EnableEntityViews
    static class TestConfig {
    }
}
