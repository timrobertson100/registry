package org.gbif.registry.search.dataset.occurrence;

import org.gbif.api.service.occurrence.OccurrenceSearchService;
import org.gbif.api.vocabulary.Country;
import org.gbif.common.search.SearchException;
import org.gbif.common.search.solr.SolrConfig;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ws client for {@link OccurrenceSearchService} only exposing the faceted search method.
 * We duplicate part of the regular client class here to avoid circular dependencies.
 */
@Singleton
public class OccSearchClient implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(OccSearchClient.class);

  private final String FIELD_DATASET_KEY = "dataset_key";
  private final String FIELD_TAXON_KEY = "taxon_key";
  private final String FIELD_YEAR= "year";
  private final String FIELD_COUNTRY= "country";
  private final SolrClient solr;

  /**
   * @param occSolr to the occurrence solr collection
   */
  @Inject
  public OccSearchClient(SolrConfig occSolr) {
    solr = occSolr.buildSolr();
  }

  /**
   *  Example query:
   *  http://prodsolr05-vh.gbif.org:8983/solr/occurrence_b/select?wt=json&rows=0&facet=true&facet.field=taxon_key&facet.sort=index&facet.limit=-1&facet.mincount=1&q=dataset_key:821cc27a-e3bb-4bc5-ac34-89ada245069d
   */
  private FacetResult facet(UUID datasetKey, String facetField, Function<String, Integer> solrValueTransformer) {
    SolrQuery query = new SolrQuery();
    query.setQuery(FIELD_DATASET_KEY+":"+datasetKey.toString());
    query.addFacetField(facetField);
    query.setFacetLimit(-1);
    query.setFacetSort("index");
    query.setFacetMinCount(1);
    query.setRows(0);

    try {
      QueryResponse resp = solr.query(query);
      List<Integer> keys = Lists.newArrayList();
      for (FacetField.Count count : resp.getFacetField(facetField).getValues()) {
        keys.add(solrValueTransformer.apply(count.getName()));
      }

      return new FacetResult(resp.getResults().getNumFound(), keys);

    } catch (SolrServerException e) {
      if (e.getRootCause() instanceof IllegalArgumentException) {
        LOG.error("Bad query", e);
        throw (IllegalArgumentException) e.getRootCause();
      } else {
        LOG.error("Error querying solr {}", query, e);
        throw new SearchException(e);
      }

    } catch (IOException e) {
      LOG.error("Error querying solr {}", query, e);
      throw new SearchException(e);
    }
  }

  public static class FacetResult {
    public final long records;
    public final List<Integer> values;

    public FacetResult(long records, List<Integer> values) {
      this.records = records;
      this.values = values;
    }
  }

  public FacetResult taxonKeys(UUID datasetKey) {
    return facet(datasetKey, FIELD_TAXON_KEY, new Function<String, Integer>() {
      @Override
      public Integer apply(@Nullable String taxonKey) {
        return Integer.valueOf(taxonKey);
      }
    });
  }

  public FacetResult years(UUID datasetKey) {
    return facet(datasetKey, FIELD_YEAR, new Function<String, Integer>() {
      @Override
      public Integer apply(@Nullable String year) {
        return Integer.valueOf(year);
      }
    });
  }

  public FacetResult countries(UUID datasetKey) {
    return facet(datasetKey, FIELD_COUNTRY, new Function<String, Integer>() {
      @Override
      // ISO 2 letter codes as input from occ solr index
      public Integer apply(@Nullable String iso) {
        Country c = Country.fromIsoCode(iso);
        if (c == null) {
          LOG.warn("Unknown country ISO code found: {}", iso);
          return Country.UNKNOWN.ordinal();
        } else {
          return c.ordinal();
        }
      }
    });
  }

  @Override
  public void close() throws Exception {
    solr.close();
  }
}
