/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.update.subview.nested.mutable;

import com.blazebit.persistence.testsuite.base.jpa.assertion.AssertStatementBuilder;
import com.blazebit.persistence.testsuite.base.jpa.category.NoDatanucleus;
import com.blazebit.persistence.testsuite.base.jpa.category.NoEclipselink;
import com.blazebit.persistence.testsuite.entity.Document;
import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.view.FlushMode;
import com.blazebit.persistence.view.FlushStrategy;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import com.blazebit.persistence.view.testsuite.update.AbstractEntityViewUpdateDocumentTest;
import com.blazebit.persistence.view.testsuite.update.subview.nested.mutable.model.UpdatableDocumentWithCollectionsView;
import com.blazebit.persistence.view.testsuite.update.subview.nested.mutable.model.UpdatableFriendPersonView;
import com.blazebit.persistence.view.testsuite.update.subview.nested.mutable.model.UpdatableResponsiblePersonView;
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
 * @since 1.2.0
 */
@RunWith(Parameterized.class)
// NOTE: No Datanucleus support yet
@Category({ NoDatanucleus.class, NoEclipselink.class})
public class EntityViewUpdateNestedMutableSubviewCollectionsTest extends AbstractEntityViewUpdateDocumentTest<UpdatableDocumentWithCollectionsView> {

    public EntityViewUpdateNestedMutableSubviewCollectionsTest(FlushMode mode, FlushStrategy strategy, boolean version) {
        super(mode, strategy, version, UpdatableDocumentWithCollectionsView.class);
    }

    @Parameterized.Parameters(name = "{0} - {1} - VERSIONED={2}")
    public static Object[][] combinations() {
        return MODE_STRATEGY_VERSION_COMBINATIONS;
    }

    @Override
    protected void registerViewTypes(EntityViewConfiguration cfg) {
        cfg.addEntityView(UpdatableResponsiblePersonView.class);
        cfg.addEntityView(UpdatableFriendPersonView.class);
    }

    @Override
    protected String[] getFetchedCollections() {
        return new String[] { "people" };
    }

    @Test
    public void testUpdateAddToCollection() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();
        
        // When
        docView.getPeople().add(newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isFullMode()) {
            if (isQueryStrategy()) {
                fullUpdate(builder);
            } else {
                fullFetch(builder)
                        .select(Person.class);

                if (version) {
                    builder.update(Document.class);
                }
            }
        } else {
            if (!isQueryStrategy()) {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "people")
                        .fetching(Person.class)
                        .and();
            }

            if (version) {
                builder.update(Document.class);
            }
        }

        builder.assertInsert()
                    .forRelation(Document.class, "people")
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
    }

    @Test
    public void testUpdateAddToNewCollection() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();

        // When
        docView.setPeople(new ArrayList<>(docView.getPeople()));
        docView.getPeople().add(newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isFullMode()) {
            if (isQueryStrategy()) {
                fullUpdate(builder);
            } else {
                fullFetch(builder)
                        .select(Person.class);

                if (version) {
                    builder.update(Document.class);
                }
            }
        } else {
            if (!isQueryStrategy()) {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "people")
                        .fetching(Person.class)
                        .and();
            }

            if (version) {
                builder.update(Document.class);
            }
        }

        builder.assertInsert()
                    .forRelation(Document.class, "people")
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
    }

    @Test
    public void testUpdateAddToCollectionAndModify() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();

        // When
        newPerson.getFriend().setName("newPerson");
        docView.getPeople().add(newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
                builder.update(Person.class);
                builder.update(Person.class);
                builder.update(Person.class);
            }

            builder.update(Person.class);

            if (version || isFullMode()) {
                builder.update(Document.class);
            }
        } else {
            fullFetch(builder)
                    .assertSelect()
                    .fetching(Person.class)
                    .fetching(Person.class)
                    .and();

            if (version) {
                builder.update(Document.class);
            }

            builder.update(Person.class);
        }
        builder.assertInsert()
                    .forRelation(Document.class, "people")
                .and()
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
        assertEquals("newPerson", p4.getName());
    }

    @Test
    public void testUpdateAddToNewCollectionAndModify() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();

        // When
        newPerson.getFriend().setName("newPerson");
        docView.setPeople(new ArrayList<>(docView.getPeople()));
        docView.getPeople().add(newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
                builder.update(Person.class);
                builder.update(Person.class);
                builder.update(Person.class);
            }

            builder.update(Person.class);

            if (version || isFullMode()) {
                builder.update(Document.class);
            }
        } else {
                fullFetch(builder)
                        .assertSelect()
                        .fetching(Person.class)
                        .fetching(Person.class)
                        .and();

            if (version) {
                builder.update(Document.class);
            }

            builder.update(Person.class);
        }
        builder.assertInsert()
                    .forRelation(Document.class, "people")
                .and()
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
        assertEquals("newPerson", p4.getName());
    }

    @Test
    public void testUpdateModifyCollectionElement() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableFriendPersonView newFriend = getPersonView(p4.getId(), UpdatableFriendPersonView.class);
        clearQueries();

        // When
        docView.getPeople().get(0).setFriend(newFriend);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder)
                        .update(Person.class);
            }

            builder.update(Person.class);

            if (version || isFullMode()) {
                builder.update(Document.class);
            }
        } else {
            if (isFullMode()) {
                fullFetch(builder)
                        .select(Person.class);
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "people")
                        .fetching(Person.class)
                        .and();
            }

            if (version) {
                builder.update(Document.class);
            }

            builder.update(Person.class);
        }
        builder.validate();

        assertNoCollectionUpdateFullAndReload(docView);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
        assertEquals(p4.getId(), p1.getFriend().getId());
    }

    @Test
    public void testUpdateModifyCollectionElementCopy() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableFriendPersonView newFriend = getPersonView(p3.getId(), UpdatableFriendPersonView.class);
        clearQueries();

        // When
        newFriend.setName("newFriend");
        docView.getPeople().get(0).setFriend(newFriend);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
                builder.update(Person.class);
            }

            builder.update(Person.class);
            if (version || isFullMode()) {
                builder.update(Document.class);
            }
        } else {
            fullFetch(builder);

            if (version) {
                builder.update(Document.class);
            }

            builder.update(Person.class);
        }
        builder.validate();

        assertNoCollectionUpdateFullAndReload(docView);
        assertEquals(p3.getId(), p1.getFriend().getId());
        assertEquals("newFriend", p3.getName());
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
    }

    @Test
    public void testUpdateModifyCollectionElementAndModify() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        UpdatableFriendPersonView newFriend = getPersonView(p4.getId(), UpdatableFriendPersonView.class);
        clearQueries();

        // When
        newFriend.setName("newFriend");
        docView.getPeople().get(0).setFriend(newFriend);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
            }

            builder.update(Person.class)
                    .update(Person.class);

            if (version || isFullMode()) {
                builder.update(Document.class);
            }
        } else {
            if (isFullMode()) {
                fullFetch(builder);
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "people")
                        .fetching(Person.class)
                        .and();
            }

            builder.select(Person.class);

            if (version) {
                builder.update(Document.class);
            }

            builder.update(Person.class)
                    .update(Person.class);
        }
        builder.validate();

        assertNoCollectionUpdateFullAndReload(docView);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
        assertEquals(p4.getId(), p1.getFriend().getId());
        assertEquals("newFriend", p4.getName());
    }

    @Test
    public void testUpdateModifyCollectionElementSetToNull() {
        // Given
        final UpdatableDocumentWithCollectionsView docView = getDoc1View();
        clearQueries();

        // When
        docView.getPeople().get(0).setFriend(null);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
            }

            builder.update(Person.class);
            if (version || isFullMode()) {
                builder.update(Document.class);
            }
        } else {
            if (isFullMode()) {
                fullFetch(builder);
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "people")
                        .fetching(Person.class)
                        .and();
            }

            if (version) {
                builder.update(Document.class);
            }

            builder.update(Person.class);
        }
        builder.validate();

        assertNoCollectionUpdateAndReload(docView);
        assertSubviewEquals(doc1.getPeople(), docView.getPeople());
        assertNull(p1.getFriend());
    }

    public void assertSubviewEquals(Collection<Person> persons, Collection<UpdatableResponsiblePersonView> personSubviews) {
        if (persons == null) {
            assertNull(personSubviews);
            return;
        }

        assertNotNull(personSubviews);
        assertEquals(persons.size(), personSubviews.size());
        for (Person p : persons) {
            boolean found = false;
            for (UpdatableResponsiblePersonView pSub : personSubviews) {
                if (p.getName().equals(pSub.getName())) {
                    found = true;
                    if (p.getFriend() == null) {
                        assertNull(pSub.getFriend());
                    } else {
                        assertNotNull(pSub.getFriend());
                        assertEquals(p.getFriend().getId(), pSub.getFriend().getId());
                        assertEquals(p.getFriend().getName(), pSub.getFriend().getName());
                    }
                    break;
                }
            }

            if (!found) {
                Assert.fail("Could not find a person subview instance with the name: " + p.getName());
            }
        }
    }

    private void assertNoCollectionUpdateAndReload(UpdatableDocumentWithCollectionsView docView) {
        AssertStatementBuilder afterBuilder = assertQueriesAfterUpdate(docView);

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(afterBuilder)
                        .update(Person.class);
                versionUpdate(afterBuilder);
            }
        } else {
            if (isFullMode()) {
                fullFetch(afterBuilder);
                if (version) {
                    versionUpdate(afterBuilder);
                }
            }
        }

        afterBuilder.validate();
    }

    private void assertNoCollectionUpdateFullAndReload(UpdatableDocumentWithCollectionsView docView) {
        AssertStatementBuilder afterBuilder = assertQueriesAfterUpdate(docView);

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(afterBuilder).update(Person.class)
                        .update(Person.class);
                versionUpdate(afterBuilder);
            }
        } else {
            if (isFullMode()) {
                fullFetch(afterBuilder);
                if (version) {
                    versionUpdate(afterBuilder);
                }
            }
        }

        afterBuilder.validate();
    }

    private AssertStatementBuilder assertReplaceAnd(AssertStatementBuilder builder) {
        builder.delete(Document.class, "people")
                .insert(Document.class, "people");
        if (doc1.getPeople().size() > 1) {
            builder.insert(Document.class, "people");
        }
        return builder;
    }

    @Override
    protected AssertStatementBuilder fullFetch(AssertStatementBuilder builder) {
        return builder.assertSelect()
                .fetching(Document.class)
                .fetching(Document.class, "people")
                .fetching(Person.class)
                .fetching(Person.class)
                .and();
    }

    @Override
    protected AssertStatementBuilder fullUpdate(AssertStatementBuilder builder) {
        assertReplaceAnd(builder)
                .update(Person.class)
                .update(Person.class)
                .update(Person.class)
                .update(Person.class);
        builder.update(Document.class);
        return builder;
    }

    @Override
    protected AssertStatementBuilder versionUpdate(AssertStatementBuilder builder) {
        return builder.update(Document.class);
    }
}
