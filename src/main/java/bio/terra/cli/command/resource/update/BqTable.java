package bio.terra.cli.command.resource.update;

import bio.terra.cli.businessobject.Context;
import bio.terra.cli.businessobject.Resource;
import bio.terra.cli.command.shared.BaseCommand;
import bio.terra.cli.command.shared.options.BqDatasetNewIds;
import bio.terra.cli.command.shared.options.Format;
import bio.terra.cli.command.shared.options.ResourceUpdate;
import bio.terra.cli.command.shared.options.WorkspaceOverride;
import bio.terra.cli.exception.UserActionableException;
import bio.terra.cli.serialization.userfacing.input.UpdateReferencedBqTableParams;
import bio.terra.cli.serialization.userfacing.resource.UFBqTable;
import picocli.CommandLine;

/** This class corresponds to the fourth-level "terra resource update bq-table" command. */
@CommandLine.Command(
    name = "bq-table",
    description = "Update a BigQuery data table.",
    showDefaultValues = true)
public class BqTable extends BaseCommand {
  @CommandLine.Mixin BqDatasetNewIds bqDatasetNewIds;
  @CommandLine.Mixin ResourceUpdate resourceUpdateOptions;
  @CommandLine.Mixin WorkspaceOverride workspaceOption;
  @CommandLine.Mixin Format formatOption;

  @CommandLine.Option(names = "--new-table-id", description = "New BigQuery table id.")
  private String newBqTableId;

  /** Update a BigQuery dataset in the workspace. */
  @Override
  protected void execute() {
    workspaceOption.overrideIfSpecified();

    // all update parameters are optional, but make sure at least one is specified
    if (!resourceUpdateOptions.isDefined()
        && !bqDatasetNewIds.isDefined()
        && newBqTableId == null) {
      throw new UserActionableException("Specify at least one property to update.");
    }

    // get the resource and make sure it's the right type
    bio.terra.cli.businessobject.resource.BqTable resource =
        Context.requireWorkspace()
            .getResource(resourceUpdateOptions.resourceNameOption.name)
            .castToType(Resource.Type.BQ_TABLE);

    UpdateReferencedBqTableParams.Builder bqTableParams =
        new UpdateReferencedBqTableParams.Builder()
            .resourceParams(resourceUpdateOptions.populateMetadataFields().build())
            .tableId(newBqTableId)
            .datasetId(bqDatasetNewIds.getNewBqDatasetId())
            .projectId(bqDatasetNewIds.getNewGcpProjectId());

    resource.updateReferenced(bqTableParams.build());

    formatOption.printReturnValue(new UFBqTable(resource), BqTable::printText);
  }

  /** Print this command's output in text format. */
  private static void printText(UFBqTable returnValue) {
    OUT.println("Successfully updated BigQuery data table.");
    returnValue.print();
  }
}
