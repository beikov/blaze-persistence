package com.blazebit.persistence.integration.graphql.dgs.converter;

public interface EntityViewInputIdTypeConverter<T>
{
  boolean supports(Class<?> clazz);

  T convert(String id);
}
