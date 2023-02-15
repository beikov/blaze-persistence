package com.blazebit.persistence.integration.graphql.dgs.converter;

import org.springframework.stereotype.Component;

@Component
public class StringInputIdConverter implements EntityViewInputIdTypeConverter<String>
{
  @Override
  public boolean supports(final Class<?> clazz)
  {
    return clazz == String.class;
  }

  @Override
  public String convert(final String id)
  {
    return id;
  }
}
