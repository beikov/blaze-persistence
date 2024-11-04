/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.jackson;

import com.fasterxml.jackson.core.JsonParser;

/**
 * This interface is used to supply an entity view id from platform specific sources
 * to the deserializer. The deserializer uses values provided by {@link EntityViewIdValueAccessor}
 * as fallback in case no ID value extraction via the {@link JsonParser} is possible.
 *
 * @author Moritz Becker
 * @since 1.5.0
 */
public interface EntityViewIdValueAccessor {

    /**
     * Retrieve an ID value for the entity view that is being deserialized at invocation time.
     *
     * @param jsonParser The {@link JsonParser} instance used by the deserializer
     * @param idType The ID type
     * @param <T> An ID type parameter
     * @return The ID value or null
     */
    <T> T getValue(JsonParser jsonParser, Class<T> idType);
}
