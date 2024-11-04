/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.update.flatview.simple.mutablesubtype;

import com.blazebit.persistence.testsuite.base.jpa.assertion.AssertStatementBuilder;
import com.blazebit.persistence.testsuite.base.jpa.category.NoDatanucleus;
import com.blazebit.persistence.testsuite.base.jpa.category.NoEclipselink;
import com.blazebit.persistence.testsuite.entity.Document;
import com.blazebit.persistence.testsuite.entity.NameObject;
import com.blazebit.persistence.view.FlushMode;
import com.blazebit.persistence.view.FlushStrategy;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import com.blazebit.persistence.view.testsuite.update.AbstractEntityViewUpdateDocumentTest;
import com.blazebit.persistence.view.testsuite.update.flatview.simple.mutablesubtype.model.ReadonlyNameObjectView;
import com.blazebit.persistence.view.testsuite.update.flatview.simple.mutablesubtype.model.UpdatableDocumentWithMapsView;
import com.blazebit.persistence.view.testsuite.update.flatview.simple.mutablesubtype.model.UpdatableNameObjectView;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Map;

import static org.junit.Assert.*;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
@RunWith(Parameterized.class)
// NOTE: No Datanucleus support yet
@Category({ NoDatanucleus.class, NoEclipselink.class})
public class EntityViewUpdateSimpleMutableSubtypeFlatViewMapsTest extends AbstractEntityViewUpdateDocumentTest<UpdatableDocumentWithMapsView> {

    public EntityViewUpdateSimpleMutableSubtypeFlatViewMapsTest(FlushMode mode, FlushStrategy strategy, boolean version) {
        super(mode, strategy, version, UpdatableDocumentWithMapsView.class);
    }

    @Parameterized.Parameters(name = "{0} - {1} - VERSIONED={2}")
    public static Object[][] combinations() {
        return MODE_STRATEGY_VERSION_COMBINATIONS;
    }

    @Override
    protected void registerViewTypes(EntityViewConfiguration cfg) {
        cfg.addEntityView(UpdatableNameObjectView.class);
        cfg.addEntityView(ReadonlyNameObjectView.class);
    }

    @Override
    protected String[] getFetchedCollections() {
        return new String[] { "nameMap" };
    }

    @Test
    public void testUpdateCollectionElement() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        clearQueries();
        
        // When

        UpdatableNameObjectView updatableNameObjectView = evm.convert(docView.getNameMap().get("doc1"), UpdatableNameObjectView.class);
        updatableNameObjectView.setPrimaryName("newPers");
        docView.getNameMap().put("doc1", updatableNameObjectView);
        update(docView);

        // Then
        // Assert that the document and the people are loaded i.e. a full fetch
        // Finally the person is updated because the primary name changed
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
        } else {
            fullFetch(builder);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }
        if (supportsIndexedInplaceUpdate() && !isQueryStrategy() || isQueryStrategy() && !isFullMode()) {
            builder.assertUpdate()
                    .forRelation(Document.class, "nameMap")
                    .and();
        } else {
            builder.delete(Document.class, "nameMap")
                    .insert(Document.class, "nameMap");

        }
        builder.validate();

        assertNoUpdateAndReload(docView);
        assertEquals("newPers", doc1.getNameMap().get("doc1").getPrimaryName());
        assertSubviewEquals(doc1.getNameMap(), docView.getNameMap());
    }

    public static void assertSubviewEquals(Map<String, NameObject> persons, Map<String, ? extends ReadonlyNameObjectView> personSubviews) {
        if (persons == null) {
            assertNull(personSubviews);
            return;
        }

        assertNotNull(personSubviews);
        assertEquals(persons.size(), personSubviews.size());
        for (Map.Entry<String, NameObject> entry : persons.entrySet()) {
            NameObject p = entry.getValue();
            boolean found = false;
            ReadonlyNameObjectView pSub = personSubviews.get(entry.getKey());
            if (pSub != null) {
                if (p.getPrimaryName().equals(pSub.getPrimaryName())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                Assert.fail("Could not find a UpdatableNameObjectView with the name: " + p.getPrimaryName());
            }
        }
    }

    @Override
    protected AssertStatementBuilder fullFetch(AssertStatementBuilder builder) {
        return builder.assertSelect()
                .fetching(Document.class)
                .fetching(Document.class, "nameMap")
                .and();
    }

    @Override
    protected AssertStatementBuilder fullUpdate(AssertStatementBuilder builder) {
        builder.delete(Document.class, "nameMap")
                .insert(Document.class, "nameMap");
        builder.update(Document.class);
        return builder;
    }

    @Override
    protected AssertStatementBuilder versionUpdate(AssertStatementBuilder builder) {
        return builder.update(Document.class);
    }
}
