/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.impl.proxy;

import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.impl.metamodel.AbstractMethodAttribute;
import com.blazebit.persistence.view.impl.metamodel.ManagedViewTypeImplementor;
import com.blazebit.persistence.view.impl.metamodel.MappingConstructorImpl;
import com.blazebit.persistence.view.metamodel.ViewType;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Christian Beikov
 * @since 1.5.0
 */
public class DirectConstructorReflectionInstantiator<T> extends AbstractReflectionInstantiator<T> {

    private final Constructor<T> constructor;
    private final int idSwapIndex;

    public DirectConstructorReflectionInstantiator(MappingConstructorImpl<T> mappingConstructor, ProxyFactory proxyFactory, ManagedViewTypeImplementor<T> viewType, Class<?>[] parameterTypes,
                                                   EntityViewManager entityViewManager, List<MutableBasicUserTypeEntry> mutableBasicUserTypes, List<TypeConverterEntry> typeConverterEntries) {
        super(mutableBasicUserTypes, typeConverterEntries, parameterTypes);
        Class<T> proxyClazz = (Class<T>) proxyFactory.getProxy(entityViewManager, viewType);
        Constructor<T> javaConstructor;

        try {
            javaConstructor = proxyClazz.getDeclaredConstructor(parameterTypes);
            javaConstructor.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException ex) {
            throw new IllegalArgumentException("The given mapping constructor '" + mappingConstructor + "' does not map to a constructor of the proxy class: " + proxyClazz
                    .getName(), ex);
        }

        this.constructor = javaConstructor;
        this.idSwapIndex = viewType instanceof ViewType<?> ? ((AbstractMethodAttribute<?, ?>) ((ViewType<?>) viewType).getIdAttribute()).getAttributeIndex() : 0;
    }

    @Override
    public T newInstance(Object[] tuple) {
        try {
            if (idSwapIndex != 0) {
                Object tmp = tuple[0];
                tuple[0] = tuple[idSwapIndex];
                tuple[idSwapIndex] = tmp;
            }
            prepareTuple(tuple);
            T instance = constructor.newInstance(tuple);
            finalizeInstance(instance);
            return instance;
        } catch (Exception ex) {
            String[] types = new String[tuple.length];

            for (int i = 0; i < types.length; i++) {
                if (tuple[i] == null) {
                    types[i] = null;
                } else {
                    types[i] = tuple[i].getClass().getName();
                }
            }
            throw new RuntimeException("Could not invoke the proxy constructor '" + constructor + "' with the given tuple: " + Arrays.toString(tuple) + " with the types: " + Arrays.toString(types), ex);
        }
    }

}
