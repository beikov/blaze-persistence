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
import com.blazebit.persistence.view.testsuite.update.subview.nested.mutable.model.UpdatableDocumentWithMapsView;
import com.blazebit.persistence.view.testsuite.update.subview.nested.mutable.model.UpdatableFriendPersonView;
import com.blazebit.persistence.view.testsuite.update.subview.nested.mutable.model.UpdatableResponsiblePersonView;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
@RunWith(Parameterized.class)
// NOTE: No Datanucleus support yet
@Category({ NoDatanucleus.class, NoEclipselink.class })
public class EntityViewUpdateNestedMutableSubviewMapsTest extends AbstractEntityViewUpdateDocumentTest<UpdatableDocumentWithMapsView> {

    public EntityViewUpdateNestedMutableSubviewMapsTest(FlushMode mode, FlushStrategy strategy, boolean version) {
        super(mode, strategy, version, UpdatableDocumentWithMapsView.class);
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
        return new String[] { "contacts" };
    }

    // NOTE: See https://github.com/h2database/h2database/issues/2288 and wait for 1.4.201
    @Override
    protected void cleanDatabase() {
        cleanDatabaseWithCleaner();
    }

    @Test
    public void testUpdateAddToCollection() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();
        
        // When
        docView.getContacts().put(2, newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
                builder.update(Person.class)
                        .update(Person.class)
                        .update(Person.class)
                        .update(Person.class);
            }
        } else {
            if (isFullMode()) {
                fullFetch(builder);
                builder.assertSelect()
                        .fetching(Person.class)
                        .fetching(Person.class)
                        .and();
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "contacts")
                        .fetching(Person.class)
                        .and();
            }
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.insert(Document.class, "contacts")
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
    }

    @Test
    public void testUpdateAddToNewCollection() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();

        // When
        docView.setContacts(new HashMap<>(docView.getContacts()));
        docView.getContacts().put(2, newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
                builder.update(Person.class)
                        .update(Person.class)
                        .update(Person.class)
                        .update(Person.class);
            }
        } else {
            if (isFullMode()) {
                fullFetch(builder);
                builder.assertSelect()
                        .fetching(Person.class)
                        .fetching(Person.class)
                        .and();
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "contacts")
                        .fetching(Person.class)
                        .and();
            }
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.assertInsert()
                    .forRelation(Document.class, "contacts")
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
    }

    @Test
    public void testUpdateAddToCollectionAndModify() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();

        // When
        newPerson.getFriend().setName("newPerson");
        docView.getContacts().put(2, newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                builder.update(Person.class)
                        .update(Person.class)
                        .update(Person.class)
                        .update(Person.class);
                assertReplaceAnd(builder);
            } else {
                builder.update(Person.class);
            }
        } else {
            fullFetch(builder);
            builder.assertSelect()
                    .fetching(Person.class)
                    .fetching(Person.class)
                    .and();
            builder.update(Person.class);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.insert(Document.class, "contacts")
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
        assertEquals("newPerson", p4.getName());
    }

    @Test
    public void testUpdateAddToNewCollectionAndModify() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableResponsiblePersonView newPerson = getP2View(UpdatableResponsiblePersonView.class);
        clearQueries();

        // When
        newPerson.getFriend().setName("newPerson");
        docView.setContacts(new HashMap<>(docView.getContacts()));
        docView.getContacts().put(2, newPerson);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
                builder.update(Person.class)
                        .update(Person.class)
                        .update(Person.class)
                        .update(Person.class);
            } else {
                builder.update(Person.class);
            }
        } else {
            fullFetch(builder);
            builder.assertSelect()
                    .fetching(Person.class)
                    .fetching(Person.class)
                    .and();
            builder.update(Person.class);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.insert(Document.class, "contacts")
                .validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
        assertEquals("newPerson", p4.getName());
    }

    @Test
    public void testUpdateModifyCollectionElement() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableFriendPersonView newFriend = getPersonView(p4.getId(), UpdatableFriendPersonView.class);
        clearQueries();

        // When
        docView.getContacts().get(1).setFriend(newFriend);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                builder.update(Person.class);
                assertReplaceAnd(builder);
            }

            builder.update(Person.class);
        } else {
            if (isFullMode()) {
                fullFetch(builder);
                if (isFullMode()) {
                    builder.assertSelect()
                            .fetching(Person.class)
                            .and();
                }
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "contacts")
                        .fetching(Person.class)
                        .and();
                if (isFullMode()) {
                    builder.assertSelect()
                            .fetching(Person.class)
                            .fetching(Person.class)
                            .and();
                }
            }
            builder.update(Person.class);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
        assertEquals(p4.getId(), p1.getFriend().getId());
    }

    @Test
    public void testUpdateModifyCollectionElementCopy() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableFriendPersonView newFriend = getPersonView(p3.getId(), UpdatableFriendPersonView.class);
        clearQueries();

        // When
        newFriend.setName("newFriend");
        docView.getContacts().get(1).setFriend(newFriend);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                builder.update(Person.class);
                assertReplaceAnd(builder);
            }

            builder.update(Person.class);
        } else {
            fullFetch(builder);
            builder.update(Person.class);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.validate();

        assertNoUpdateAndReload(docView, true);
        assertEquals(p3.getId(), p1.getFriend().getId());
        assertEquals("newFriend", p3.getName());
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
    }

    @Test
    public void testUpdateModifyCollectionElementAndModify() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        UpdatableFriendPersonView newFriend = getPersonView(p4.getId(), UpdatableFriendPersonView.class);
        clearQueries();

        // When
        newFriend.setName("newFriend");
        docView.getContacts().get(1).setFriend(newFriend);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
            }

            builder.update(Person.class);
            builder.update(Person.class);
        } else {
            if (isFullMode()) {
                fullFetch(builder);
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "contacts")
                        .fetching(Person.class)
                        .and();
            }
            builder.assertSelect()
                    .fetching(Person.class)
                    .and();
            builder.update(Person.class);
            builder.update(Person.class);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }

        builder.validate();

        assertNoUpdateAndReload(docView, true);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
        assertEquals(p4.getId(), p1.getFriend().getId());
        assertEquals("newFriend", p4.getName());
    }

    @Test
    public void testUpdateModifyCollectionElementSetToNull() {
        // Given
        final UpdatableDocumentWithMapsView docView = getDoc1View();
        clearQueries();

        // When
        docView.getContacts().get(1).setFriend(null);
        update(docView);

        // Then
        AssertStatementBuilder builder = assertUnorderedQuerySequence();

        if (isQueryStrategy()) {
            if (isFullMode()) {
                assertReplaceAnd(builder);
            }

            builder.update(Person.class);
        } else {
            if (isFullMode()) {
                fullFetch(builder);
            } else {
                builder.assertSelect()
                        .fetching(Document.class)
                        .fetching(Document.class, "contacts")
                        .fetching(Person.class)
                        .and();
            }
            builder.update(Person.class);
        }
        if (version || isQueryStrategy() && isFullMode()) {
            builder.update(Document.class);
        }
        builder.validate();

        assertNoCollectionUpdateAndReload(docView);
        assertSubviewEquals(doc1.getContacts(), docView.getContacts());
        assertNull(p1.getFriend());
    }

    public static void assertSubviewEquals(Map<Integer, Person> persons, Map<Integer, ? extends UpdatableResponsiblePersonView> personSubviews) {
        if (persons == null) {
            assertNull(personSubviews);
            return;
        }

        assertNotNull(personSubviews);
        assertEquals(persons.size(), personSubviews.size());
        for (Map.Entry<Integer, Person> entry : persons.entrySet()) {
            Person p = entry.getValue();
            boolean found = false;
            UpdatableResponsiblePersonView pSub = personSubviews.get(entry.getKey());
            if (pSub != null) {
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

    private void assertNoCollectionUpdateAndReload(UpdatableDocumentWithMapsView docView) {
        AssertStatementBuilder afterBuilder = assertQueriesAfterUpdate(docView);

        if (isQueryStrategy()) {
            if (isFullMode()) {
                afterBuilder.update(Person.class);
                assertReplaceAnd(afterBuilder);
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
        return builder.delete(Document.class, "contacts")
                .insert(Document.class, "contacts");
    }

    @Override
    protected AssertStatementBuilder fullFetch(AssertStatementBuilder builder) {
        return builder.assertSelect()
                .fetching(Document.class)
                .fetching(Document.class, "contacts")
                .fetching(Person.class)
                .fetching(Person.class)
                .and();
    }

    @Override
    protected AssertStatementBuilder fullUpdate(AssertStatementBuilder builder) {
        builder.update(Person.class)
                .update(Person.class);
        assertReplaceAnd(builder);
        if (doc1.getContacts().size() > 1) {
            builder.insert(Document.class, "contacts")
                    .update(Person.class)
                    .update(Person.class);
        }
        builder.update(Document.class);
        return builder;
    }

    @Override
    protected AssertStatementBuilder versionUpdate(AssertStatementBuilder builder) {
        return builder.update(Document.class);
    }
}
