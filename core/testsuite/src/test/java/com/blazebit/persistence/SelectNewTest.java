/*
 * Copyright 2014 Blazebit.
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
package com.blazebit.persistence;

import com.blazebit.persistence.entity.Document;
import com.blazebit.persistence.entity.Person;
import com.blazebit.persistence.entity.Version;
import com.blazebit.persistence.model.DocumentCount;
import com.blazebit.persistence.model.DocumentPartnerView;
import com.blazebit.persistence.model.DocumentViewModel;
import java.lang.reflect.Constructor;
import java.util.List;
import javax.persistence.EntityTransaction;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Christian Beikov
 * @author Moritz Becker
 * @since 1.0
 */
public class SelectNewTest extends AbstractCoreTest {
    @Before
    public void setUp() {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Person p = new Person("Karl");
            p.getLocalized().put(1, "msg1");
            p.getLocalized().put(2, "msg2");
            em.persist(p);
            
            Version v1 = new Version();
            Version v2 = new Version();
            Version v3 = new Version();
            em.persist(v1);
            em.persist(v2);
            em.persist(v3);
            
            Document doc1 = new Document("Doc1", p, v1, v3);
            doc1.getPartners().add(p);
            em.persist(doc1);
            p.setPartnerDocument(doc1);
            em.persist(new Document("Doc2", p, v2));

            em.flush();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSelectNewDocumentViewModel() {
        CriteriaBuilder<DocumentViewModel> criteria = cbf.from(em, Document.class)
                .selectNew(DocumentViewModel.class).with("name").end().orderByAsc("name");

        assertEquals("SELECT document.name FROM Document document ORDER BY document.name ASC NULLS LAST", criteria.getQueryString());
        List<DocumentViewModel> actual = criteria.getQuery().getResultList();

        /* expected */
        List<Document> expected = em.createQuery("FROM Document d ORDER BY d.name ASC", Document.class).getResultList();

        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(actual.get(i).getName(), expected.get(i).getName());
        }
    }

    @Test
    public void testSelectNewDocument() {
        CriteriaBuilder<Document> criteria = cbf.from(em, Document.class, "d");
        criteria.selectNew(Document.class).with("d.name").end().where("LENGTH(d.name)").le(4).orderByAsc("d.name");
        assertEquals("SELECT d.name FROM Document d WHERE LENGTH(d.name) <= :param_0 ORDER BY d.name ASC NULLS LAST", criteria.getQueryString());
        List<Document> actual = criteria.getQuery().getResultList();

        /* expected */
        List<Document> expected = em.createQuery("FROM Document d ORDER BY d.name ASC", Document.class).getResultList();

        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(actual.get(i).getName(), expected.get(i).getName());
        }
    }
    
    @Test
    public void testSelectNewSubquery() {
        CriteriaBuilder<DocumentCount> crit = cbf.from(em, Document.class, "d")
            .selectNew(DocumentCount.class).withSubquery().from(Document.class).select("COUNT(document.id)").end().end();
        
        assertEquals("SELECT (SELECT COUNT(document.id) FROM Document document) FROM Document d", crit.getQueryString());
        List<DocumentCount> actual = crit.getResultList();
        
        /* expected */
        Long expectedCount = (Long) em.createQuery("SELECT COUNT(d.id) FROM Document d").getSingleResult();
        
        assertEquals((long) expectedCount, actual.size());
    }
    
    @Test
    public void testSelectCollection() {
        CriteriaBuilder<DocumentPartnerView> crit = cbf.from(em, Document.class, "d")
            .selectNew(DocumentPartnerView.class).with("id").with("partners").end();

        assertEquals("SELECT d.id, " + joinAliasValue("partners") + " FROM Document d LEFT JOIN d.partners partners", crit.getQueryString());
    }
    
    @Test
    public void testSelectNewModel(){
        CriteriaBuilder<Document> criteria = cbf.from(em, Document.class, "d");
        criteria.selectNew(Document.class).with("d.owner.name").end().where("d.age").lt(4);
        
        
        assertEquals("SELECT owner.name FROM Document d JOIN d.owner owner WHERE d.age < :param_0", criteria.getQueryString());
    }
    
    @Test
    public void testSelectNewWithParameters(){
        CriteriaBuilder<Document> criteria = cbf.from(em, Document.class, "d");
        criteria.selectNew(Document.class).with("d.contacts[:index].partnerDocument.name").end().where("d.age").lt(4);
        
        assertEquals("SELECT partnerDocument.name FROM Document d LEFT JOIN d.contacts contacts " + ON_CLAUSE + " KEY(contacts) = :index LEFT JOIN contacts.partnerDocument partnerDocument WHERE d.age < :param_0", criteria.getQueryString());
    }
    
    @Test(expected = NullPointerException.class)
    public void testSelectNewNullClass(){
        CriteriaBuilder<Document> criteria = cbf.from(em, Document.class, "d");
        criteria.selectNew((Class<Document>)null);        
    }
    
    @Test(expected = NullPointerException.class)
    public void testSelectNewNullConstructor(){
        CriteriaBuilder<Document> criteria = cbf.from(em, Document.class, "d");
        criteria.selectNew((Constructor<Document>)null);        
    }
}
