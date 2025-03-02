package bio.terra.cli.command.shared;

import bio.terra.cli.app.utils.VersionCheckUtils;
import bio.terra.cli.businessobject.Context;
import bio.terra.cli.businessobject.User;
import bio.terra.cli.command.Main;
import bio.terra.cli.utils.Logger;
import bio.terra.cli.utils.UserIO;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Base class for all commands. This class handles:
 *
 * <p>- reading in the current global and workspace context
 *
 * <p>- setting up logging
 *
 * <p>- executing the command
 *
 * <p>Sub-classes define how to execute the command (i.e. the implementation of {@link #execute}).
 */
@CommandLine.Command
@SuppressFBWarnings(
    value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", "MS_CANNOT_BE_FINAL"},
    justification =
        "Output streams are static, so the OUT & ERR properties should also be so. Each command "
            + "execution should only instantiate a single BaseCommand sub-class. So in practice, "
            + "the call() instance method will only be called once, and these static pointers to "
            + "the output streams will only be initialized once. And since picocli handles instantiating "
            + "the command classes, we can't set the output streams in the constructor and make them "
            + "static.")
public abstract class BaseCommand implements Callable<Integer> {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BaseCommand.class);
  // output streams for commands to write to
  protected static PrintStream OUT;
  protected static PrintStream ERR;

  @Override
  public Integer call() {
    // pull the output streams from the singleton object setup by the top-level Main class
    // in the future, these streams could also be controlled by a global context property
    OUT = UserIO.getOut();
    ERR = UserIO.getErr();

    // read in the global context and setup logging
    Context.initializeFromDisk();
    Logger.setupLogging(
        Context.getConfig().getConsoleLoggingLevel(), Context.getConfig().getFileLoggingLevel());

    if (requiresLogin()) {
      // load existing credentials, prompt for login if they don't exist or are expired
      User.login();
    } else if (Context.getUser().isPresent()) {
      Context.requireUser().loadExistingCredentials();
    }

    // execute the command
    logger.debug("[COMMAND RUN] terra " + String.join(" ", Main.getArgList()));
    execute();

    //     optionally check if this version of the CLI is out of date
    if (VersionCheckUtils.isObsolete()) {
      ERR.printf(
          "Warning: Version %s of the CLI has expired. Functionality may not work as expected. To install the latest version: curl -L https://github.com/DataBiosphere/terra-cli/releases/latest/download/download-install.sh | bash ./terra\n"
              + "If you have added the CLI to your $PATH, this step will need to be repeated after the installation is complete.%n",
          bio.terra.cli.utils.Version.getVersion());
    }

    // set the command exit code
    return 0;
  }

  /**
   * Required override for executing this command and printing any output.
   *
   * <p>Sub-classes should throw exceptions for errors. The Main class handles turning these
   * exceptions into the appropriate exit code.
   */
  protected abstract void execute();

  /**
   * This method returns true if login is required for the command. Default implementation is to
   * always require login.
   *
   * <p>Sub-classes can use the current global and workspace context to decide whether to require
   * login.
   *
   * @return true if login is required for this command
   */
  protected boolean requiresLogin() {
    return true;
  }
}
