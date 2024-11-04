/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.update.correlated.immutable;

import com.blazebit.persistence.testsuite.base.jpa.assertion.AssertStatementBuilder;
import com.blazebit.persistence.testsuite.base.jpa.category.NoDatanucleus;
import com.blazebit.persistence.testsuite.base.jpa.category.NoEclipselink;
import com.blazebit.persistence.testsuite.entity.Document;
import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.view.FlushMode;
import com.blazebit.persistence.view.FlushStrategy;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import com.blazebit.persistence.view.testsuite.update.AbstractEntityViewUpdateDocumentTest;
import com.blazebit.persistence.view.testsuite.update.correlated.immutable.model.UpdatablePersonView;
import com.blazebit.persistence.view.testsuite.update.correlated.immutable.model.UpdatableDocumentWithCollectionsView;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 *
 * @author Christian Beikov
 * @since 1.3.0
 */
@RunWith(Parameterized.class)
// NOTE: No Datanucleus support yet
@Category({ NoDatanucleus.class, NoEclipselink.class})
public class EntityViewUpdateCorrelatedImmutableSubviewCollectionsTest extends AbstractEntityViewUpdateDocumentTest<UpdatableDocumentWithCollectionsView> {

    public EntityViewUpdateCorrelatedImmutableSubviewCollectionsTest(FlushMode mode, FlushStrategy strategy, boolean version) {
        super(mode, strategy, version, UpdatableDocumentWithCollectionsView.class);
    }

    @Parameterized.Parameters(name = "{0} - {1} - VERSIONED={2}")
    public static Object[][] combinations() {
        return MODE_STRATEGY_VERSION_COMBINATIONS;
    }

    @Override
    protected void registerViewTypes(EntityViewConfiguration cfg) {
        cfg.addEntityView(UpdatablePersonView.class);
    }

    @Override
    protected String[] getFetchedCollections() {
        return new String[] { "partners" };
    }

    @Test
    public void testUpdateReplaceCollection() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        clearQueries();
        
        // When
        docView.setPartners(new ArrayList<>(docView.getPartners()));
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isFullMode()) {
            if (isQueryStrategy()) {
                builder.update(Document.class);
            } else {
                fullFetch(builder);
            }
        }

        builder.validate();

        assertNoUpdateAndReload(docView);
        assertSubviewEquals(doc1.getPartners(), docView.getPartners());
    }

    @Test
    public void testUpdateAddToCollection() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatablePersonView newPerson = getP2View(UpdatablePersonView.class);
        clearQueries();
        
        // When
        docView.getPartners().add(newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isFullMode()) {
            if (isQueryStrategy()) {
                builder.update(Document.class);
            } else {
                fullFetch(builder);
            }
        }

        builder.validate();

        assertNoUpdateAndReload(docView);
        assertEquals(doc1.getPartners().size(), docView.getPartners().size() - 1);
        docView.getPartners().remove(newPerson);
        assertSubviewEquals(doc1.getPartners(), docView.getPartners());
    }

    @Test
    public void testUpdateAddToNewCollection() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatablePersonView newPerson = getP2View(UpdatablePersonView.class);
        clearQueries();

        // When
        docView.setPartners(new ArrayList<>(docView.getPartners()));
        docView.getPartners().add(newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isFullMode()) {
            if (isQueryStrategy()) {
                builder.update(Document.class);
            } else {
                fullFetch(builder);
            }
        }

        builder.validate();
        assertNoUpdateAndReload(docView);
        assertEquals(doc1.getPartners().size(), docView.getPartners().size() - 1);
        docView.getPartners().remove(newPerson);
        assertSubviewEquals(doc1.getPartners(), docView.getPartners());
    }

    public static void assertSubviewEquals(Collection<Person> persons, Collection<UpdatablePersonView> personSubviews) {
        if (persons == null) {
            assertNull(personSubviews);
            return;
        }

        assertNotNull(personSubviews);
        assertEquals(persons.size(), personSubviews.size());
        for (Person p : persons) {
            boolean found = false;
            for (UpdatablePersonView pSub : personSubviews) {
                if (p.getName().equals(pSub.getName())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                Assert.fail("Could not find a person subview instance with the name: " + p.getName());
            }
        }
    }

    @Override
    protected AssertStatementBuilder fullFetch(AssertStatementBuilder builder) {
        return builder.assertSelect()
                .fetching(Document.class)
                .and();
    }

    @Override
    protected AssertStatementBuilder fullUpdate(AssertStatementBuilder builder) {
        return builder.update(Document.class);
    }

    @Override
    protected AssertStatementBuilder versionUpdate(AssertStatementBuilder builder) {
        return builder.update(Document.class);
    }
}
