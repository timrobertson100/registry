package org.gbif.registry.ws.client.collections;

import org.gbif.api.model.collections.CollectionEntity;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.service.collections.CrudService;
import org.gbif.ws.client.BaseWsGetClient;

import java.util.UUID;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.ClientFilter;

/** Base ws client for {@link CollectionEntity}. */
public abstract class BaseCrudClient<T extends CollectionEntity> extends BaseWsGetClient<T, UUID>
    implements CrudService<T> {

  private final GenericType<PagingResponse<T>> pagingType;

  protected BaseCrudClient(
      Class<T> resourceClass,
      WebResource resource,
      @Nullable ClientFilter authFilter,
      GenericType<PagingResponse<T>> pagingType) {
    super(resourceClass, resource, authFilter);
    this.pagingType = pagingType;
  }

  @Override
  public UUID create(@NotNull T entity) {
    return post(UUID.class, entity, "/");
  }

  @Override
  public void delete(@NotNull UUID uuid) {
    delete(String.valueOf(uuid));
  }

  @Override
  public void update(@NotNull T entity) {
    put(entity, String.valueOf(entity.getKey()));
  }
}
