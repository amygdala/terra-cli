package bio.terra.cli.command.groups;

import bio.terra.cli.businessobject.Context;
import bio.terra.cli.command.shared.BaseCommand;
import bio.terra.cli.service.SamService;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the third-level "terra groups create" command. */
@Command(name = "create", description = "Create a new Terra group.")
public class Create extends BaseCommand {
  @CommandLine.Parameters(index = "0", description = "The name of the group")
  private String group;

  /** Create a new Terra group. */
  @Override
  protected void execute() {
    new SamService(Context.getServer(), Context.requireUser()).createGroup(group);
    OUT.println("Group " + group + " successfully created.");
  }
}
