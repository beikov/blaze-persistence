/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.collections.subview;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.testsuite.entity.DocumentForEntityKeyMaps;
import com.blazebit.persistence.testsuite.entity.PersonForEntityKeyMaps;
import com.blazebit.persistence.testsuite.tx.TxVoidWork;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import com.blazebit.persistence.view.testsuite.AbstractEntityViewTest;
import com.blazebit.persistence.view.testsuite.collections.subview.model.SubviewDocumentCollectionsView;
import com.blazebit.persistence.view.testsuite.collections.subview.model.SubviewDocumentForEntityKeyMapsView;
import com.blazebit.persistence.view.testsuite.collections.subview.model.SubviewPersonForEntityKeyMapsView;
import com.blazebit.persistence.view.testsuite.collections.subview.model.SubviewSimpleDocumentForEntityKeyMapsView;
import jakarta.persistence.EntityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Moritz Becker
 * @since 1.2.0
 */
public class EntityMapKeySubviewTest<T extends SubviewDocumentCollectionsView> extends AbstractEntityViewTest {

    private DocumentForEntityKeyMaps doc1;
    private DocumentForEntityKeyMaps doc2;


    @Override
    protected Class<?>[] getEntityClasses() {
        return new Class<?>[]{
                DocumentForEntityKeyMaps.class,
                PersonForEntityKeyMaps.class
        };
    }

    @Override
    public void setUpOnce() {
        cleanDatabase();
        transactional(new TxVoidWork() {
            @Override
            public void work(EntityManager em) {
                doc1 = new DocumentForEntityKeyMaps("doc1");
                doc2 = new DocumentForEntityKeyMaps("doc2");

                PersonForEntityKeyMaps o1 = new PersonForEntityKeyMaps("pers1");
                PersonForEntityKeyMaps o2 = new PersonForEntityKeyMaps("pers2");
                PersonForEntityKeyMaps o3 = new PersonForEntityKeyMaps("pers3");
                PersonForEntityKeyMaps o4 = new PersonForEntityKeyMaps("pers4");

                em.persist(o1);
                em.persist(o2);
                em.persist(o3);
                em.persist(o4);

                doc1.getContactDocuments().put(o1, doc2);
                doc2.getContactDocuments().put(o2, doc1);

                em.persist(doc1);
                em.persist(doc2);
            }
        });
    }

    @Before
    public void setUp() {
        doc1 = cbf.create(em, DocumentForEntityKeyMaps.class).where("name").eq("doc1").getSingleResult();
        doc2 = cbf.create(em, DocumentForEntityKeyMaps.class).where("name").eq("doc2").getSingleResult();
    }

    /**
     * Test for https://github.com/Blazebit/blaze-persistence/issues/329
     */
    @Test
    public void testCollections() {
        EntityViewManager evm = build(
                SubviewDocumentForEntityKeyMapsView.class,
                SubviewSimpleDocumentForEntityKeyMapsView.class,
                SubviewPersonForEntityKeyMapsView.class
        );

        CriteriaBuilder<DocumentForEntityKeyMaps> criteria = cbf.create(em, DocumentForEntityKeyMaps.class, "d")
                .orderByAsc("id");
        CriteriaBuilder<SubviewDocumentForEntityKeyMapsView> cb = evm.applySetting(EntityViewSetting.create(SubviewDocumentForEntityKeyMapsView.class), criteria);
        List<SubviewDocumentForEntityKeyMapsView> results = cb.getResultList();

        assertEquals(2, results.size());
        // Doc1
        assertEquals(doc1.getName(), results.get(0).getName());
        assertContactDocumentsEquals(doc1.getContactDocuments(), results.get(0).getContactDocuments());

        // Doc2
        assertEquals(doc2.getName(), results.get(1).getName());
        assertContactDocumentsEquals(doc2.getContactDocuments(), results.get(1).getContactDocuments());
    }

    private static void assertContactDocumentsEquals(Map<PersonForEntityKeyMaps, DocumentForEntityKeyMaps> contactDocuments, Map<SubviewPersonForEntityKeyMapsView, SubviewSimpleDocumentForEntityKeyMapsView> contactDocumentSubviews) {
        if (contactDocuments == null) {
            assertNull(contactDocumentSubviews);
            return;
        }

        assertNotNull(contactDocumentSubviews);
        assertEquals(contactDocuments.size(), contactDocumentSubviews.size());
        for (Map.Entry<PersonForEntityKeyMaps, DocumentForEntityKeyMaps> contactDocumentEntry : contactDocuments.entrySet()) {
            boolean found = false;
            for (Map.Entry<SubviewPersonForEntityKeyMapsView, SubviewSimpleDocumentForEntityKeyMapsView> contactDocumentSubviewEntry : contactDocumentSubviews.entrySet()) {
                if (contactDocumentEntry.getKey().getName().equals(contactDocumentSubviewEntry.getKey().getName()) &&
                        contactDocumentEntry.getValue().getName().equals(contactDocumentSubviewEntry.getValue().getName())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                Assert.fail("Could not find an entry (PersonForCollectionsView, SubviewDocumentCollectionsView) with names: (" + contactDocumentEntry.getKey().getName() + ", " + contactDocumentEntry.getValue().getName() + ")");
            }
        }
    }
}
