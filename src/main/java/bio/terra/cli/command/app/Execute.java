package bio.terra.cli.command.app;

import bio.terra.cli.apps.DockerAppsRunner;
import bio.terra.cli.command.baseclasses.BaseCommand;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the third-level "terra app execute" command. */
@Command(
    name = "execute",
    description =
        "[FOR DEBUG] Execute a command in the application container for the Terra workspace, with no setup.")
public class Execute extends BaseCommand<Void> {

  @CommandLine.Parameters(index = "0", paramLabel = "command", description = "command to execute")
  private String cmd;

  @CommandLine.Unmatched private List<String> cmdArgs;

  @Override
  protected Void execute() {
    String fullCommand = cmd;
    if (cmdArgs != null && cmdArgs.size() > 0) {
      final String argSeparator = " ";
      fullCommand += argSeparator + String.join(argSeparator, cmdArgs);
    }
    new DockerAppsRunner(globalContext, workspaceContext).runToolCommand(fullCommand);

    return null;
  }
}
