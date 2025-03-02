package bio.terra.cli.command.workspace;

import bio.terra.cli.businessobject.Context;
import bio.terra.cli.businessobject.WorkspaceUser;
import bio.terra.cli.command.shared.BaseCommand;
import bio.terra.cli.command.shared.options.Format;
import bio.terra.cli.command.shared.options.WorkspaceOverride;
import bio.terra.cli.serialization.userfacing.UFWorkspaceUser;
import bio.terra.cli.utils.UserIO;
import java.util.Comparator;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the third-level "terra workspace list-users" command. */
@Command(name = "list-users", description = "List the users of the workspace.")
public class ListUsers extends BaseCommand {

  @CommandLine.Mixin WorkspaceOverride workspaceOption;
  @CommandLine.Mixin Format formatOption;

  /** List all users of the workspace. */
  @Override
  protected void execute() {
    workspaceOption.overrideIfSpecified();
    formatOption.printReturnValue(
        UserIO.sortAndMap(
            WorkspaceUser.list(Context.requireWorkspace()),
            Comparator.comparing(WorkspaceUser::getEmail),
            UFWorkspaceUser::new),
        ListUsers::printText);
  }

  /** Print this command's output in text format. */
  private static void printText(java.util.List<UFWorkspaceUser> returnValue) {
    for (UFWorkspaceUser workspaceUser : returnValue) {
      workspaceUser.print();
    }
  }
}
