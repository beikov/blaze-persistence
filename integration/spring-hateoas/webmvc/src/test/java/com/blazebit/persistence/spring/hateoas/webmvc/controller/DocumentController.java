/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.spring.hateoas.webmvc.controller;

import com.blazebit.persistence.spring.data.repository.KeysetPageable;
import com.blazebit.persistence.spring.data.webmvc.KeysetConfig;
import com.blazebit.persistence.spring.hateoas.webmvc.KeysetAwarePagedResourcesAssembler;
import com.blazebit.persistence.spring.hateoas.webmvc.entity.Document;
import com.blazebit.persistence.spring.hateoas.webmvc.repository.ReadOnlyDocumentViewRepository;
import com.blazebit.persistence.spring.hateoas.webmvc.view.DocumentView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Christian Beikov
 * @since 1.5.0
 */
@RestController
public class DocumentController {

    private final ReadOnlyDocumentViewRepository readOnlyDocumentViewRepository;

    @Autowired
    public DocumentController(ReadOnlyDocumentViewRepository readOnlyDocumentViewRepository) {
        this.readOnlyDocumentViewRepository = readOnlyDocumentViewRepository;
    }

    @GetMapping("/documents")
    public HttpEntity<Page<DocumentView>> getDocuments(
            @KeysetConfig(Document.class) @PageableDefault(sort = "id") KeysetPageable keysetPageable,
            KeysetAwarePagedResourcesAssembler<DocumentView> assembler) {
        Page<DocumentView> resultPage = readOnlyDocumentViewRepository.findAll(null, keysetPageable);

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        for (Link link : assembler.toModel(resultPage).getLinks()) {
            if (link.getRel() == IanaLinkRelations.FIRST || link.getRel() == IanaLinkRelations.PREV || link.getRel() == IanaLinkRelations.NEXT || link.getRel() == IanaLinkRelations.LAST) {
                headers.add(HttpHeaders.LINK, link.toString());
            }
        }

        return new HttpEntity<>(resultPage, headers);
    }

    @GetMapping(path = "/documents", produces = { "application/hal+json" })
    public PagedModel<EntityModel<DocumentView>> getDocumentsHateoas(
            @KeysetConfig(Document.class) @PageableDefault(sort = "id") KeysetPageable keysetPageable,
            KeysetAwarePagedResourcesAssembler<DocumentView> assembler) {
        return assembler.toModel(readOnlyDocumentViewRepository.findAll(null, keysetPageable));
    }
}
