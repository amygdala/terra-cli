package bio.terra.cli.command;

import bio.terra.cli.businessobject.Context;
import bio.terra.cli.command.shared.BaseCommand;
import bio.terra.cli.command.shared.options.Format;
import bio.terra.cli.serialization.command.CommandStatus;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the second-level "terra status" command. */
@Command(name = "status", description = "Print details about the current workspace and server.")
public class Status extends BaseCommand {

  @CommandLine.Mixin Format formatOption;

  /** Build the return value from the global and workspace context. */
  @Override
  protected void execute() {
    CommandStatus statusReturnValue =
        new CommandStatus(Context.getServer(), Context.getWorkspace().orElse(null));
    formatOption.printReturnValue(statusReturnValue, this::printText);
  }

  /** Print this command's output in text format. */
  private void printText(CommandStatus returnValue) {
    // check if current workspace is defined
    if (returnValue.workspace == null) {
      OUT.println("There is no current Terra workspace defined.");
    } else {
      returnValue.workspace.print();
    }

    OUT.println();
    OUT.println("Terra server: " + returnValue.server.name);

    if (returnValue.workspace != null
        && !returnValue.server.name.equals(returnValue.workspace.serverName)) {
      OUT.println(
          "WARNING: The current workspace exists on a different server ("
              + returnValue.workspace.serverName
              + ") than the current one ("
              + returnValue.server.name
              + ").");
    }
  }

  /** This command never requires login. */
  @Override
  protected boolean requiresLogin() {
    return false;
  }
}
