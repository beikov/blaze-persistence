package com.blazebit.persistence.integration.graphql.dgs.converter;

import org.springframework.stereotype.Component;

@Component
public class IntegerInputIdConverter implements EntityViewInputIdTypeConverter<Integer>
{
  @Override
  public boolean supports(final Class<?> clazz)
  {
    return clazz == Integer.class;
  }

  @Override
  public Integer convert(final String id)
  {
    return Integer.parseInt(id);
  }
}
