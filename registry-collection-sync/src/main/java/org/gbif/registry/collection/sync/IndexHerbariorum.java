package org.gbif.registry.collection.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import okhttp3.OkHttpClient;
import org.gbif.api.model.collections.Institution;
import org.gbif.api.model.common.paging.Pageable;
import org.gbif.api.model.common.paging.PagingRequest;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.service.collections.InstitutionService;
import org.gbif.registry.ws.client.guice.RegistryWsClientModule;
import org.gbif.ws.client.guice.AnonymousAuthModule;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class IndexHerbariorum {

  public static void main(String[] args) throws Exception {
    List<IndexHerbariorumAPI.Institution> ih = getIH();
    List<org.gbif.api.model.collections.Institution> grSciColl = GrSciCollAPI.list();


    // for all records in IH see if we can find them in GrSciColl by code and/or name.
    // this is a quick hack...

    int count = 0;
    int codeMatched = 0;
    int codeAndNameMatched = 0;
    int nameOnlyMatched = 0;
    int multipleMatched = 0;

    for (IndexHerbariorumAPI.Institution ihRecord : ih) {

      System.out.format("%d: %s\n", count++, ihRecord.organization);

      boolean matched = false;
      for (Institution  grSciCollRecord : grSciColl) {

        if (matchCode(grSciCollRecord.getCode(), ihRecord.getCode())) {

          if (matchName(grSciCollRecord.getName(), ihRecord.getOrganization())) {
            if (matched) multipleMatched++;
            codeAndNameMatched++;
          } else {
            if (matched) multipleMatched++;
            codeMatched++;
          }
          matched = true;

        } else if (matchName(grSciCollRecord.getName(), ihRecord.getOrganization())) {
          if (matched) multipleMatched++;
          nameOnlyMatched++;
          matched = true;

        }
      }
    }

    System.out.format("Code only[%d], codeAndName[%d], nameOnly[%d], multiple[%d] from %d\n",
      codeMatched, codeAndNameMatched, nameOnlyMatched, multipleMatched, ih.size());
  }

  static boolean matchCode(String s1, String s2) {
    return (s1 != null && (s1.equalsIgnoreCase(s2) ||
      s1.replaceFirst("IH:", "").equalsIgnoreCase(s2.replaceFirst("IH:", ""))));
  }

  static boolean matchName(String s1, String s2) {
    return (s1 != null && normalize(s1).equals(normalize(s2)));
  }

  // normalise all strings for comparison
  static String normalize(String s) {
    if (s == null) return null;
    return s
      .trim()
      .toUpperCase()
      .replaceAll("THE", "")
      .replaceAll("OF", "")
      .replaceAll("UNI", "UNIVERSITY");
  }

  static List<IndexHerbariorumAPI.Institution> getIH () throws IOException {
    OkHttpClient client =
      new OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    Retrofit retrofit =
      new Retrofit.Builder()
        .client(client)
        .baseUrl("http://sweetgum.nybg.org/science/api/v1/")
        .addConverterFactory(JacksonConverterFactory.create())
        .validateEagerly(true)
        .build();

    IndexHerbariorumAPI service = retrofit.create(IndexHerbariorumAPI.class);
    Response<IndexHerbariorumAPI.IHSearchResponse<IndexHerbariorumAPI.Institution>> response = service.list().execute();
    return response.body().getData();
  }

  /**
   * A self contained client to GrSciColl.
   * Much TODO...
   */
  private interface GrSciCollAPI {

    /**
     * Returns all institutions in GrSciColl assembled by paging over the api.
     * @return All institutions.
     */
    static List<Institution> list() throws IOException, InterruptedException {
      Properties props = new Properties();
      props.setProperty("registry.ws.url", "http://api.gbif.org/v1/");
      Injector injector = Guice.createInjector(new RegistryWsClientModule(props), new AnonymousAuthModule());
      InstitutionService service = injector.getInstance(InstitutionService.class);

      List<Institution> results = Lists.newArrayList();
      int offset = 0;
      boolean endOfRecords = true;
      do {
        Pageable page = new PagingRequest(offset, 1000);
        PagingResponse<org.gbif.api.model.collections.Institution> response = service.list(null, null, page);
        results.addAll(response.getResults());
        offset += response.getLimit();
        endOfRecords = response.isEndOfRecords();
      } while (!endOfRecords);

      return results;
    }
  }

  /**
   * A self contained client to IndexHerbariorum.
   * Much TODO...
   */
  private interface IndexHerbariorumAPI {

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Institution {
      String key;
      String organization;
      String code;

      public String getKey() {
        return key;
      }

      public void setKey(String key) {
        this.key = key;
      }

      public String getOrganization() {
        return organization;
      }

      public void setOrganization(String organization) {
        this.organization = organization;
      }

      public String getCode() {
        return code;
      }

      public void setCode(String code) {
        this.code = code;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class IHSearchResponse<T> {
      List<T> data;

      public List<T> getData() {
        return data;
      }

      public void setData(List<T> data) {
        this.data = data;
      }
    }

    @GET("institutions")
    Call<IHSearchResponse<Institution>> list();
  }
}
