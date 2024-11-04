/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.jaxrs.jackson.testsuite;

import com.blazebit.persistence.integration.jaxrs.jackson.testsuite.view.DocumentUpdateView;
import com.blazebit.persistence.integration.jaxrs.jackson.testsuite.view.DocumentView;
import com.blazebit.persistence.integration.jaxrs.jackson.testsuite.entity.Document;
import com.blazebit.persistence.integration.jaxrs.jackson.testsuite.entity.Person;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import static org.junit.Assert.assertEquals;

/**
 * @author Christian Beikov
 * @since 1.5.0
 */
public class DocumentResourceTest extends AbstractJaxrsTest {

    @Test
    public void testUpdateDocument1() throws JsonProcessingException {
        // Given
        Document d1 = createDocument("D1");

        // When
        DocumentUpdateView updateView = transactional(em -> {
            return evm.find(em, DocumentUpdateView.class, d1.getId());
        });
        updateView.setName("D2");
        DocumentView updatedView = webTarget.path("/documents/{id}")
                .resolveTemplate("id", d1.getId())
                .request()
                .buildPut(Entity.entity(toJsonWithoutId(updateView), "application/vnd.blazebit.update1+json"))
                .invoke(DocumentViewImpl.class);

        // Then
        assertEquals(updateView.getName(), updatedView.getName());
    }

    @Test
    public void testUpdateDocument2() throws JsonProcessingException {
        // Given
        Document d1 = createDocument("D1");

        // When
        DocumentUpdateView updateView = transactional(em -> {
            return evm.find(em, DocumentUpdateView.class, d1.getId());
        });
        updateView.setName("D2");
        DocumentView updatedView = webTarget.path("/documents/{id}")
                .resolveTemplate("id", d1.getId())
                .request()
                .buildPut(Entity.entity(toJsonWithoutId(updateView), "application/vnd.blazebit.update2+json"))
                .invoke(DocumentViewImpl.class);

        // Then
        assertEquals(updateView.getName(), updatedView.getName());
    }

    @Test
    public void testUpdateDocument3() throws JsonProcessingException {
        // Given
        Document d1 = createDocument("D1");

        // When
        DocumentUpdateView updateView = transactional(em -> {
            return evm.find(em, DocumentUpdateView.class, d1.getId());
        });
        updateView.setName("D2");
        DocumentView updatedView = webTarget.path("/documents")
                .request()
                .buildPut(Entity.entity(toJsonWithId(updateView), MediaType.APPLICATION_JSON_TYPE))
                .invoke(DocumentViewImpl.class);

        // Then
        assertEquals(updateView.getName(), updatedView.getName());
    }

    private Document createDocument(String name) {
        return createDocument(name, null);
    }

    private Document createDocument(final String name, final Person owner) {
        return createDocument(name, null, 0L, owner);
    }

    private Document createDocument(final String name, final String description, final long age, final Person owner) {
        return transactional(em -> {
            Document d = new Document(name);
            d.setDescription(description);
            d.setAge(age);
            d.setOwner(owner);
            em.persist(d);
            return d;
        });
    }
}
