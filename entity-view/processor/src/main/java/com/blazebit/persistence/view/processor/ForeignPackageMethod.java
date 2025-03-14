/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.view.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Christian Beikov
 * @since 1.6.8
 */
public class ForeignPackageMethod {

    private final String name;
    private final String returnType;
    private final String realReturnType;
    private final boolean packagePrivate;
    private final boolean generic;
    private final List<ForeignPackageMethodParameter> parameters;

    public ForeignPackageMethod(MetaEntityView entity, ExecutableElement element, Context context) {
        this.name = element.getSimpleName().toString();
        this.packagePrivate = !element.getModifiers().contains(Modifier.PROTECTED);
        this.realReturnType = TypeUtils.toTypeString((DeclaredType) entity.getTypeElement().asType(), element.getReturnType(), context);
        boolean isGeneric = false;
        List<? extends VariableElement> parameters = element.getParameters();
        List<ForeignPackageMethodParameter> parameterList = new ArrayList<>(parameters.size());
        for (VariableElement parameter : parameters) {
            isGeneric = isGeneric || parameter.asType().getKind() == TypeKind.TYPEVAR;
            String realType = TypeUtils.toTypeString((DeclaredType) entity.getTypeElement().asType(), parameter.asType(), context);
            String type = parameter.asType().getKind() == TypeKind.TYPEVAR ? parameter.asType().toString() : realType;
            parameterList.add(new ForeignPackageMethodParameter(parameter.getSimpleName().toString(), realType, type));
        }
        this.generic = isGeneric;
        this.returnType = isGeneric ? element.getReturnType().toString() : realReturnType;
        this.parameters = parameterList;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getRealReturnType() {
        return realReturnType;
    }

    public boolean isPackagePrivate() {
        return packagePrivate;
    }

    public boolean isGeneric() {
        return generic;
    }

    public List<ForeignPackageMethodParameter> getParameters() {
        return parameters;
    }
}
