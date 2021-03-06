package org.gbif.registry.cli.doisynchronizer;

import org.gbif.registry.cli.common.DataCiteConfiguration;
import org.gbif.registry.cli.common.DbConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

/**
 *
 *
 */
public class DoiSynchronizerConfiguration {

  @Parameter(names = "--portal-url")
  @Valid
  @NotNull
  public String portalurl;

  @Parameter(names = "--api-root")
  @Valid
  @NotNull
  public String apiRoot;

  @Valid
  @NotNull
  public DbConfiguration registry = new DbConfiguration();

  @ParametersDelegate
  @Valid
  @NotNull
  public DataCiteConfiguration datacite = new DataCiteConfiguration();

  @ParametersDelegate
  @Valid
  @NotNull
  public PostalServiceConfiguration postalservice = new PostalServiceConfiguration();


  @Parameter(names = "--doi", required = false)
  @NotNull
  public String doi = "";

  @Parameter(names = "--doi-list", required = false)
  @NotNull
  public String doiList = "";

  @Parameter(names = {"--fix-doi"}, required = false)
  @Valid
  public boolean fixDOI = false;

  @Parameter(names = {"--skip-dia"}, required = false)
  @Valid
  public boolean skipDiagnostic = false;

  @Parameter(names = {"--export"}, required = false)
  @Valid
  public boolean export = false;

  @Parameter(names = {"--list-failed-doi"}, required = false)
  @Valid
  public boolean listFailedDOI = false;
}
