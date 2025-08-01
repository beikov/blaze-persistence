/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.integration.graphql;

import com.blazebit.annotation.AnnotationUtils;
import com.blazebit.lang.StringUtils;
import com.blazebit.persistence.impl.ExpressionUtils;
import com.blazebit.persistence.parser.EntityMetamodel;
import com.blazebit.persistence.view.CreatableEntityView;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.UpdatableEntityView;
import com.blazebit.persistence.view.impl.metamodel.AbstractAttribute;
import com.blazebit.persistence.view.metamodel.FlatViewType;
import com.blazebit.persistence.view.metamodel.ManagedViewType;
import com.blazebit.persistence.view.metamodel.MapAttribute;
import com.blazebit.persistence.view.metamodel.MappingAttribute;
import com.blazebit.persistence.view.metamodel.MethodAttribute;
import com.blazebit.persistence.view.metamodel.PluralAttribute;
import com.blazebit.persistence.view.metamodel.SingularAttribute;
import com.blazebit.reflection.ReflectionUtils;
import graphql.language.Description;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A factory for creating a support class for using entity views in a GraphQL environment.
 *
 * @author Christian Beikov
 * @since 1.4.0
 */
public class GraphQLEntityViewSupportFactory {

    private static final Map<Class<?>, String> BASIC_TYPES;
    private static final Map<Class<?>, String> SUPPORTED_TYPES;
    private static final String[] JAVA_TIME_SER_TYPES = {
        Duration.class.getName(),
        Instant.class.getName(),
        LocalDate.class.getName(),
        LocalDateTime.class.getName(),
        LocalTime.class.getName(),
        ZonedDateTime.class.getName(),
        ZoneOffset.class.getName(),
        "java.time.ZoneRegion",
        OffsetTime.class.getName(),
        OffsetDateTime.class.getName(),
        Year.class.getName(),
        YearMonth.class.getName(),
        MonthDay.class.getName(),
        Period.class.getName()
    };

    static {
        Map<Class<?>, String> types = new HashMap<>();
        types.put(boolean.class, "Boolean");
        types.put(Boolean.class, "Boolean");
        types.put(int.class, "Int");
        types.put(Integer.class, "Int");
        types.put(float.class, "Float");
        types.put(Float.class, "Float");
        types.put(double.class, "Float");
        types.put(Double.class, "Float");
        types.put(String.class, "String");
        BASIC_TYPES = types;

        Map<Class<?>, String> extendedTypes = new HashMap<>();
        // Java primitives
        extendedTypes.put(long.class, "Long");
        extendedTypes.put(Long.class, "Long");
        extendedTypes.put(short.class, "Short");
        extendedTypes.put(Short.class, "Short");
        extendedTypes.put(byte.class, "Byte");
        extendedTypes.put(Byte.class, "Byte");
        extendedTypes.put(BigDecimal.class, "BigDecimal");
        extendedTypes.put(BigInteger.class, "BigInteger");
        extendedTypes.put(char.class, "Char");
        extendedTypes.put(Character.class, "Char");
        // Extensions
        extendedTypes.put(LocalDate.class, "Date");
        extendedTypes.put(LocalTime.class, "Time");
        extendedTypes.put(OffsetTime.class, "Time");
        extendedTypes.put(LocalDateTime.class, "DateTime");
        extendedTypes.put(OffsetDateTime.class, "DateTime");
        extendedTypes.put(ZonedDateTime.class, "DateTime");
        extendedTypes.put(Locale.class, "Locale");
        extendedTypes.put(Currency.class, "Currency");
        extendedTypes.put(UUID.class, "UUID");
        extendedTypes.put(URL.class, "Url");

        Map<Class<?>, String> supportedTypes = new HashMap<>(BASIC_TYPES.size() + extendedTypes.size());
        supportedTypes.putAll(BASIC_TYPES);
        try {
            // If extended scalars are available, assume we can use them
            Class.forName("graphql.scalars.ExtendedScalars");
            extendedTypes.put(Class.forName( "graphql.scalars.country.code.CountryCode" ), "CountryCode");
            supportedTypes.putAll(extendedTypes);
        } catch (ClassNotFoundException notFound) {
            // Ignore
        }
        SUPPORTED_TYPES = supportedTypes;
    }

    private boolean defineNormalTypes;
    private boolean defineRelayTypes;
    private boolean defineNonInputTypesForUpdatableOrCreatableEntityViews;
    private Boolean implementRelayNode;
    private boolean defineRelayNodeIfNotExist;
    private boolean defineDedicatedRelayNodes;
    private Pattern typeFilterPattern;
    private Predicate<ManagedViewType<?>> typeInclusionPredicate;
    private Map<String, GraphQLScalarType> scalarTypeMap;
    private Set<String> registeredScalarTypeNames;
    private Set<String> additionalSerializableBasicTypes;

    /**
     * Creates a new entity view support factory with the given configuration.
     *
     * @param defineNormalTypes If <code>true</code>, generates normal types for managed view types
     * @param defineRelayTypes If <code>true</code>, generates relay types for managed views
     */
    public GraphQLEntityViewSupportFactory(boolean defineNormalTypes, boolean defineRelayTypes) {
        this.defineNormalTypes = defineNormalTypes;
        this.defineRelayTypes = defineRelayTypes;
    }

    /**
     * Returns <code>true</code> if normal types should be defined.
     *
     * @return <code>true</code> if normal types should be defined
     */
    public boolean isDefineNormalTypes() {
        return defineNormalTypes;
    }

    /**
     * Sets whether normal types should be defined.
     *
     * @param defineNormalTypes Whether normal types should be defined
     */
    public void setDefineNormalTypes(boolean defineNormalTypes) {
        this.defineNormalTypes = defineNormalTypes;
    }

    /**
     * Returns <code>true</code> if Relay types should be defined.
     *
     * @return <code>true</code> if Relay types should be defined
     */
    public boolean isDefineRelayTypes() {
        return defineRelayTypes;
    }

    /**
     * Sets whether Relay types should be defined.
     *
     * @param defineRelayTypes Whether Relay types should be defined
     */
    public void setDefineRelayTypes(boolean defineRelayTypes) {
        this.defineRelayTypes = defineRelayTypes;
    }

    /**
     * Returns <code>true</code> if non-input types should be defined for updatable or creatable entity views.
     *
     * @return <code>true</code> if non-input types should be defined for updatable or creatable entity views
     */
    public boolean isDefineNonInputTypesForUpdatableOrCreatableEntityViews() {
        return defineNonInputTypesForUpdatableOrCreatableEntityViews;
    }

    /**
     * Sets whether non-input types should be defined for updatable or creatable entity views.
     *
     * @param defineNonInputTypesForUpdatableOrCreatableEntityViews Whether non-input types should be defined for updatable or creatable entity views
     */
    public void setDefineNonInputTypesForUpdatableOrCreatableEntityViews(boolean defineNonInputTypesForUpdatableOrCreatableEntityViews) {
        this.defineNonInputTypesForUpdatableOrCreatableEntityViews = defineNonInputTypesForUpdatableOrCreatableEntityViews;
    }

    /**
     * Returns <code>true</code> if node types should implement the Relay Node interface.
     *
     * @return <code>true</code> if node types should implement the Relay Node interface
     */
    public boolean isImplementRelayNode() {
        return implementRelayNode == null ? defineRelayNodeIfNotExist : implementRelayNode;
    }

    /**
     * Sets whether Relay Node type should be implemented by node types.
     *
     * @param implementRelayNode Whether Relay Node type should be implemented by node types
     */
    public void setImplementRelayNode(boolean implementRelayNode) {
        this.implementRelayNode = implementRelayNode;
    }

    /**
     * Returns <code>true</code> if the Relay Node interface should be created if not found in the type registry.
     *
     * @return <code>true</code> if the Relay Node interface should be created if not found in the type registry
     */
    public boolean isDefineRelayNodeIfNotExist() {
        return defineRelayNodeIfNotExist;
    }

    /**
     * Sets whether the Relay Node interface should be defined if not found in the type registry.
     *
     * @param defineRelayNodeIfNotExist Whether the Relay Node interface should be defined if not found in the type registry
     */
    public void setDefineRelayNodeIfNotExist(boolean defineRelayNodeIfNotExist) {
        this.defineRelayNodeIfNotExist = defineRelayNodeIfNotExist;
    }

    /**
     * Returns <code>true</code> if dedicated types should be created, name-suffixed with {@code Node}, as relay node types,
     * or <code>false</code> if the object types for entity views should be used directly.
     *
     * @return <code>true</code> if dedicated types should be created, name-suffixed with {@code Node}, as relay node types
     * @since 1.6.9
     */
    public boolean isDefineDedicatedRelayNodes() {
        return defineDedicatedRelayNodes;
    }

    /**
     * Sets whether dedicated types should be created, name-suffixed with {@code Node}, as relay node types,
     * or <code>false</code> if the object types for entity views should be used directly.
     *
     * @param defineDedicatedRelayNodes Whether dedicated types should be created, name-suffixed with {@code Node}, as relay node types
     * @since 1.6.9
     */
    public void setDefineDedicatedRelayNodes(boolean defineDedicatedRelayNodes) {
        this.defineDedicatedRelayNodes = defineDedicatedRelayNodes;
    }

    /**
     * Returns <code>true</code> if the scalar type definition should be created if not found in the type registry.
     *
     * @return <code>true</code> if the scalar type definition should be created if not found in the type registry
     */
    public boolean isRegisterScalarTypeDefinitions() {
        return registeredScalarTypeNames != null;
    }

    /**
     * Sets whether the scalar type definition should be defined if not found in the type registry.
     *
     * @param registerScalarTypeDefinitions Whether the scalar type definition should be defined if not found in the type registry
     */
    public void setRegisterScalarTypeDefinitions(boolean registerScalarTypeDefinitions) {
        if (registerScalarTypeDefinitions) {
            this.registeredScalarTypeNames = new HashSet<>();
        } else {
            this.registeredScalarTypeNames = null;
        }
    }

    /**
     * Returns the scalar types as map with the type name as key.
     *
     * @return the scalar types as map with the type name as key
     * @since 1.6.2
     */
    public Map<String, GraphQLScalarType> getScalarTypeMap() {
        return scalarTypeMap;
    }

    /**
     * Sets the scalar types as map with the type name as key.
     *
     * @param scalarTypeMap the scalar type map
     * @since 1.6.2
     */
    public void setScalarTypeMap(Map<String, GraphQLScalarType> scalarTypeMap) {
        this.scalarTypeMap = scalarTypeMap;
    }

    /**
     * Returns the type filter pattern to use during {@code GraphQLEntityViewSupportFactory.create}.
     *
     * @return the type filter pattern
     * @since 1.6.3
     */
    public Pattern getTypeFilterPattern() {
        return typeFilterPattern;
    }

    /**
     * Sets the type filter pattern to use during {@code GraphQLEntityViewSupportFactory.create}.
     *
     * @param typeFilterPattern the type filter pattern
     * @since 1.6.3
     */
    public void setTypeFilterPattern(Pattern typeFilterPattern) {
        this.typeFilterPattern = typeFilterPattern;
    }

    /**
     * Returns the type inclusion predicate to use during {@code GraphQLEntityViewSupportFactory.create}.
     *
     * @return the type inclusion predicate
     * @since 1.6.16
     */
    public Predicate<ManagedViewType<?>> getTypeInclusionPredicate() {
        return typeInclusionPredicate;
    }

    /**
     * Sets the type inclusion predicate to use during {@code GraphQLEntityViewSupportFactory.create}.
     *
     * @param typeInclusionPredicate the type inclusion predicate
     * @since 1.6.16
     */
    public void setTypeInclusionPredicate(Predicate<ManagedViewType<?>> typeInclusionPredicate) {
        this.typeInclusionPredicate = typeInclusionPredicate;
    }

    /**
     * Returns the additional serializable basic types to use during {@code GraphQLEntityViewSupportFactory.create}.
     *
     * @return the additional serializable basic types
     * @since 1.6.16
     */
    public Set<String> getAdditionalSerializableBasicTypes() {
        return additionalSerializableBasicTypes;
    }

    /**
     * Sets the additional serializable basic types to use during {@code GraphQLEntityViewSupportFactory.create}.
     *
     * @param additionalSerializableBasicTypes the additional serializable basic types
     * @since 1.6.16
     */
    public void setAdditionalSerializableBasicTypes(Set<String> additionalSerializableBasicTypes) {
        this.additionalSerializableBasicTypes = additionalSerializableBasicTypes;
    }

    /**
     * Returns a new {@link GraphQLEntityViewSupport} after registering the entity view types from {@link EntityViewManager}
     * on the given {@link TypeDefinitionRegistry}.
     *
     * @param typeRegistry The registry to register types
     * @param entityViewManager The entity view manager
     * @return a new {@link GraphQLEntityViewSupport}
     */
    public GraphQLEntityViewSupport create(TypeDefinitionRegistry typeRegistry, EntityViewManager entityViewManager) {
        EntityMetamodel entityMetamodel = entityViewManager.getService(EntityMetamodel.class);
        Map<String, ManagedViewType<?>> typeNameToViewType = new HashMap<>();
        Map<String, Map<String, String>> typeNameToFieldMapping = new HashMap<>();
        Map<String, Set<DefaultFetchMapping>> typeNameToDefaultFetchMappings = new HashMap<>();
        List<Type> defaultImplementsTypes;
        if (isImplementRelayNode()) {
            defaultImplementsTypes = Collections.singletonList(new TypeName("Node"));
        } else {
            defaultImplementsTypes = Collections.emptyList();
        }
        Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph = determineViewsForSchema(entityViewManager);
        Collection<ManagedViewType<?>> managedViews = usageGraph.keySet();
        for (ManagedViewType<?> managedView : managedViews) {
            String typeName = getObjectTypeName(managedView);
            String inputTypeName = typeName + "Input";
            String description = getDescription(managedView.getJavaType());
            List<FieldDefinition> fieldDefinitions = new ArrayList<>(managedView.getAttributes().size());
            List<InputValueDefinition> valueDefinitions = new ArrayList<>(managedView.getAttributes().size());
            Set<String> fieldNames = new HashSet<>();
            for (MethodAttribute<?, ?> attribute : managedView.getAttributes()) {
                if (isIgnored(attribute.getJavaMethod())) {
                    continue;
                }
                Type type;
                Type inputType;
                if (attribute instanceof SingularAttribute<?, ?>) {
                    SingularAttribute<?, ?> singularAttribute = (SingularAttribute<?, ?>) attribute;
                    if (singularAttribute.isId() && !singularAttribute.isSubview()) {
                        // Usual numeric ID
                        type = getIdType(typeRegistry, singularAttribute);
                        inputType = getInputIdType(typeRegistry, singularAttribute);
                    } else {
                        type = getElementType(typeRegistry, singularAttribute, entityMetamodel);
                        inputType = getInputElementType(typeRegistry, singularAttribute, entityMetamodel);
                    }
                } else if (attribute instanceof MapAttribute<?, ?, ?>) {
                    MapAttribute<?, ?, ?> mapAttribute = (MapAttribute<?, ?, ?>) attribute;
                    type = getEntryType(typeRegistry, attribute, getKeyType(typeRegistry, mapAttribute), getElementType(typeRegistry, mapAttribute));
                    inputType = getInputEntryType(typeRegistry, attribute, getInputKeyType(typeRegistry, mapAttribute), getInputElementType(typeRegistry, mapAttribute));
                } else {
                    type = makeNonNull(getListType(getElementType(typeRegistry, (PluralAttribute<?, ?, ?>) attribute)));
                    inputType = getListType(getInputElementType(typeRegistry, (PluralAttribute<?, ?, ?>) attribute));
                    if (inputType != null && isNotNull(attribute.getJavaMethod())) {
                        inputType = makeNonNull(inputType);
                    }
                }
                if (type != null) {
                    String fieldName = getFieldName(attribute);
                    FieldDefinition fieldDefinition = new FieldDefinition(fieldName, type);
                    fieldDefinitions.add(fieldDefinition);
                    fieldNames.add(fieldName);
                    addFieldMapping(typeNameToFieldMapping, typeNameToDefaultFetchMappings, managedViews, typeName, "", attribute, fieldName);
                    if (isDefineRelayTypes() && isDefineDedicatedRelayNodes()) {
                        addFieldMapping(typeNameToFieldMapping, typeNameToDefaultFetchMappings, managedViews, typeName, "Node", attribute, fieldName);
                    }
                    if (needsDefinitionInInputType(attribute)) {
                        valueDefinitions.add(new InputValueDefinition(fieldName, inputType));
                        addFieldMapping(typeNameToFieldMapping, typeNameToDefaultFetchMappings, managedViews, typeName, "Input", attribute, fieldName);
                    }
                }
            }
            for (Method method : managedView.getJavaType().getMethods()) {
                if (isIgnored(method) || !isReadAccessor(method)) {
                    continue;
                }
                String fieldName = getFieldName(method);
                if (!fieldNames.add(fieldName)) {
                    continue;
                }
                boolean isWritable = ReflectionUtils.getSetter(managedView.getJavaType(), fieldName) != null;
                Class<?> fieldType = ReflectionUtils.resolveType(managedView.getJavaType(), method.getGenericReturnType());
                Type type;
                Type inputType;
                if (Map.class.isAssignableFrom(fieldType)) {
                    Class<?>[] typeArguments = ReflectionUtils.resolveTypeArguments(managedView.getJavaType(), method.getGenericReturnType());
                    Class<?> keyTypeClass = ReflectionUtils.resolveType(managedView.getJavaType(), typeArguments[0]);
                    Class<?> elementTypeClass = ReflectionUtils.resolveType(managedView.getJavaType(), typeArguments[1]);
                    Type keyType = getKeyType(typeRegistry, entityViewManager, keyTypeClass);
                    Type inputKeyType = getInputKeyType(typeRegistry, entityViewManager, keyTypeClass);
                    Type elementType = getElementType(typeRegistry, entityViewManager, elementTypeClass);
                    Type inputElementType = getInputElementType(typeRegistry, entityViewManager, elementTypeClass);
                    AnnotatedParameterizedType annotatedReturnType = (AnnotatedParameterizedType) method.getAnnotatedReturnType();
                    if (isNotNull(annotatedReturnType.getAnnotatedActualTypeArguments()[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0)) {
                        keyType = makeNonNull(keyType);
                        inputKeyType = makeNonNull(inputKeyType);
                    }
                    if (isNotNull(annotatedReturnType.getAnnotatedActualTypeArguments()[1].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 1)) {
                        elementType = makeNonNull(elementType);
                        inputElementType = makeNonNull(inputElementType);
                    }
                    type = getEntryType(typeRegistry, typeName, fieldName, keyType, elementType);
                    inputType = getInputEntryType(typeRegistry, inputTypeName, fieldName, inputKeyType, inputElementType);
                } else if (Collection.class.isAssignableFrom(fieldType)) {
                    Class<?>[] typeArguments = ReflectionUtils.resolveTypeArguments(managedView.getJavaType(), method.getGenericReturnType());
                    Class<?> elementTypeClass = ReflectionUtils.resolveType(managedView.getJavaType(), typeArguments[0]);
                    Type elementType = getElementType(typeRegistry, entityViewManager, elementTypeClass);
                    Type inputElementType = getInputElementType(typeRegistry, entityViewManager, elementTypeClass);
                    AnnotatedParameterizedType annotatedReturnType = (AnnotatedParameterizedType) method.getAnnotatedReturnType();
                    if (isNotNull(annotatedReturnType.getAnnotatedActualTypeArguments()[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0)) {
                        elementType = makeNonNull(elementType);
                        inputElementType = makeNonNull(inputElementType);
                    }
                    type = makeNonNull(getListType(elementType));
                    inputType = getListType(inputElementType);
                } else {
                    type = getElementType(typeRegistry, entityViewManager, fieldType);
                    inputType = getInputElementType(typeRegistry, entityViewManager, fieldType);
                }
                if (type != null) {
                    if (isNotNull(method)) {
                        type = makeNonNull(type);
                        inputType = makeNonNull(inputType);
                    }
                    FieldDefinition fieldDefinition = new FieldDefinition(fieldName, type);
                    fieldDefinitions.add(fieldDefinition);
                    if (isWritable) {
                        valueDefinitions.add(new InputValueDefinition(fieldName, inputType));
                    }
                }
            }
            List<Type> implementsTypes;
            if (managedView.getInheritanceMapping() != null) {
                implementsTypes = new ArrayList<>(defaultImplementsTypes.size());
                implementsTypes.addAll(defaultImplementsTypes);
                for (ManagedViewType<?> view : managedViews) {
                    if (view.getInheritanceSubtypes().size() > 1
                            && view.getInheritanceSubtypes().contains(managedView)) {
                        implementsTypes.add(new TypeName(getObjectTypeName(view)));
                    }
                }
            } else {
                implementsTypes = defaultImplementsTypes;
            }
            ImplementingTypeDefinition<?> typeDefinition;
            if (managedView.getInheritanceSubtypes().size() > 1 ) {
                typeDefinition = newInterfaceTypeDefinition(typeName, implementsTypes, fieldDefinitions, description);
            } else {
                typeDefinition = newObjectTypeDefinition(typeName, implementsTypes, fieldDefinitions, description);
            }
            addObjectTypeDefinition(typeRegistry, typeNameToViewType, usageGraph, managedView, typeDefinition, newInputObjectTypeDefinition(inputTypeName, valueDefinitions, description));
        }

        Set<String> serializableBasicTypes = new HashSet<>(this.additionalSerializableBasicTypes == null ? Collections.emptySet() : this.additionalSerializableBasicTypes);
        for (javax.persistence.metamodel.Type<?> basicType : entityMetamodel.getBasicTypes()) {
            for (Class<?> superType : ReflectionUtils.getSuperTypes(basicType.getJavaType())) {
                serializableBasicTypes.add(superType.getName());
            }

            serializableBasicTypes.add(basicType.getJavaType().getName());
        }

        serializableBasicTypes.add(Serializable[].class.getName());
        serializableBasicTypes.add(GraphQLCursor.class.getName());
        addSerializableBasicTypes(serializableBasicTypes);
        return new GraphQLEntityViewSupport(typeNameToViewType, typeNameToFieldMapping, typeNameToDefaultFetchMappings, serializableBasicTypes);
    }

    protected void addSerializableBasicTypes(Set<String> serializableBasicTypes) {
        for (String javaTimeSerType : JAVA_TIME_SER_TYPES) {
            if (serializableBasicTypes.contains(javaTimeSerType)) {
                serializableBasicTypes.add("java.time.Ser");
                break;
            }
        }
    }

    /**
     * Determine whether the given method represents a read accessor
     *
     * @param method The method to check
     * @return <code>true</code> if the method represents a read accessor, false otherwise
     */
    protected boolean isReadAccessor(Method method) {
        String methodName = method.getName();
        return !method.isSynthetic() && method.getReturnType() != void.class && method.getParameterTypes().length == 0 && (
            methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))
                || methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))
                || getExplicitFieldName(method) != null);
    }

    /**
     * Returns a new {@link GraphQLEntityViewSupport} after initializing entity view types from {@link EntityViewManager}
     * that are available in the given schema.
     *
     * @param schema The existing schema
     * @param entityViewManager The entity view manager
     * @return a new {@link GraphQLEntityViewSupport}
     */
    public GraphQLEntityViewSupport create(GraphQLSchema schema, EntityViewManager entityViewManager) {
        boolean defineNormalTypes = this.defineNormalTypes;
        boolean defineRelayTypes = this.defineRelayTypes;
        Boolean implementRelayNode = this.implementRelayNode;
        boolean defineRelayNodeIfNotExist = this.defineRelayNodeIfNotExist;
        try {
            // Set all to false so that we don't try to register anything in the null schema builder
            this.defineNormalTypes = false;
            this.defineRelayTypes = false;
            this.implementRelayNode = false;
            this.defineRelayNodeIfNotExist = false;
            // For now, we scan all entity view types. Using the schema can be done later as optimization
            return create((GraphQLSchema.Builder) null, entityViewManager);
        } finally {
            this.defineNormalTypes = defineNormalTypes;
            this.defineRelayTypes = defineRelayTypes;
            this.implementRelayNode = implementRelayNode;
            this.defineRelayNodeIfNotExist = defineRelayNodeIfNotExist;
        }
    }

    /**
     * Returns a new {@link GraphQLEntityViewSupport} after registering the entity view types from {@link EntityViewManager}
     * on the given {@link TypeDefinitionRegistry}.
     *
     * @param schemaBuilder The registry to register types
     * @param entityViewManager The entity view manager
     * @return a new {@link GraphQLEntityViewSupport}
     */
    public GraphQLEntityViewSupport create(GraphQLSchema.Builder schemaBuilder, EntityViewManager entityViewManager) {
        Set<GraphQLType> additionalTypes = isDefineNormalTypes() ? getAndClearAdditionalTypes(schemaBuilder) : Collections.emptySet();

        EntityMetamodel entityMetamodel = entityViewManager.getService(EntityMetamodel.class);
        Map<String, ManagedViewType<?>> typeNameToViewType = new HashMap<>();
        Map<String, Map<String, String>> typeNameToFieldMapping = new HashMap<>();
        Map<String, Set<DefaultFetchMapping>> typeNameToDefaultFetchMappings = new HashMap<>();
        Map<Class<?>, String> registeredTypeNames = new HashMap<>();

        Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph = determineViewsForSchema(entityViewManager);
        Collection<ManagedViewType<?>> managedViews = usageGraph.keySet();
        for (ManagedViewType<?> managedView : managedViews) {
            String typeName = getObjectTypeName(managedView);
            String inputTypeName = getInputObjectTypeName(managedView);
            String description = getDescription(managedView.getJavaType());
            GraphQLObjectType.Builder objectBuilder;
            GraphQLInterfaceType.Builder interfaceBuilder;
            if (managedView.getInheritanceSubtypes().size() > 1) {
                objectBuilder = null;
                interfaceBuilder = GraphQLInterfaceType.newInterface().name(typeName);
            } else {
                objectBuilder = GraphQLObjectType.newObject().name(typeName);
                interfaceBuilder = null;
            }
            GraphQLInputObjectType.Builder inputBuilder = GraphQLInputObjectType.newInputObject().name(inputTypeName);
            if (isImplementRelayNode()) {
                GraphQLTypeReference nodeType = new GraphQLTypeReference("Node");
                if (objectBuilder == null) {
                    interfaceBuilder.withInterface(nodeType);
                } else {
                    objectBuilder.withInterface(nodeType);
                }
            }
            if (description != null) {
                if (objectBuilder == null) {
                    interfaceBuilder.description(description);
                } else {
                    objectBuilder.description(description);
                }
                inputBuilder.description(description);
            }
            for (MethodAttribute<?, ?> attribute : managedView.getAttributes()) {
                if (isIgnored(attribute.getJavaMethod())) {
                    continue;
                }
                GraphQLFieldDefinition.Builder fieldBuilder = GraphQLFieldDefinition.newFieldDefinition();
                String fieldName = getFieldName(attribute);
                fieldBuilder.name(fieldName);
                GraphQLOutputType type;
                GraphQLInputType inputType;
                if (attribute instanceof SingularAttribute<?, ?>) {
                    SingularAttribute<?, ?> singularAttribute = (SingularAttribute<?, ?>) attribute;
                    if (singularAttribute.isId() && !singularAttribute.isSubview()) {
                        type = getIdType(schemaBuilder, singularAttribute, registeredTypeNames);
                        inputType = getInputIdType(schemaBuilder, singularAttribute, registeredTypeNames);
                    } else {
                        type = getElementType(schemaBuilder, singularAttribute, registeredTypeNames, entityMetamodel);
                        inputType = getInputElementType(schemaBuilder, singularAttribute, registeredTypeNames, entityMetamodel);
                    }
                } else if (attribute instanceof MapAttribute<?, ?, ?>) {
                    MapAttribute<?, ?, ?> mapAttribute = (MapAttribute<?, ?, ?>) attribute;
                    type = getEntryType(schemaBuilder, attribute, getKeyType(schemaBuilder, mapAttribute, registeredTypeNames), getElementType(schemaBuilder, mapAttribute, registeredTypeNames));
                    inputType = getInputEntryType(schemaBuilder, attribute, getInputKeyType(schemaBuilder, mapAttribute, registeredTypeNames), getInputElementType(schemaBuilder, mapAttribute, registeredTypeNames));
                } else {
                    type = makeNonNull(getListType(getElementType(schemaBuilder, (PluralAttribute<?, ?, ?>) attribute, registeredTypeNames)));
                    inputType = getListType(getInputElementType(schemaBuilder, (PluralAttribute<?, ?, ?>) attribute, registeredTypeNames));
                    if (inputType != null && isNotNull(attribute.getJavaMethod())) {
                        inputType = makeNonNull(type);
                    }
                }
                if (type != null) {
                    fieldBuilder.type(type);
                    if (objectBuilder == null) {
                        interfaceBuilder.field(fieldBuilder);
                    } else {
                        objectBuilder.field(fieldBuilder);
                    }
                    addFieldMapping(typeNameToFieldMapping, typeNameToDefaultFetchMappings, managedViews, typeName, "", attribute, fieldName);
                    if (isDefineRelayTypes() && isDefineDedicatedRelayNodes()) {
                        addFieldMapping(typeNameToFieldMapping, typeNameToDefaultFetchMappings, managedViews, typeName, "Node", attribute, fieldName);
                    }
                    if (needsDefinitionInInputType(attribute)) {
                        inputBuilder.field(GraphQLInputObjectField.newInputObjectField().name(fieldName).type(inputType).build());
                        addFieldMapping(typeNameToFieldMapping, typeNameToDefaultFetchMappings, managedViews, typeName, "Input", attribute, fieldName);
                    }
                }
            }
            for (Method method : managedView.getJavaType().getMethods()) {
                if (isIgnored(method) || !isReadAccessor(method)) {
                    continue;
                }
                String fieldName = getFieldName(method);
                if (objectBuilder != null && objectBuilder.hasField(fieldName) || interfaceBuilder != null && interfaceBuilder.hasField(fieldName)) {
                    continue;
                }
                boolean isWritable = ReflectionUtils.getSetter(managedView.getJavaType(), fieldName) != null;
                GraphQLFieldDefinition.Builder fieldBuilder = GraphQLFieldDefinition.newFieldDefinition();
                Class<?> fieldType = ReflectionUtils.resolveType(managedView.getJavaType(), method.getGenericReturnType());
                fieldBuilder.name(fieldName);
                GraphQLOutputType type;
                GraphQLInputType inputType;
                if (Map.class.isAssignableFrom(fieldType)) {
                    Class<?>[] typeArguments = ReflectionUtils.resolveTypeArguments(managedView.getJavaType(), method.getGenericReturnType());
                    Class<?> keyTypeClass = ReflectionUtils.resolveType(managedView.getJavaType(), typeArguments[0]);
                    Class<?> elementTypeClass = ReflectionUtils.resolveType(managedView.getJavaType(), typeArguments[1]);
                    GraphQLOutputType keyType = getKeyType(schemaBuilder, entityViewManager, keyTypeClass, registeredTypeNames);
                    GraphQLInputType inputKeyType = getInputKeyType(schemaBuilder, entityViewManager, keyTypeClass, registeredTypeNames);
                    GraphQLOutputType elementType = getElementType(schemaBuilder, entityViewManager, elementTypeClass, registeredTypeNames);
                    GraphQLInputType inputElementType = getInputElementType(schemaBuilder, entityViewManager, elementTypeClass, registeredTypeNames);
                    AnnotatedParameterizedType annotatedReturnType = (AnnotatedParameterizedType) method.getAnnotatedReturnType();
                    if (isNotNull(annotatedReturnType.getAnnotatedActualTypeArguments()[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0)) {
                        keyType = makeNonNull(keyType);
                        inputKeyType = makeNonNull(inputKeyType);
                    }
                    if (isNotNull(annotatedReturnType.getAnnotatedActualTypeArguments()[1].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 1)) {
                        elementType = makeNonNull(elementType);
                        inputElementType = makeNonNull(inputElementType);
                    }
                    type = getEntryType(schemaBuilder, typeName, fieldName, keyType, elementType);
                    inputType = getInputEntryType(schemaBuilder, inputTypeName, fieldName, inputKeyType, inputElementType);
                } else if (Collection.class.isAssignableFrom(fieldType)) {
                    Class<?>[] typeArguments = ReflectionUtils.resolveTypeArguments(managedView.getJavaType(), method.getGenericReturnType());
                    Class<?> elementTypeClass = ReflectionUtils.resolveType(managedView.getJavaType(), typeArguments[0]);
                    GraphQLOutputType elementType = getElementType(schemaBuilder, entityViewManager, elementTypeClass, registeredTypeNames);
                    GraphQLInputType inputElementType = getInputElementType(schemaBuilder, entityViewManager, elementTypeClass, registeredTypeNames);
                    AnnotatedParameterizedType annotatedReturnType = (AnnotatedParameterizedType) method.getAnnotatedReturnType();
                    if (isNotNull(annotatedReturnType.getAnnotatedActualTypeArguments()[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0)) {
                        elementType = makeNonNull(elementType);
                        inputElementType = makeNonNull(inputElementType);
                    }
                    type = makeNonNull(getListType(elementType));
                    inputType = getListType(inputElementType);
                } else {
                    type = getElementType(schemaBuilder, entityViewManager, fieldType, registeredTypeNames);
                    inputType = getInputElementType(schemaBuilder, entityViewManager, fieldType, registeredTypeNames);
                }
                if (type != null) {
                    if (isNotNull(method)) {
                        type = makeNonNull(type);
                        inputType = makeNonNull(inputType);
                    }
                    fieldBuilder.type(type);
                    if (objectBuilder == null) {
                        interfaceBuilder.field(fieldBuilder);
                    } else {
                        objectBuilder.field(fieldBuilder);
                    }
                    if (isWritable) {
                        inputBuilder.field(GraphQLInputObjectField.newInputObjectField().name(fieldName).type(inputType).build());
                    }
                }
            }
            if (managedView.getInheritanceMapping() != null) {
                for (ManagedViewType<?> view : managedViews) {
                    if (view.getInheritanceSubtypes().size() > 1
                            && view.getInheritanceSubtypes().contains(managedView)) {
                        GraphQLTypeReference nodeType = new GraphQLTypeReference(getObjectTypeName(view));
                        if (objectBuilder == null) {
                            interfaceBuilder.withInterface(nodeType);
                        } else {
                            objectBuilder.withInterface(nodeType);
                        }
                    }
                }
            }
            GraphQLNamedType type;
            if (objectBuilder == null) {
                type = interfaceBuilder.build();
            } else {
                type = objectBuilder.build();
            }
            addObjectTypeDefinition(schemaBuilder, typeNameToViewType, usageGraph, managedView, type, inputBuilder.build());
        }

        Set<String> serializableBasicTypes = new HashSet<>(this.additionalSerializableBasicTypes == null ? Collections.emptySet() : this.additionalSerializableBasicTypes);
        for (javax.persistence.metamodel.Type<?> basicType : entityMetamodel.getBasicTypes()) {
            for (Class<?> superType : ReflectionUtils.getSuperTypes(basicType.getJavaType())) {
                serializableBasicTypes.add(superType.getName());
            }

            serializableBasicTypes.add(basicType.getJavaType().getName());
        }

        serializableBasicTypes.add(Serializable[].class.getName());
        serializableBasicTypes.add(GraphQLCursor.class.getName());
        addSerializableBasicTypes(serializableBasicTypes);
        for (GraphQLType additionalType : additionalTypes) {
            String typeName;
            if (additionalType instanceof GraphQLNamedType) {
                typeName = ((GraphQLNamedType) additionalType).getName();
            } else {
                typeName = null;
            }
            if (typeName == null || typeNameToViewType.get(typeName) == null) {
                schemaBuilder.additionalType(additionalType);
            }
        }
        return new GraphQLEntityViewSupport(typeNameToViewType, typeNameToFieldMapping, typeNameToDefaultFetchMappings, serializableBasicTypes);
    }

    private HashMap<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> determineViewsForSchema(EntityViewManager entityViewManager) {
        HashMap<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph = new HashMap<>();
        for (ManagedViewType<?> managedView : entityViewManager.getMetamodel().getManagedViews()) {
            if ((typeFilterPattern == null || typeFilterPattern.matcher(managedView.getJavaType().getName()).matches())
                    && (typeInclusionPredicate == null || typeInclusionPredicate.test(managedView))
                    && !isIgnored(managedView.getJavaType())) {
                // Build up the attribute usage graph recursively
                addUsage(managedView, usageGraph);
            }
        }
        return usageGraph;
    }

    private static void addUsage(ManagedViewType<?> usedType, HashMap<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph) {
        if (usageGraph.containsKey(usedType)) {
            // Already discovered this type
            return;
        }
        usageGraph.put(usedType, new HashSet<>());
        for (MethodAttribute<?, ?> attribute : usedType.getAttributes()) {
            if (attribute.isSubview()) {
                if (attribute instanceof SingularAttribute<?, ?>) {
                    addUsage(attribute, (ManagedViewType<?>) ((SingularAttribute<?, ?>) attribute).getType(), usageGraph);
                } else if (attribute instanceof PluralAttribute<?, ?, ?>) {
                    addUsage(attribute, (ManagedViewType<?>) ((PluralAttribute<?, ?, ?>) attribute).getElementType(), usageGraph);
                    if (attribute instanceof MapAttribute<?, ?, ?>) {
                        com.blazebit.persistence.view.metamodel.Type<?> keyType = ((MapAttribute<?, ?, ?>) attribute).getKeyType();
                        if (keyType instanceof ManagedViewType<?>) {
                            addUsage(attribute, (ManagedViewType<?>) keyType, usageGraph);
                        }
                    }
                }
            }
        }
    }

    private static void addUsage(MethodAttribute<?, ?> attribute, ManagedViewType<?> usedType, HashMap<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph) {
        addUsage(usedType, usageGraph);
        usageGraph.get(usedType).add(attribute);
    }

    private GraphQLList getListType(GraphQLType elementType) {
        if (elementType == null) {
            return null;
        }
        return new GraphQLList(elementType);
    }

    private ListType getListType(Type<?> elementType) {
        if (elementType == null) {
            return null;
        }
        return new ListType(elementType);
    }

    private GraphQLNonNull makeNonNull(GraphQLType type) {
        if (type == null || type instanceof GraphQLNonNull) {
            return (GraphQLNonNull) type;
        }
        return new GraphQLNonNull(type);
    }

    private NonNullType makeNonNull(Type<?> type) {
        if (type == null || type instanceof NonNullType) {
            return (NonNullType) type;
        }
        return new NonNullType(type);
    }

    private void addFieldMapping(Map<String, Map<String, String>> typeNameToFieldMapping, Map<String, Set<DefaultFetchMapping>> typeNameToDefaultFetchMappings, Collection<ManagedViewType<?>> managedViews, String baseName, String suffix, MethodAttribute<?, ?> attribute, String fieldName) {
        String typeName = baseName + suffix;
        Map<String, String> fieldMapping = typeNameToFieldMapping.get(typeName);
        if (fieldMapping == null) {
            typeNameToFieldMapping.put(typeName, fieldMapping = new HashMap<>());
        }
        fieldMapping.put(fieldName, attribute.getName());
        DefaultFetchMapping[] defaultFetchMappings = determineDefaultFetchMappings(attribute);
        for (DefaultFetchMapping defaultFetchMapping : defaultFetchMappings) {
            ArrayList<ManagedViewType<?>> superTypes = getInheritanceSuperTypes(managedViews, attribute.getDeclaringType());
            if (superTypes != null) {
                for (ManagedViewType<?> superType : superTypes) {
                    String superTypeName = getObjectTypeName(superType);
                    Set<DefaultFetchMapping> existingDefaultFetchMappings = typeNameToDefaultFetchMappings.get(superTypeName);
                    if (existingDefaultFetchMappings == null) {
                        typeNameToDefaultFetchMappings.put(superTypeName, existingDefaultFetchMappings = new HashSet<>());
                    }
                    existingDefaultFetchMappings.add(defaultFetchMapping);
                }
            }
            //noinspection unchecked
            Set<ManagedViewType<?>> subtypes = (Set<ManagedViewType<?>>) attribute.getDeclaringType().getInheritanceSubtypes();
            for (ManagedViewType<?> subtype : subtypes) {
                String subtypeName = getObjectTypeName(subtype);
                Set<DefaultFetchMapping> existingDefaultFetchMappings = typeNameToDefaultFetchMappings.get(subtypeName);
                if (existingDefaultFetchMappings == null) {
                    typeNameToDefaultFetchMappings.put(subtypeName, existingDefaultFetchMappings = new HashSet<>());
                }
                existingDefaultFetchMappings.add(defaultFetchMapping);
            }
        }
    }

    private ArrayList<ManagedViewType<?>> getInheritanceSuperTypes(Collection<ManagedViewType<?>> managedViews, ManagedViewType<?> subtype) {
        ArrayList<ManagedViewType<?>> list = null;
        for (ManagedViewType<?> managedView : managedViews) {
            if (managedView != subtype && managedView.getInheritanceSubtypes().contains(subtype)) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(managedView);
            }
        }
        return list;
    }

    protected DefaultFetchMapping[] determineDefaultFetchMappings(MethodAttribute<?, ?> attribute) {
        GraphQLDefaultFetch[] annotations = attribute.getJavaMethod().getAnnotationsByType(GraphQLDefaultFetch.class);
        if (annotations.length != 0) {
            DefaultFetchMapping[] defaultFetchMappings = new DefaultFetchMapping[annotations.length];
            for (int i = 0; i < annotations.length; i++) {
                defaultFetchMappings[i] = new DefaultFetchMappingImpl(attribute.getName(), annotations[i].ifFieldSelected());
            }
            return defaultFetchMappings;
        }
        return DefaultFetchMappingImpl.EMPTY;
    }

    /**
     *
     * @author Christian Beikov
     * @since 1.6.15
     */
    private static final class DefaultFetchMappingImpl implements DefaultFetchMapping {
        private static final DefaultFetchMapping[] EMPTY = new DefaultFetchMapping[0];
        private final String attributeName;
        private final String ifFieldSelected;

        private DefaultFetchMappingImpl(String attributeName, String ifFieldSelected) {
            this.attributeName = attributeName;
            this.ifFieldSelected = ifFieldSelected;
        }

        @Override
        public String getAttributeName() {
            return attributeName;
        }

        @Override
        public String getIfFieldSelected() {
            return ifFieldSelected;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DefaultFetchMapping)) {
                return false;
            }

            DefaultFetchMapping that = (DefaultFetchMapping) o;
            return attributeName.equals(that.getAttributeName()) && ifFieldSelected.equals(that.getIfFieldSelected());
        }

        @Override
        public int hashCode() {
            int result = attributeName.hashCode();
            result = 31 * result + ifFieldSelected.hashCode();
            return result;
        }
    }

    private <T> T getAnnotationValue(Annotation annotation, String memberName) {
        try {
            return (T) annotation.annotationType().getMethod(memberName).invoke(annotation);
        } catch (Exception e) {
            throw new RuntimeException("Can't access annotation member", e);
        }
    }

    private Set<GraphQLType> getAndClearAdditionalTypes(GraphQLSchema.Builder schemaBuilder) {
        // Option 1: Break into the builder and extract the additional types through reflection
        try {
            Field f = GraphQLSchema.Builder.class.getDeclaredField("additionalTypes");
            f.setAccessible(true);
            Set<GraphQLType> graphQLTypes = (Set<GraphQLType>) f.get(schemaBuilder);
            Set<GraphQLType> copy = new HashSet<>(graphQLTypes);
            graphQLTypes.clear();
            return copy;
        } catch (Exception e) {
            try {
                // Option 2: Build an intermediate schema to access the additional types
                //  Building this intermediate schema though only works since 1.3.1,
                //  because a GraphQL interface type is used by default for Java interfaces which wouldn't build before 1.3.1
                GraphQLSchema intermediateSchema = schemaBuilder.build();
                Set<GraphQLType> graphQLTypes = intermediateSchema.getAdditionalTypes();
                Method m = GraphQLSchema.Builder.class.getMethod("clearAdditionalTypes");
                m.invoke(schemaBuilder);
                return graphQLTypes;
            } catch (Exception e2) {
                RuntimeException runtimeException = new RuntimeException("Could not extract the additional types", e2);
                runtimeException.addSuppressed(e);
                throw runtimeException;
            }
        }
    }

    protected ObjectTypeDefinition newObjectTypeDefinition(String typeName, List<FieldDefinition> fieldDefinitions, String description) {
        return newObjectTypeDefinition(typeName, new ArrayList<>(0), fieldDefinitions, description);
    }

    protected ObjectTypeDefinition newObjectTypeDefinition(String typeName, List<Type> implementsTypes, List<FieldDefinition> fieldDefinitions, String description) {
        return ObjectTypeDefinition.newObjectTypeDefinition()
                .name(typeName)
                .description(new Description(description, null, false))
                .implementz(implementsTypes)
                .fieldDefinitions(fieldDefinitions)
                .build();
    }

    protected InterfaceTypeDefinition newInterfaceTypeDefinition(String typeName, List<Type> implementsTypes, List<FieldDefinition> fieldDefinitions, String description) {
        return InterfaceTypeDefinition.newInterfaceTypeDefinition()
                .name(typeName)
                .description(new Description(description, null, false))
                .implementz(implementsTypes)
                .definitions(fieldDefinitions)
                .build();
    }

    protected InputObjectTypeDefinition newInputObjectTypeDefinition(String typeName, List<InputValueDefinition> valueDefinitions, String description) {
        return InputObjectTypeDefinition.newInputObjectDefinition()
                .name(typeName)
                .description(new Description(description, null, false))
                .inputValueDefinitions(valueDefinitions)
                .build();
    }

    protected InterfaceTypeDefinition newInterfaceTypeDefinition(String name, List<FieldDefinition> fieldDefinitions, String description) {
        return InterfaceTypeDefinition.newInterfaceTypeDefinition()
            .name(name)
            .description(new Description(description, null, false))
            .definitions(fieldDefinitions)
            .build();
    }

    protected EnumTypeDefinition newEnumTypeDefinition(String typeName, List<EnumValueDefinition> enumValueDefinitions, String description) {
        return EnumTypeDefinition.newEnumTypeDefinition()
                .name(typeName)
                .description(new Description(description, null, false))
                .enumValueDefinitions(enumValueDefinitions)
                .build();
    }

    /**
     * @deprecated Use {@link #addObjectTypeDefinition(TypeDefinitionRegistry, Map, Map, ManagedViewType, ImplementingTypeDefinition, InputObjectTypeDefinition)} instead
     */
    @Deprecated
    protected void addObjectTypeDefinition(TypeDefinitionRegistry typeRegistry, Map<String, ManagedViewType<?>> typeNameToViewType, ManagedViewType<?> managedView, ImplementingTypeDefinition<?> objectTypeDefinition, InputObjectTypeDefinition inputObjectTypeDefinition) {
        addObjectTypeDefinition(typeRegistry, typeNameToViewType, null, managedView, objectTypeDefinition, inputObjectTypeDefinition);
    }

    protected void addObjectTypeDefinition(TypeDefinitionRegistry typeRegistry, Map<String, ManagedViewType<?>> typeNameToViewType, Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph, ManagedViewType<?> managedView, ImplementingTypeDefinition<?> objectTypeDefinition, InputObjectTypeDefinition inputObjectTypeDefinition) {
        if (!(managedView.isUpdatable() || managedView.isCreatable()) || isDefineNonInputTypesForUpdatableOrCreatableEntityViews() || needsNormalType(usageGraph, managedView)) {
            registerManagedViewType(typeRegistry, typeNameToViewType, managedView, objectTypeDefinition);
            if (isDefineNormalTypes()) {
                typeRegistry.add(objectTypeDefinition);
            }
        }
        if (needsInputType(usageGraph, managedView)) {
            registerManagedViewType(typeRegistry, typeNameToViewType, managedView, inputObjectTypeDefinition);
            if (isDefineNormalTypes()) {
                typeRegistry.add(inputObjectTypeDefinition);
            }
        }
        if ((managedView.isUpdatable() || managedView.isCreatable()) && !isDefineNonInputTypesForUpdatableOrCreatableEntityViews()) {
            return;
        }
        String nodeTypeName;
        ObjectTypeDefinition nodeType;
        if (isDefineDedicatedRelayNodes()) {
            nodeTypeName = objectTypeDefinition.getName() + "Node";
            List<Type> implementTypes = new ArrayList<>(objectTypeDefinition.getImplements());
            implementTypes.add(new TypeName("Node"));
            nodeType = newObjectTypeDefinition(nodeTypeName, implementTypes, objectTypeDefinition.getFieldDefinitions(), null);
        } else {
            nodeTypeName = objectTypeDefinition.getName();
            nodeType = null;
        }

        if (isDefineRelayTypes() && !typeRegistry.getType("Node").isPresent() && (isImplementRelayNode() || isDefineDedicatedRelayNodes()) && isDefineRelayNodeIfNotExist()) {
            List<FieldDefinition> nodeFields = new ArrayList<>(4);
            nodeFields.add(new FieldDefinition("id", new NonNullType(new TypeName("ID"))));
            typeRegistry.add(newInterfaceTypeDefinition("Node", nodeFields, null));
        }

        List<FieldDefinition> edgeFields = new ArrayList<>(2);
        edgeFields.add(new FieldDefinition("node", new NonNullType(new TypeName(nodeTypeName))));
        edgeFields.add(new FieldDefinition("cursor", new NonNullType(new TypeName("String"))));
        ObjectTypeDefinition edgeType = newObjectTypeDefinition(objectTypeDefinition.getName() + "Edge", edgeFields, null);

        List<FieldDefinition> connectionFields = new ArrayList<>(2);
        connectionFields.add(new FieldDefinition("edges", new ListType(new TypeName(edgeType.getName()))));
        connectionFields.add(new FieldDefinition("pageInfo", new NonNullType(new TypeName("PageInfo"))));
        connectionFields.add(new FieldDefinition("totalCount", new NonNullType(new TypeName("Int"))));
        ObjectTypeDefinition connectionType = newObjectTypeDefinition(objectTypeDefinition.getName() + "Connection", connectionFields, null);

        if (isDefineRelayTypes() && !typeRegistry.getType("PageInfo").isPresent() && isDefineRelayNodeIfNotExist()) {
            List<FieldDefinition> pageInfoFields = new ArrayList<>(4);
            pageInfoFields.add(new FieldDefinition("hasNextPage", new NonNullType(new TypeName("Boolean"))));
            pageInfoFields.add(new FieldDefinition("hasPreviousPage", new NonNullType(new TypeName("Boolean"))));
            pageInfoFields.add(new FieldDefinition("startCursor", new TypeName("String")));
            pageInfoFields.add(new FieldDefinition("endCursor", new TypeName("String")));
            typeRegistry.add(newObjectTypeDefinition("PageInfo", pageInfoFields, null));
        }

        if (nodeType != null) {
            registerManagedViewType(typeRegistry, typeNameToViewType, managedView, nodeType);
        }
        registerManagedViewType(typeRegistry, typeNameToViewType, managedView, edgeType);
        registerManagedViewType(typeRegistry, typeNameToViewType, managedView, connectionType);
        if (isDefineRelayTypes()) {
            if (nodeType != null) {
                typeRegistry.add(nodeType);
            }
            typeRegistry.add(edgeType);
            typeRegistry.add(connectionType);
        }
    }

    /**
     * @deprecated Use {@link #addObjectTypeDefinition(GraphQLSchema.Builder, Map, Map, ManagedViewType, GraphQLNamedType, GraphQLInputObjectType)}  instead
     */
    @Deprecated
    protected void addObjectTypeDefinition(GraphQLSchema.Builder schemaBuilder, Map<String, ManagedViewType<?>> typeNameToViewType, ManagedViewType<?> managedView, GraphQLNamedType objectType, GraphQLInputObjectType inputObjectType) {
        addObjectTypeDefinition(schemaBuilder, typeNameToViewType, null, managedView, objectType, inputObjectType);
    }

    protected void addObjectTypeDefinition(GraphQLSchema.Builder schemaBuilder, Map<String, ManagedViewType<?>> typeNameToViewType, Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph, ManagedViewType<?> managedView, GraphQLNamedType objectType, GraphQLInputObjectType inputObjectType) {
        if (!(managedView.isUpdatable() || managedView.isCreatable()) || isDefineNonInputTypesForUpdatableOrCreatableEntityViews() || needsNormalType(usageGraph, managedView)) {
            typeNameToViewType.put(objectType.getName(), managedView);
            if (isDefineNormalTypes()) {
                schemaBuilder.additionalType(objectType);
            }
        }
        if (needsInputType(usageGraph, managedView)) {
            typeNameToViewType.put(inputObjectType.getName(), managedView);
            if (isDefineNormalTypes()) {
                schemaBuilder.additionalType(inputObjectType);
            }
        }
        if ((managedView.isUpdatable() || managedView.isCreatable()) && !isDefineNonInputTypesForUpdatableOrCreatableEntityViews()) {
            return;
        }
        String nodeTypeName;
        String edgeTypeName;
        String connectionTypeName;
        String pageInfoTypeName;
        if (scalarTypeMap == null) {
            if (isDefineDedicatedRelayNodes()) {
                nodeTypeName = objectType.getName() + "Node";
            } else {
                nodeTypeName = objectType.getName();
            }
            edgeTypeName = objectType.getName() + "Edge";
            connectionTypeName = objectType.getName() + "Connection";
            pageInfoTypeName = "PageInfo";
            if (isDefineRelayTypes() && (isImplementRelayNode() || isDefineDedicatedRelayNodes())) {
                GraphQLObjectType.Builder nodeType = GraphQLObjectType.newObject().name(nodeTypeName);
                nodeType.fields(((GraphQLFieldsContainer) objectType).getFieldDefinitions());
                nodeType.withInterface(new GraphQLTypeReference("Node"));
                if (!typeNameToViewType.containsKey("Node") && isDefineRelayNodeIfNotExist()) {
                    GraphQLInterfaceType.Builder nodeInterfaceType = GraphQLInterfaceType.newInterface().name("Node")
                            .field(
                                    GraphQLFieldDefinition.newFieldDefinition().name("id")
                                            .type(new GraphQLNonNull(getScalarType("ID")))
                                            .build()
                            );
                    schemaBuilder.additionalType(nodeInterfaceType.build());
                }
                schemaBuilder.additionalType(nodeType.build());
            }
            typeNameToViewType.put(nodeTypeName, managedView);
        } else {
            // We use the presence of the scalar map to detect if we are on SmallRye
            // We have to adapt to their naming convention for generic types
            nodeTypeName = objectType.getName();
            edgeTypeName = "GraphQLRelayEdge_" + objectType.getName();
            connectionTypeName = "GraphQLRelayConnection_" + objectType.getName();
            pageInfoTypeName = "GraphQLRelayPageInfo";
        }

        GraphQLObjectType.Builder edgeType = GraphQLObjectType.newObject().name(edgeTypeName);
        edgeType.field(GraphQLFieldDefinition.newFieldDefinition().name("node")
                .type(new GraphQLNonNull(new GraphQLTypeReference(nodeTypeName)))
                .build());
        edgeType.field(GraphQLFieldDefinition.newFieldDefinition().name("cursor")
                .type(new GraphQLNonNull(getScalarType("String")))
                .build());

        GraphQLObjectType.Builder connectionType = GraphQLObjectType.newObject().name(connectionTypeName);
        connectionType.field(GraphQLFieldDefinition.newFieldDefinition().name("edges")
                .type(new GraphQLList(new GraphQLTypeReference(edgeTypeName)))
                .build());
        connectionType.field(GraphQLFieldDefinition.newFieldDefinition().name("pageInfo")
                .type(new GraphQLNonNull(new GraphQLTypeReference(pageInfoTypeName)))
                .build());
        connectionType.field(GraphQLFieldDefinition.newFieldDefinition().name("totalCount")
                .type(new GraphQLNonNull(getScalarType("Int")))
                .build());

        if (!typeNameToViewType.containsKey(pageInfoTypeName) && isDefineRelayNodeIfNotExist()) {
            GraphQLObjectType.Builder pageInfoType = GraphQLObjectType.newObject().name(pageInfoTypeName);
            pageInfoType.field(GraphQLFieldDefinition.newFieldDefinition().name("hasNextPage")
                    .type(new GraphQLNonNull(getScalarType("Boolean")))
                    .build());
            pageInfoType.field(GraphQLFieldDefinition.newFieldDefinition().name("hasPreviousPage")
                    .type(new GraphQLNonNull(getScalarType("Boolean")))
                    .build());
            pageInfoType.field(GraphQLFieldDefinition.newFieldDefinition().name("startCursor")
                    .type(getScalarType("String"))
                    .build());
            pageInfoType.field(GraphQLFieldDefinition.newFieldDefinition().name("endCursor")
                    .type(getScalarType("String"))
                    .build());
            schemaBuilder.additionalType(pageInfoType.build());
        }
        typeNameToViewType.put(edgeTypeName, managedView);
        typeNameToViewType.put(connectionTypeName, managedView);
        typeNameToViewType.put(objectType.getName(), managedView);

        if (isDefineRelayTypes()) {
            schemaBuilder.additionalType(edgeType.build());
            schemaBuilder.additionalType(connectionType.build());
        }
    }

    private boolean needsInputType(Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph, ManagedViewType<?> managedView) {
        if (managedView.isUpdatable() || managedView.isCreatable() || usageGraph == null) {
            return true;
        }
        Set<MethodAttribute<?, ?>> usedInAttributes = usageGraph.get(managedView);
        if (usedInAttributes != null) {
            for (MethodAttribute<?, ?> attribute : usedInAttributes) {
                ManagedViewType<?> declaringType = attribute.getDeclaringType();
                if (declaringType.isCreatable() || declaringType.isUpdatable() || isPartOfUpdatableSubviewId(usageGraph, attribute)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean needsNormalType(Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph, ManagedViewType<?> managedView) {
        if (usageGraph == null) {
            return true;
        }
        Set<MethodAttribute<?, ?>> usedInAttributes = usageGraph.get(managedView);
        if (usedInAttributes != null) {
            for (MethodAttribute<?, ?> attribute : usedInAttributes) {
                ManagedViewType<?> declaringType = attribute.getDeclaringType();
                if (!(declaringType.isCreatable() || declaringType.isUpdatable()) || needsInputType(usageGraph, declaringType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPartOfUpdatableSubviewId(Map<ManagedViewType<?>, Set<MethodAttribute<?, ?>>> usageGraph, MethodAttribute<?, ?> attribute) {
        ManagedViewType<?> declaringType = attribute.getDeclaringType();
        if (attribute instanceof SingularAttribute<?, ?>) {
            Set<MethodAttribute<?, ?>> usedInAttributes = usageGraph.get(declaringType);
            if (usedInAttributes != null) {
                if (((SingularAttribute<?, ?>) attribute).isId()) {
                    // When the attribute is an id, attributes referring to its declaring type must be updatable
                    // and their declaring types must be creatable/updatable
                    for (MethodAttribute<?, ?> declaringTypeAttributeReferrer : usedInAttributes) {
                        ManagedViewType<?> referrerDeclaringType = declaringTypeAttributeReferrer.getDeclaringType();
                        if (declaringTypeAttributeReferrer.isUpdatable() && (referrerDeclaringType.isCreatable() || referrerDeclaringType.isUpdatable())) {
                            return true;
                        }
                    }
                } else if (declaringType instanceof FlatViewType<?>) {
                    // The attribute could be a nested flat view of an id view
                    for (MethodAttribute<?, ?> declaringTypeAttributeReferrer : usedInAttributes) {
                        if (isPartOfUpdatableSubviewId(usageGraph, declaringTypeAttributeReferrer)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean needsDefinitionInInputType(MethodAttribute<?, ?> attribute) {
        // Updatable attributes obviously need to be included in the input type
        return attribute.isUpdatable()
                // Attributes in flat views could be part of an id, which is checked later
                || attribute.getDeclaringType() instanceof FlatViewType<?>
                // If the attribute is an id attribute, also include it
                || attribute instanceof SingularAttribute<?, ?> && ((SingularAttribute<?, ?>) attribute).isId();
    }

    protected void registerManagedViewType(TypeDefinitionRegistry typeRegistry, Map<String, ManagedViewType<?>> typeNameToViewType, ManagedViewType<?> managedView, TypeDefinition<?> objectTypeDefinition) {
        if (isDefineNormalTypes()) {
            typeRegistry.add(objectTypeDefinition);
        }
        ManagedViewType<?> old;
        if ((old = typeNameToViewType.put(objectTypeDefinition.getName(), managedView)) != null) {
            throw new IllegalArgumentException("Type with name '" + objectTypeDefinition.getName() + "' is registered multiple times: [" + old.getEntityClass().getName() + ", " + managedView.getJavaType().getName() + "]!");
        }
    }

    /**
     * Return the GraphQL id type for the given singular attribute.
     *
     * @param typeRegistry The type registry
     * @param singularAttribute The singular attribute
     * @return The type
     */
    protected Type getIdType(TypeDefinitionRegistry typeRegistry, SingularAttribute<?, ?> singularAttribute) {
        return new NonNullType(new TypeName("ID"));
    }

    /**
     * Return the GraphQL id type for the given singular attribute.
     *
     * @param typeRegistry The type registry
     * @param singularAttribute The singular attribute
     * @return The type
     */
    protected Type getInputIdType(TypeDefinitionRegistry typeRegistry, SingularAttribute<?, ?> singularAttribute) {
        // Ideally, we would make this only nullable if the value is generated, but that's hard to determine
        return new TypeName("ID");
    }

    /**
     * Return the GraphQL id type for the given singular attribute.
     *
     * @param singularAttribute The singular attribute
     * @return The type
     */
    protected GraphQLOutputType getIdType(GraphQLSchema.Builder schemaBuilder, SingularAttribute<?, ?> singularAttribute, Map<Class<?>, String> registeredTypeNames) {
        if (scalarTypeMap != null) {
            return new GraphQLNonNull(scalarTypeMap.get("ID"));
        }
        return new GraphQLNonNull(new GraphQLTypeReference("ID"));
    }

    /**
     * Return the GraphQL id type for the given singular attribute.
     *
     * @param singularAttribute The singular attribute
     * @return The type
     */
    protected GraphQLInputType getInputIdType(GraphQLSchema.Builder schemaBuilder, SingularAttribute<?, ?> singularAttribute, Map<Class<?>, String> registeredTypeNames) {
        // Ideally, we would make this only nullable if the value is generated, but that's hard to determine
        if (scalarTypeMap != null) {
            return scalarTypeMap.get("ID");
        }
        return new GraphQLTypeReference("ID");
    }

    /**
     * Return the GraphQL id type for the given singular attribute.
     *
     * @param singularAttribute The singular attribute
     * @return The type
     * @deprecated Use {@link #getIdType(GraphQLSchema.Builder, SingularAttribute, Map)} instead
     */
    protected GraphQLOutputType getIdType(SingularAttribute<?, ?> singularAttribute) {
        if (scalarTypeMap != null) {
            return new GraphQLNonNull(scalarTypeMap.get("ID"));
        }
        return new GraphQLNonNull(new GraphQLTypeReference("ID"));
    }

    /**
     * Return the GraphQL id type for the given singular attribute.
     *
     * @param singularAttribute The singular attribute
     * @return The type
     * @deprecated Use {@link #getInputIdType(GraphQLSchema.Builder, SingularAttribute, Map)} instead
     */
    protected GraphQLInputType getInputIdType(SingularAttribute<?, ?> singularAttribute) {
        // Ideally, we would make this only nullable if the value is generated, but that's hard to determine
        if (scalarTypeMap != null) {
            return scalarTypeMap.get("ID");
        }
        return new GraphQLTypeReference("ID");
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param typeRegistry The type registry
     * @param attribute The map attribute
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected Type getEntryType(TypeDefinitionRegistry typeRegistry, MethodAttribute<?, ?> attribute, Type key, Type value) {
        if (key == null || value == null) {
            return null;
        }
        return getEntryType(typeRegistry, getObjectTypeName(attribute.getDeclaringType()), attribute.getName(), key, value);
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param typeRegistry The type registry
     * @param typeName The declaring type name
     * @param fieldName The map attribute field name
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected Type getEntryType(TypeDefinitionRegistry typeRegistry, String typeName, String fieldName, Type key, Type value) {
        if (key == null || value == null) {
            return null;
        }
        String entryName = typeName + StringUtils.firstToUpper(fieldName) + "Entry";
        List<FieldDefinition> fields = new ArrayList<>();
        fields.add(new FieldDefinition("key", key));
        fields.add(new FieldDefinition("value", value));
        if (isDefineNormalTypes()) {
            typeRegistry.add(newObjectTypeDefinition(entryName, fields, null));
        }
        return new NonNullType(new ListType(new NonNullType(new TypeName(entryName))));
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param typeRegistry The type registry
     * @param attribute The map attribute
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected Type getInputEntryType(TypeDefinitionRegistry typeRegistry, MethodAttribute<?, ?> attribute, Type key, Type value) {
        if (key == null || value == null) {
            return null;
        }
        Type type = getInputEntryType(typeRegistry, getObjectTypeName(attribute.getDeclaringType()), attribute.getName(), key, value);
        if (type != null && isNotNull(attribute.getJavaMethod())) {
            type = makeNonNull(type);
        }
        return type;
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param typeRegistry The type registry
     * @param typeName The declaring type name
     * @param fieldName The map attribute field name
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected Type getInputEntryType(TypeDefinitionRegistry typeRegistry, String typeName, String fieldName, Type key, Type value) {
        if (key == null || value == null) {
            return null;
        }
        String entryName = typeName + StringUtils.firstToUpper(fieldName) + "EntryInput";
        List<FieldDefinition> fields = new ArrayList<>();
        fields.add(new FieldDefinition("key", key));
        fields.add(new FieldDefinition("value", value));
        if (isDefineNormalTypes()) {
            typeRegistry.add(newObjectTypeDefinition(entryName, fields, null));
        }
        return new ListType(new NonNullType(new TypeName(entryName)));
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param schemaBuilder The schema builder
     * @param attribute The map attribute
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected GraphQLOutputType getEntryType(GraphQLSchema.Builder schemaBuilder, MethodAttribute<?, ?> attribute, GraphQLOutputType key, GraphQLOutputType value) {
        if (key == null || value == null) {
            return null;
        }
        return getEntryType(schemaBuilder, getObjectTypeName(attribute.getDeclaringType()), attribute.getName(), key, value);
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param schemaBuilder The schema builder
     * @param typeName The declaring type name
     * @param fieldName The map attribute field name
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected GraphQLOutputType getEntryType(GraphQLSchema.Builder schemaBuilder, String typeName, String fieldName, GraphQLOutputType key, GraphQLOutputType value) {
        if (key == null || value == null) {
            return null;
        }
        String entryName = typeName + StringUtils.firstToUpper(fieldName) + "Entry";
        GraphQLObjectType type = GraphQLObjectType.newObject().name(entryName)
            .field(GraphQLFieldDefinition.newFieldDefinition().name("key").type(key))
            .field(GraphQLFieldDefinition.newFieldDefinition().name("value").type(value))
            .build();
        if (isDefineNormalTypes()) {
            schemaBuilder.additionalType(type);
        }
        return new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference(entryName))));
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param schemaBuilder The schema builder
     * @param attribute The map attribute
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected GraphQLInputType getInputEntryType(GraphQLSchema.Builder schemaBuilder, MethodAttribute<?, ?> attribute, GraphQLInputType key, GraphQLInputType value) {
        if (key == null || value == null) {
            return null;
        }
        GraphQLInputType type = getInputEntryType(schemaBuilder, getObjectTypeName(attribute.getDeclaringType()), attribute.getName(), key, value);
        if (type != null && isNotNull(attribute.getJavaMethod())) {
            type = makeNonNull(type);
        }
        return type;
    }

    /**
     * Return the GraphQL entry type for the given map attribute with the given key and value types.
     *
     * @param schemaBuilder The schema builder
     * @param typeName The declaring type name
     * @param fieldName The map attribute field name
     * @param key The key type
     * @param value The value type
     * @return The type
     */
    protected GraphQLInputType getInputEntryType(GraphQLSchema.Builder schemaBuilder, String typeName, String fieldName, GraphQLInputType key, GraphQLInputType value) {
        if (key == null || value == null) {
            return null;
        }
        String entryName = typeName + StringUtils.firstToUpper(fieldName) + "EntryInput";
        GraphQLInputObjectType type = GraphQLInputObjectType.newInputObject().name(entryName)
            .field(GraphQLInputObjectField.newInputObjectField().name("key").type(key))
            .field(GraphQLInputObjectField.newInputObjectField().name("value").type(value))
            .build();
        if (isDefineNormalTypes()) {
            schemaBuilder.additionalType(type);
        }
        return new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference(entryName)));
    }

    /**
     * Returns the GraphQL type name for the given managed view type.
     *
     * @param type The managed view type
     * @return The GraphQL type name
     */
    protected String getObjectTypeName(ManagedViewType type) {
        return getObjectTypeName(type.getJavaType());
    }

    /**
     * Returns the GraphQL type name for the given managed view type java type.
     *
     * @param javaType The managed view type java type
     * @return The GraphQL type name
     */
    protected String getObjectTypeName(Class<?> javaType) {
        //CHECKSTYLE:OFF: MissingSwitchDefault
        for (Annotation annotation : javaType.getAnnotations()) {
            switch (annotation.annotationType().getName()) {
                case "com.blazebit.persistence.integration.graphql.GraphQLName":
                case "org.eclipse.microprofile.graphql.Name":
                case "org.eclipse.microprofile.graphql.Type":
                    return getAnnotationValue(annotation, "value");
                case "io.leangen.graphql.annotations.types.GraphQLType":
                case "io.leangen.graphql.annotations.types.GraphQLInterface":
                case "io.leangen.graphql.annotations.types.GraphQLUnion":
                    return getAnnotationValue(annotation, "name");
            }
        }
        //CHECKSTYLE:ON: MissingSwitchDefault
        return javaType.getSimpleName();
    }

    /**
     * Returns the GraphQL input type name for the given managed view type.
     *
     * @param managedView The managed view type
     * @return The GraphQL type name
     */
    protected String getInputObjectTypeName(ManagedViewType managedView) {
        String typeName = getObjectTypeName(managedView);
        // So far, we only use this for MicroProfile GraphQL where we can't register custom types
        // and instead have to simply use the name the MP GraphQL implementations choose for such types.
        // In case of input object types, implementations don't suffix the name with "Input" since the type is abstract
        if (Modifier.isAbstract(managedView.getJavaType().getModifiers()) && (managedView.isCreatable() || managedView.isUpdatable())) {
            return typeName;
        } else {
            return typeName + "Input";
        }
    }

    /**
     * Returns the GraphQL input type name for the given managed view java type.
     *
     * @param managedViewJavaType The managed view java type
     * @return The GraphQL type name
     */
    protected String getInputObjectTypeName(Class<?> managedViewJavaType) {
        String typeName = getObjectTypeName(managedViewJavaType);
        // So far, we only use this for MicroProfile GraphQL where we can't register custom types
        // and instead have to simply use the name the MP GraphQL implementations choose for such types.
        // In case of input object types, implementations don't suffix the name with "Input" since the type is abstract
        if (Modifier.isAbstract(managedViewJavaType.getModifiers()) && (AnnotationUtils.findAnnotation(managedViewJavaType, CreatableEntityView.class) != null || AnnotationUtils.findAnnotation(managedViewJavaType, UpdatableEntityView.class) != null)) {
            return typeName;
        } else {
            return typeName + "Input";
        }
    }

    /**
     * Returns the GraphQL type name for the given java type.
     *
     * @param type The java type
     * @return The GraphQL type name
     */
    protected String getTypeName(Class<?> type) {
        //CHECKSTYLE:OFF: MissingSwitchDefault
        for (Annotation annotation : type.getAnnotations()) {
            switch (annotation.annotationType().getName()) {
                case "com.blazebit.persistence.integration.graphql.GraphQLName":
                case "org.eclipse.microprofile.graphql.Name":
                case "org.eclipse.microprofile.graphql.Type":
                    return getAnnotationValue(annotation, "value");
                case "io.leangen.graphql.annotations.types.GraphQLType":
                case "io.leangen.graphql.annotations.types.GraphQLInterface":
                case "io.leangen.graphql.annotations.types.GraphQLUnion":
                    return getAnnotationValue(annotation, "name");
                case "org.eclipse.microprofile.graphql.Enum":
                    if (type.isEnum()) {
                        return getAnnotationValue(annotation, "value");
                    }
            }
        }
        //CHECKSTYLE:ON: MissingSwitchDefault
        return type.getSimpleName();
    }

    protected String getExplicitFieldName(Method method) {
        //CHECKSTYLE:OFF: MissingSwitchDefault
        for (Annotation annotation : method.getAnnotations()) {
            switch (annotation.annotationType().getName()) {
                case "com.blazebit.persistence.integration.graphql.GraphQLName":
                case "org.eclipse.microprofile.graphql.Query":
                case "org.eclipse.microprofile.graphql.Name":
                    return getAnnotationValue(annotation, "value");
                case "com.netflix.graphql.dgs.DgsData":
                    return getAnnotationValue(annotation, "field");
                case "io.leangen.graphql.annotations.GraphQLQuery":
                    return getAnnotationValue(annotation, "name");
            }
        }
        return null;
    }

    protected String getFieldName(Method method) {
        String explicitFieldName = getExplicitFieldName(method);
        if (explicitFieldName != null && !explicitFieldName.isEmpty()) {
            return explicitFieldName;
        }
        String methodName = method.getName();
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return methodName;
    }

    /**
     * Returns the GraphQL field name for the given attribute.
     *
     * @param attribute The attribute
     * @return The GraphQL field name
     */
    protected String getFieldName(MethodAttribute<?, ?> attribute) {
        String explicitFieldName = getExplicitFieldName(attribute.getJavaMethod());
        if (explicitFieldName != null && !explicitFieldName.isEmpty()) {
            return explicitFieldName;
        }
        return attribute.getName();
    }

    /**
     * Returns the GraphQL description for the given java type.
     *
     * @param type The java type
     * @return The GraphQL type description
     */
    protected String getDescription(Class<?> type) {
        //CHECKSTYLE:OFF: MissingSwitchDefault
        for (Annotation annotation : type.getAnnotations()) {
            switch (annotation.annotationType().getName()) {
                case "org.eclipse.microprofile.graphql.Description":
                    return getAnnotationValue(annotation, "value");
                case "io.leangen.graphql.annotations.types.GraphQLType":
                case "io.leangen.graphql.annotations.types.GraphQLInterface":
                case "io.leangen.graphql.annotations.types.GraphQLUnion":
                    return getAnnotationValue(annotation, "description");
            }
        }
        //CHECKSTYLE:ON: MissingSwitchDefault
        return null;
    }

    /**
     * Returns whether the GraphQL type for the class should be ignored.
     *
     * @param javaType The java type
     * @return Whether the type should be ignored
     */
    protected boolean isIgnored(Class<?> javaType) {
        //CHECKSTYLE:OFF: MissingSwitchDefault
        for (Annotation annotation : javaType.getAnnotations()) {
            switch (annotation.annotationType().getName()) {
                case "com.blazebit.persistence.integration.graphql.GraphQLIgnore":
                case "io.leangen.graphql.annotations.GraphQLIgnore":
                    return true;
            }
        }
        //CHECKSTYLE:ON: MissingSwitchDefault
        return false;
    }

    /**
     * Returns whether the GraphQL field for the method should be ignored.
     *
     * @param javaMethod The java method
     * @return Whether the field should be ignored
     */
    protected boolean isIgnored(Method javaMethod) {
        //CHECKSTYLE:OFF: MissingSwitchDefault
        for (Annotation annotation : javaMethod.getAnnotations()) {
            switch (annotation.annotationType().getName()) {
                case "com.blazebit.persistence.integration.graphql.GraphQLIgnore":
                case "org.eclipse.microprofile.graphql.Ignore":
                case "io.leangen.graphql.annotations.GraphQLIgnore":
                    return true;
            }
        }
        //CHECKSTYLE:ON: MissingSwitchDefault
        return false;
    }

    /**
     * Return the GraphQL type for the given managed view type.
     *
     * @param type The managed view type
     * @return The type
     */
    protected Type getObjectType(ManagedViewType type) {
        if (isIgnored(type.getJavaType())) {
            return null;
        }
        return new TypeName(getObjectTypeName(type));
    }

    /**
     * Return the GraphQL type for the given managed view type.
     *
     * @param type The managed view type
     * @return The type
     */
    protected Type getInputObjectType(ManagedViewType type) {
        if (isIgnored(type.getJavaType())) {
            return null;
        }
        return new TypeName(getObjectTypeName(type) + "Input");
    }

    /**
     * Return the GraphQL type for the given managed view type.
     *
     * @param type The managed view type
     * @return The type
     */
    protected GraphQLOutputType getObjectTypeReference(ManagedViewType<?> type) {
        if (isIgnored(type.getJavaType())) {
            return null;
        }
        return new GraphQLTypeReference(getObjectTypeName(type));
    }

    /**
     * Return the GraphQL type for the given managed view type.
     *
     * @param type The managed view type
     * @return The type
     */
    protected GraphQLInputType getInputObjectTypeReference(ManagedViewType<?> type) {
        if (isIgnored(type.getJavaType())) {
            return null;
        }
        return new GraphQLTypeReference(getInputObjectTypeName(type));
    }

    /**
     * Return the GraphQL type for the given singular attribute.
     *
     * @param typeRegistry The type registry
     * @param singularAttribute The singular attribute
     * @param entityMetamodel The entity metamodel
     * @return The type
     */
    protected Type getElementType(TypeDefinitionRegistry typeRegistry, SingularAttribute<?, ?> singularAttribute, EntityMetamodel entityMetamodel) {
        com.blazebit.persistence.view.metamodel.Type elementType = singularAttribute.getType();
        Type type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            if (Collection.class.isAssignableFrom(elementType.getJavaType())) {
                type = getScalarType(typeRegistry, singularAttribute.getDeclaringType().getJavaType(), ((MethodAttribute<?, ?>) singularAttribute).getJavaMethod().getGenericReturnType());
            } else {
                type = getScalarType(typeRegistry, elementType.getJavaType());
            }
        } else {
            type = getObjectType((ManagedViewType<?>) elementType);
        }
        if (type != null && (singularAttribute.isId() || isNotNull(singularAttribute, entityMetamodel))) {
            type = makeNonNull(type);
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given singular attribute.
     *
     * @param typeRegistry The type registry
     * @param singularAttribute The singular attribute
     * @param entityMetamodel The entity metamodel
     * @return The type
     */
    protected Type getInputElementType(TypeDefinitionRegistry typeRegistry, SingularAttribute<?, ?> singularAttribute, EntityMetamodel entityMetamodel) {
        com.blazebit.persistence.view.metamodel.Type elementType = singularAttribute.getType();
        Type type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            if (Collection.class.isAssignableFrom(elementType.getJavaType())) {
                type = getScalarType(typeRegistry, singularAttribute.getDeclaringType().getJavaType(), ((MethodAttribute<?, ?>) singularAttribute).getJavaMethod().getGenericReturnType());
            } else {
                type = getScalarType(typeRegistry, elementType.getJavaType());
            }
        } else {
            type = getInputObjectType((ManagedViewType<?>) elementType);
        }
        if (type != null && (singularAttribute.isId() || isNotNull(((MethodAttribute<?, ?>) singularAttribute).getJavaMethod()))) {
            type = makeNonNull(type);
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param typeRegistry The type registry
     * @param pluralAttribute The plural attribute
     * @return The type
     */
    protected Type getElementType(TypeDefinitionRegistry typeRegistry, PluralAttribute<?, ?, ?> pluralAttribute) {
        com.blazebit.persistence.view.metamodel.Type elementType = pluralAttribute.getElementType();
        Type type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = getScalarType(typeRegistry, elementType.getJavaType());
        } else {
            type = getObjectType((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) pluralAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[annotatedArgumentTypes.length - 1].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, annotatedArgumentTypes.length - 1))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }


    private static AnnotatedType[] getReturnTypeArgumentAnnotations(Method method) {
        AnnotatedType genericReturnType = method.getAnnotatedReturnType();
        if (genericReturnType instanceof AnnotatedParameterizedType) {
            AnnotatedParameterizedType parameterizedType = (AnnotatedParameterizedType) genericReturnType;
            return parameterizedType.getAnnotatedActualTypeArguments();
        }
        return new AnnotatedType[0];
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param typeRegistry The type registry
     * @param evm The entity view manager
     * @param elementType The map element type
     * @return The type
     */
    protected Type getElementType(TypeDefinitionRegistry typeRegistry, EntityViewManager evm, Class<?> elementType) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(elementType);
        if (managedViewType == null) {
            return getScalarType(typeRegistry, elementType);
        } else {
            return getObjectType(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param typeRegistry The type registry
     * @param pluralAttribute The plural attribute
     * @return The type
     */
    protected Type getInputElementType(TypeDefinitionRegistry typeRegistry, PluralAttribute<?, ?, ?> pluralAttribute) {
        com.blazebit.persistence.view.metamodel.Type elementType = pluralAttribute.getElementType();
        Type type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = getScalarType(typeRegistry, elementType.getJavaType());
        } else {
            type = getInputObjectType((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) pluralAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[annotatedArgumentTypes.length - 1].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, annotatedArgumentTypes.length - 1))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param typeRegistry The type registry
     * @param evm The entity view manager
     * @param elementType The map element type
     * @return The type
     */
    protected Type getInputElementType(TypeDefinitionRegistry typeRegistry, EntityViewManager evm, Class<?> elementType) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(elementType);
        if (managedViewType == null) {
            return getScalarType(typeRegistry, elementType);
        } else {
            return getInputObjectType(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the given singular attribute.
     *
     * @param schemaBuilder The schema builder
     * @param singularAttribute The singular attribute
     * @param entityMetamodel The entity metamodel
     * @return The type
     */
    protected GraphQLOutputType getElementType(GraphQLSchema.Builder schemaBuilder, SingularAttribute<?, ?> singularAttribute, Map<Class<?>, String> registeredTypeNames, EntityMetamodel entityMetamodel) {
        com.blazebit.persistence.view.metamodel.Type<?> elementType = singularAttribute.getType();
        GraphQLOutputType type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            if (Collection.class.isAssignableFrom(elementType.getJavaType())) {
                type = getScalarType(schemaBuilder, singularAttribute.getDeclaringType().getJavaType(), ((MethodAttribute<?, ?>) singularAttribute).getJavaMethod().getGenericReturnType(), registeredTypeNames);
            } else {
                type = getScalarType(schemaBuilder, elementType.getJavaType(), registeredTypeNames);
            }
        } else {
            type = getObjectTypeReference((ManagedViewType<?>) elementType);
        }
        if (type != null && (singularAttribute.isId() || isNotNull(singularAttribute, entityMetamodel))) {
            type = makeNonNull(type);
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given singular attribute.
     *
     * @param schemaBuilder The schema builder
     * @param singularAttribute The singular attribute
     * @param entityMetamodel The entity metamodel
     * @return The type
     */
    protected GraphQLInputType getInputElementType(GraphQLSchema.Builder schemaBuilder, SingularAttribute<?, ?> singularAttribute, Map<Class<?>, String> registeredTypeNames, EntityMetamodel entityMetamodel) {
        com.blazebit.persistence.view.metamodel.Type<?> elementType = singularAttribute.getType();
        GraphQLInputType type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            if (Collection.class.isAssignableFrom(elementType.getJavaType())) {
                type = (GraphQLInputType) getScalarType(schemaBuilder, singularAttribute.getDeclaringType().getJavaType(), ((MethodAttribute<?, ?>) singularAttribute).getJavaMethod().getGenericReturnType(), registeredTypeNames);
            } else {
                type = (GraphQLInputType) getScalarType(schemaBuilder, elementType.getJavaType(), registeredTypeNames);
            }
        } else {
            type = getInputObjectTypeReference((ManagedViewType<?>) elementType);
        }
        if (type != null && (singularAttribute.isId() || isNotNull(((MethodAttribute<?, ?>) singularAttribute).getJavaMethod()))) {
            type = makeNonNull(type);
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param schemaBuilder The schema builder
     * @param pluralAttribute The plural attribute
     * @return The type
     */
    protected GraphQLOutputType getElementType(GraphQLSchema.Builder schemaBuilder, PluralAttribute<?, ?, ?> pluralAttribute, Map<Class<?>, String> registeredTypeNames) {
        com.blazebit.persistence.view.metamodel.Type<?> elementType = pluralAttribute.getElementType();
        GraphQLOutputType type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = getScalarType(schemaBuilder, elementType.getJavaType(), registeredTypeNames);
        } else {
            type = getObjectTypeReference((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) pluralAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[annotatedArgumentTypes.length - 1].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, annotatedArgumentTypes.length - 1))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param schemaBuilder The schema builder
     * @param evm The entity view manager
     * @param elementType The element type
     * @return The type
     */
    protected GraphQLOutputType getElementType(GraphQLSchema.Builder schemaBuilder, EntityViewManager evm, Class<?> elementType, Map<Class<?>, String> registeredTypeNames) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(elementType);
        if (managedViewType == null) {
            return getScalarType(schemaBuilder, elementType, registeredTypeNames);
        } else {
            return getObjectTypeReference(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param schemaBuilder The schema builder
     * @param pluralAttribute The plural attribute
     * @return The type
     */
    protected GraphQLInputType getInputElementType(GraphQLSchema.Builder schemaBuilder, PluralAttribute<?, ?, ?> pluralAttribute, Map<Class<?>, String> registeredTypeNames) {
        com.blazebit.persistence.view.metamodel.Type<?> elementType = pluralAttribute.getElementType();
        GraphQLInputType type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = (GraphQLInputType) getScalarType(schemaBuilder, elementType.getJavaType(), registeredTypeNames);
        } else {
            type = getInputObjectTypeReference((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) pluralAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[annotatedArgumentTypes.length - 1].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, annotatedArgumentTypes.length - 1))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the given plural attribute.
     *
     * @param schemaBuilder The schema builder
     * @param evm The entity view manager
     * @param elementType The element type
     * @return The type
     */
    protected GraphQLInputType getInputElementType(GraphQLSchema.Builder schemaBuilder, EntityViewManager evm, Class<?> elementType, Map<Class<?>, String> registeredTypeNames) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(elementType);
        if (managedViewType == null) {
            return (GraphQLInputType) getScalarType(schemaBuilder, elementType, registeredTypeNames);
        } else {
            return getInputObjectTypeReference(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param typeRegistry The type registry
     * @param mapAttribute The map attribute
     * @return The type
     */
    protected Type getKeyType(TypeDefinitionRegistry typeRegistry, MapAttribute<?, ?, ?> mapAttribute) {
        com.blazebit.persistence.view.metamodel.Type elementType = mapAttribute.getKeyType();
        Type type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = getScalarType(typeRegistry, elementType.getJavaType());
        } else {
            type = getObjectType((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) mapAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param typeRegistry The type registry
     * @param evm The entity view manager
     * @param keyType The map key type
     * @return The type
     */
    protected Type getKeyType(TypeDefinitionRegistry typeRegistry, EntityViewManager evm, Class<?> keyType) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(keyType);
        if (managedViewType == null) {
            return getScalarType(typeRegistry, keyType);
        } else {
            return getObjectType(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param typeRegistry The type registry
     * @param mapAttribute The map attribute
     * @return The type
     */
    protected Type getInputKeyType(TypeDefinitionRegistry typeRegistry, MapAttribute<?, ?, ?> mapAttribute) {
        com.blazebit.persistence.view.metamodel.Type elementType = mapAttribute.getKeyType();
        Type type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = getScalarType(typeRegistry, elementType.getJavaType());
        } else {
            type = getInputObjectType((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) mapAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param typeRegistry The type registry
     * @param evm The entity view manager
     * @param keyType The map key type
     * @return The type
     */
    protected Type getInputKeyType(TypeDefinitionRegistry typeRegistry, EntityViewManager evm, Class<?> keyType) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(keyType);
        if (managedViewType == null) {
            return getScalarType(typeRegistry, keyType);
        } else {
            return getInputObjectType(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param schemaBuilder The schema builder
     * @param mapAttribute The map attribute
     * @return The type
     */
    protected GraphQLOutputType getKeyType(GraphQLSchema.Builder schemaBuilder, MapAttribute<?, ?, ?> mapAttribute, Map<Class<?>, String> registeredTypeNames) {
        com.blazebit.persistence.view.metamodel.Type<?> elementType = mapAttribute.getKeyType();
        GraphQLOutputType type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = getScalarType(schemaBuilder, elementType.getJavaType(), registeredTypeNames);
        } else {
            type = getObjectTypeReference((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) mapAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param schemaBuilder The schema builder
     * @param evm The entity view manager
     * @param keyType The map key type
     * @return The type
     */
    protected GraphQLOutputType getKeyType(GraphQLSchema.Builder schemaBuilder, EntityViewManager evm, Class<?> keyType, Map<Class<?>, String> registeredTypeNames) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(keyType);
        if (managedViewType == null) {
            return getScalarType(schemaBuilder, keyType, registeredTypeNames);
        } else {
            return getObjectTypeReference(managedViewType);
        }
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param schemaBuilder The schema builder
     * @param mapAttribute The map attribute
     * @return The type
     */
    protected GraphQLInputType getInputKeyType(GraphQLSchema.Builder schemaBuilder, MapAttribute<?, ?, ?> mapAttribute, Map<Class<?>, String> registeredTypeNames) {
        com.blazebit.persistence.view.metamodel.Type<?> elementType = mapAttribute.getKeyType();
        GraphQLInputType type;
        if (elementType.getMappingType() == com.blazebit.persistence.view.metamodel.Type.MappingType.BASIC) {
            type = (GraphQLInputType) getScalarType(schemaBuilder, elementType.getJavaType(), registeredTypeNames);
        } else {
            type = getInputObjectTypeReference((ManagedViewType<?>) elementType);
        }
        Method method = ((MethodAttribute<?, ?>) mapAttribute).getJavaMethod();
        if (type != null) {
            AnnotatedType[] annotatedArgumentTypes = getReturnTypeArgumentAnnotations(method);
            if (annotatedArgumentTypes.length != 0 && (isNotNull(annotatedArgumentTypes[0].getAnnotations()) || KotlinSupport.isKotlinTypeArgumentNotNull(method, 0))) {
                type = makeNonNull(type);
            }
        }
        return type;
    }

    /**
     * Return the GraphQL type for the key of the given map attribute.
     *
     * @param schemaBuilder The schema builder
     * @param evm The entity view manager
     * @param keyType The map key type
     * @return The type
     */
    protected GraphQLInputType getInputKeyType(GraphQLSchema.Builder schemaBuilder, EntityViewManager evm, Class<?> keyType, Map<Class<?>, String> registeredTypeNames) {
        ManagedViewType<?> managedViewType = evm.getMetamodel().managedView(keyType);
        if (managedViewType == null) {
            return (GraphQLInputType) getScalarType(schemaBuilder, keyType, registeredTypeNames);
        } else {
            return getInputObjectTypeReference(managedViewType);
        }
    }

    protected GraphQLOutputType getScalarType(String typeName) {
        if (scalarTypeMap != null) {
            GraphQLScalarType scalarType = scalarTypeMap.get(typeName);
            if (scalarType != null) {
                return scalarType;
            }
        }
        return new GraphQLTypeReference(typeName);
    }

    /**
     * Return the GraphQL type for the given scalar java type.
     *
     * @param typeRegistry The type registry
     * @param ownerType The owner of the java type
     * @param javaType The java type
     * @return The type
     */
    protected Type getScalarType(TypeDefinitionRegistry typeRegistry, Class<?> ownerType, java.lang.reflect.Type javaType) {
        if (javaType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) javaType;
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom(rawType)) {
                Class<?> elementType = ReflectionUtils.resolveType(ownerType, parameterizedType.getActualTypeArguments()[0]);
                return getListType(getScalarType(typeRegistry, elementType));
            } else {
                javaType = rawType;
            }
        }
        if (javaType instanceof Class<?>) {
            return getScalarType(typeRegistry, (Class<?>) javaType);
        } else {
            throw new IllegalArgumentException("Unsupported scalar type: " + javaType);
        }
    }

    /**
     * Return the GraphQL type for the given scalar java type.
     *
     * @param typeRegistry The type registry
     * @param javaType The java type
     * @return The type
     */
    protected Type getScalarType(TypeDefinitionRegistry typeRegistry, Class<?> javaType) {
        String typeName = SUPPORTED_TYPES.get(javaType);
        if (typeName == null) {
            if (javaType.isEnum()) {
                typeName = getTypeName(javaType);
                if (!typeRegistry.getType(typeName).isPresent()) {
                    List<EnumValueDefinition> enumValueDefinitions = new ArrayList<>();
                    for (Enum<?> enumConstant : (Enum<?>[]) javaType.getEnumConstants()) {
                        enumValueDefinitions.add(new EnumValueDefinition(enumConstant.name(), new ArrayList<>(0)));
                    }

                    if (isDefineNormalTypes()) {
                        typeRegistry.add(newEnumTypeDefinition(typeName, enumValueDefinitions, getDescription(javaType)));
                    }
                }
            } else {
                typeName = "String";
            }
        }
        if (!javaType.isEnum() && registeredScalarTypeNames != null && registeredScalarTypeNames.add(typeName)) {
            typeRegistry.add(new ScalarTypeDefinition(typeName));
        }
        return new TypeName(typeName);
    }

    /**
     * Return the GraphQL type for the given scalar java type.
     *
     * @param schemaBuilder The schema builder
     * @param ownerType The owner of the java type
     * @param javaType The java type
     * @param registeredTypeNames The registered type names
     * @return The type
     */
    protected GraphQLOutputType getScalarType(GraphQLSchema.Builder schemaBuilder, Class<?> ownerType, java.lang.reflect.Type javaType, Map<Class<?>, String> registeredTypeNames) {
        if (javaType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) javaType;
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom(rawType)) {
                Class<?> elementType = ReflectionUtils.resolveType(ownerType, parameterizedType.getActualTypeArguments()[0]);
                return getListType(getScalarType(schemaBuilder, elementType, registeredTypeNames));
            } else {
                javaType = rawType;
            }
        }
        if (javaType instanceof Class<?>) {
            return getScalarType(schemaBuilder, (Class<?>) javaType, registeredTypeNames);
        } else {
            throw new IllegalArgumentException("Unsupported scalar type: " + javaType);
        }
    }

    /**
     * Return the GraphQL type for the given scalar java type.
     *
     * @param schemaBuilder The schema builder
     * @param javaType The java type
     * @param registeredTypeNames The registered type names
     * @return The type
     */
    protected GraphQLOutputType getScalarType(GraphQLSchema.Builder schemaBuilder, Class<?> javaType, Map<Class<?>, String> registeredTypeNames) {
        if (scalarTypeMap != null) {
            GraphQLScalarType scalarType = scalarTypeMap.get(javaType.getName());
            if (scalarType != null) {
                return scalarType;
            }
        }
        String typeName = SUPPORTED_TYPES.get(javaType);
        if (typeName == null) {
            if (javaType.isEnum()) {
                typeName = getTypeName(javaType);
                if (!registeredTypeNames.containsKey(javaType)) {
                    GraphQLEnumType.Builder enumBuilder = GraphQLEnumType.newEnum().name(typeName);
                    for (Enum<?> enumConstant : (Enum<?>[]) javaType.getEnumConstants()) {
                        enumBuilder.value(enumConstant.name());
                    }

                    if (isDefineNormalTypes()) {
                        schemaBuilder.additionalType(enumBuilder.build());
                    }
                    registeredTypeNames.put(javaType, typeName);
                }
            } else {
                typeName = "String";
            }
        }
        if (!javaType.isEnum() && registeredScalarTypeNames != null && registeredScalarTypeNames.add(typeName)) {
            schemaBuilder.additionalType(GraphQLScalarType.newScalar().name(typeName).build());
        }
        return new GraphQLTypeReference(typeName);
    }

    /**
     * Returns whether the GraphQL type for the singular attribute should be non-null.
     *
     * @param attribute The attribute
     * @param entityMetamodel The entity metamodel
     * @return Whether the type should be non-null
     */
    protected boolean isNotNull(SingularAttribute<?, ?> attribute, EntityMetamodel entityMetamodel) {
        if (attribute instanceof MappingAttribute<?, ?> && !attribute.isQueryParameter()) {
            AbstractAttribute<?, ?> attr = (AbstractAttribute<?, ?>) attribute;
            Map<String, javax.persistence.metamodel.Type<?>> rootTypes = attr.getDeclaringType().getEntityViewRootTypes();
            if (rootTypes.isEmpty()) {
                rootTypes = Collections.singletonMap("this", attr.getDeclaringType().getJpaManagedType());
            } else {
                rootTypes = new HashMap<>(rootTypes);
                rootTypes.put("this", attr.getDeclaringType().getJpaManagedType());
            }
            if (!ExpressionUtils.isNullable(entityMetamodel, rootTypes, attr.getMappingExpression())) {
                return true;
            }
        }
        return isNotNull(((MethodAttribute<?, ?>) attribute).getJavaMethod());
    }

    /**
     * Returns whether the GraphQL type for the method should be non-null.
     *
     * @param method The method
     * @return Whether the type should be non-null
     * @since 1.6.8
     */
    protected boolean isNotNull(Method method) {
        return method.getReturnType().isPrimitive()
                || KotlinSupport.isKotlinNotNull(method)
                || isNotNull(method.getAnnotations());
    }

    /**
     * Returns whether the GraphQL type based on a set of annotations should be non-null.
     *
     * @param annotations The annotations
     * @return Whether the type should be non-null
     * @since 1.6.8
     */
    protected boolean isNotNull(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            //CHECKSTYLE:OFF: MissingSwitchDefault
            switch (annotation.annotationType().getName()) {
                case "org.eclipse.microprofile.graphql.NonNull":
                case "com.blazebit.persistence.integration.graphql.GraphQLNonNull":
                case "io.leangen.graphql.annotations.GraphQLNonNull":
                // Also respect common NonNull language annotations
                case "javax.validation.constraints.NotNull":
                case "jakarta.validation.constraints.NotNull":
                case "org.springframework.lang.NonNull":
                case "lombok.NonNull":
                    return true;
            }
            //CHECKSTYLE:ON: MissingSwitchDefault
        }
        return false;
    }
}
