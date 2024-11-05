/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.testsuite.cte;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.testsuite.base.jpa.category.NoEclipselink;
import com.blazebit.persistence.testsuite.entity.Document;
import com.blazebit.persistence.testsuite.entity.IntIdEntity;
import com.blazebit.persistence.testsuite.entity.Person;
import com.blazebit.persistence.testsuite.entity.Version;
import com.blazebit.persistence.testsuite.tx.TxVoidWork;
import com.blazebit.persistence.view.CTEProvider;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import com.blazebit.persistence.view.metamodel.ManagedViewType;
import com.blazebit.persistence.view.testsuite.AbstractEntityViewTest;
import com.blazebit.persistence.view.testsuite.cte.model.DocumentOwnersCTE;
import com.blazebit.persistence.view.testsuite.cte.model.DocumentWithCTE;
import com.blazebit.persistence.view.testsuite.cte.model.PersonWithPartnerDocument;
import com.blazebit.persistence.view.testsuite.cte.model.PersonWithPartnerDocumentFullAged;
import com.blazebit.persistence.view.testsuite.cte.model.PersonWithPartnerDocumentFullAged.FullAgedCTEProvider;
import com.blazebit.persistence.view.testsuite.cte.model.PersonWithPartnerDocumentUnderAged;
import jakarta.persistence.EntityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Giovanni Lovato
 * @since 1.4.0
 */
// NOTE: Uses CTEs and entity joins, so only works for a few combinations
@Category({ NoEclipselink.class })
public class WithCTEMappingTest extends AbstractEntityViewTest {

    private Document doc1;
    private Document doc2;

    @Override
    public void setUpOnce() {
        cleanDatabase();
        transactional(new TxVoidWork() {

            @Override
            public void work(EntityManager em) {
                doc1 = new Document("doc1");
                doc2 = new Document("doc2");

                Person o1 = new Person("pers1", 64);
                Person o2 = new Person("pers2", 32);
                Person o3 = new Person("pers3", 16);
                o1.getLocalized().put(1, "localized1");
                o2.getLocalized().put(1, "localized2");
                o3.getLocalized().put(1, "localized3");

                doc1.setAge(10);
                doc1.setOwner(o1);
                doc2.setAge(20);
                doc2.setOwner(o2);

                doc1.getContacts().put(1, o1);
                doc2.getContacts().put(1, o2);

                doc1.getContacts2().put(2, o1);
                doc2.getContacts2().put(2, o2);

                em.persist(o1);
                em.persist(o2);
                em.persist(o3);

                // Flush doc1 before so we get the ids we would expect
                em.persist(doc1);
                em.flush();

                em.persist(doc2);
                em.flush();

                o1.setPartnerDocument(doc1);
                o2.setPartnerDocument(doc2);
                o3.setPartnerDocument(doc2);
            }
        });
    }

    @Before
    public void setUp() {
        doc1 = cbf.create(em, Document.class).where("name").eq("doc1").getSingleResult();
        doc2 = cbf.create(em, Document.class).where("name").eq("doc2").getSingleResult();
    }

    @Test
    public void testCteBinding() {
        EntityViewManager evm = build(DocumentWithCTE.class);

        CriteriaBuilder<Document> cb = cbf.create(em, Document.class).orderByAsc("id");
        EntityViewSetting<DocumentWithCTE, CriteriaBuilder<DocumentWithCTE>> setting;
        setting = EntityViewSetting.create(DocumentWithCTE.class);
        setting.addOptionalParameter("ownerMaxAge", 63L);
        List<DocumentWithCTE> list = evm.applySetting(setting, cb).getResultList();

        assertEquals(2, list.size());
        assertEquals("doc1", list.get(0).getName());
        assertEquals("doc2", list.get(1).getName());
        assertNull(list.get(0).getOwnedDocumentCount());
        assertEquals(Long.valueOf(1), list.get(1).getOwnedDocumentCount());
    }

    @Test
    public void testCteBindingWithSubview() {
        EntityViewManager evm = build(
                DocumentWithCTE.class,
                PersonWithPartnerDocument.class,
                PersonWithPartnerDocumentFullAged.class,
                PersonWithPartnerDocumentUnderAged.class
        );

        CriteriaBuilder<Person> cb = cbf.create(em, Person.class).orderByAsc("id");
        EntityViewSetting<PersonWithPartnerDocument, CriteriaBuilder<PersonWithPartnerDocument>> setting;
        setting = EntityViewSetting.create(PersonWithPartnerDocument.class);
        List<PersonWithPartnerDocument> list = evm.applySetting(setting, cb).getResultList();

        assertEquals(3, list.size());
        assertEquals("doc1", list.get(0).getPartnerDocument().getName());
        assertEquals(Long.valueOf(1), list.get(0).getPartnerDocument().getOwnedDocumentCount());

        boolean containsProvider = false;
        ManagedViewType<?> viewType = evm.getMetamodel().managedView(PersonWithPartnerDocumentFullAged.class);
        for (CTEProvider provider : viewType.getCteProviders()) {
            if (provider instanceof FullAgedCTEProvider) {
                containsProvider = true;
            }
        }
        assertTrue(containsProvider);
    }

    @Override
    protected Class<?>[] getEntityClasses() {
        return new Class<?>[] {
            IntIdEntity.class,
            Version.class,
            Person.class,
            Document.class,
            DocumentOwnersCTE.class
        };
    }
}
