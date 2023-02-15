package com.blazebit.persistence.integration.graphql.dgs.converter;

import org.springframework.stereotype.Component;

@Component
public class LongInputIdConverter implements EntityViewInputIdTypeConverter<Long>
{
  @Override
  public boolean supports(final Class<?> clazz)
  {
    return clazz == Long.class;
  }

  @Override
  public Long convert(final String id)
  {
    return Long.parseLong(id);
  }
}
