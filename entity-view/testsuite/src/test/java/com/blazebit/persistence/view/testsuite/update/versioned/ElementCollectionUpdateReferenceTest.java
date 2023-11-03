/*
 * Copyright 2014 - 2022 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.view.testsuite.update.versioned;

import com.blazebit.persistence.testsuite.base.jpa.assertion.AssertStatementBuilder;
import com.blazebit.persistence.testsuite.base.jpa.category.NoDatanucleus;
import com.blazebit.persistence.testsuite.base.jpa.category.NoEclipselink;
import com.blazebit.persistence.testsuite.entity.VersionedEntity;
import com.blazebit.persistence.view.FlushMode;
import com.blazebit.persistence.view.FlushStrategy;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import com.blazebit.persistence.view.testsuite.update.AbstractEntityViewUpdateTest;
import com.blazebit.persistence.view.testsuite.update.versioned.model.VersionedEntityView;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.persistence.EntityManager;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Christian Beikov
 * @since 1.6.8
 */
@RunWith(Parameterized.class)
// NOTE: No EclipseLink and Datanucleus support yet
@Category({ NoDatanucleus.class, NoEclipselink.class})
public class ElementCollectionUpdateReferenceTest extends AbstractEntityViewUpdateTest<VersionedEntityView> {

    private VersionedEntity entity1;

    @Override
    protected Class<?>[] getEntityClasses() {
        return new Class<?>[] {
            VersionedEntity.class
        };
    }

    @Override
    protected void registerViewTypes(EntityViewConfiguration cfg) {
        cfg.addEntityView(VersionedEntityView.class);
    }

    public ElementCollectionUpdateReferenceTest(FlushMode mode, FlushStrategy strategy, boolean version) {
        super(mode, strategy, version, VersionedEntityView.class);
    }

    @Parameterized.Parameters(name = "{0} - {1} - VERSIONED={2}")
    public static Object[][] combinations() {
        return MODE_STRATEGY_VERSION_COMBINATIONS;
    }

    @Override
    protected void prepareData(EntityManager em) {
        entity1 = new VersionedEntity("doc1");

        em.persist(entity1);
    }

    // Test for #1536
    @Test
    public void testUpdateName() {
        VersionedEntityView doc1View = evm.find(em, VersionedEntityView.class, entity1.getId());
        doc1View.setName("newName");

        update(doc1View);

        reload();
        // Doc1
        assertEquals("newName", entity1.getName());
    }

    @Override
    protected void reload() {
        entity1 = em.find(VersionedEntity.class, entity1.getId());
    }

    @Override
    protected AssertStatementBuilder fullFetch(AssertStatementBuilder builder) {
        return builder;
    }

    @Override
    protected AssertStatementBuilder versionUpdate(AssertStatementBuilder builder) {
        return builder;
    }
}
