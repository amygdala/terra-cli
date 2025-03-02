package bio.terra.cli.app;

import bio.terra.cli.app.utils.AppDefaultCredentialUtils;
import bio.terra.cli.app.utils.DockerClientWrapper;
import bio.terra.cli.businessobject.Context;
import bio.terra.cli.exception.PassthroughException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class runs client-side tools in a Docker container and manipulates the tools-related
 * properties of the global context object.
 */
public class DockerCommandRunner extends CommandRunner {
  private static final Logger logger = LoggerFactory.getLogger(DockerCommandRunner.class);

  private final DockerClientWrapper dockerClientWrapper = new DockerClientWrapper();

  // default $HOME directory on the container (this is where we expect to look for the global
  // context)
  private static final String CONTAINER_HOME_DIR = "/root";
  // mount point for the workspace directory
  private static final String CONTAINER_WORKING_DIR = "/usr/local/etc";

  // name of the ADC file mounted on the container
  private static final String APPLICATION_DEFAULT_CREDENTIALS_FILE_NAME =
      "application_default_credentials.json";

  /**
   * This method builds a command string that:
   *
   * <p>- calls the terra_init.sh script, which configures gcloud with the workspace project and pet
   * SA
   *
   * <p>- runs the given command
   *
   * @param command the command and arguments to execute
   * @return the full string of commands and arguments to execute
   */
  protected String wrapCommandInSetupCleanup(List<String> command) {
    // the terra_init script is already copied into the Docker image
    return "terra_init.sh && " + buildFullCommand(command);
  }

  /**
   * Run a tool command inside a new Docker container.
   *
   * <p>The terra_init.sh script that was copied into the Docker image will be run before the given
   * command.
   *
   * <p>This method sets the GOOGLE_APPLICATION_CREDENTIALS env var = path to the pet SA key file on
   * the container. This will overwrite any previous version, because the path will likely be
   * different on the container.
   *
   * @param command the full string of command and arguments to execute
   * @param envVars a mapping of environment variable names to values
   * @return process exit code
   */
  protected int runToolCommandImpl(String command, Map<String, String> envVars)
      throws PassthroughException {
    // mount the global context directory and the current working directory to the container
    //  e.g. global context dir (host) $HOME/.terra -> (container) CONTAINER_HOME_DIR/.terra
    //       current working dir (host) /Users/mm/workspace123 -> (container) CONTAINER_HOME_DIR
    Map<Path, Path> bindMounts = new HashMap<>();
    bindMounts.put(getGlobalContextDirOnContainer(), Context.getContextDir());
    bindMounts.put(Path.of(CONTAINER_WORKING_DIR), Path.of(System.getProperty("user.dir")));

    // mount the gcloud config directory to the container
    // e.g. gcloud config dir (host) $HOME/.config/gcloud -> (container)
    // CONTAINER_HOME_DIR/.config/gcloud
    Path gcloudConfigDir = Path.of(System.getProperty("user.home"), ".config/gcloud");
    Path gcloudConfigDirOnContainer = Path.of(CONTAINER_HOME_DIR, ".config/gcloud");
    if (gcloudConfigDir.toFile().exists() && gcloudConfigDir.toFile().isDirectory()) {
      bindMounts.put(gcloudConfigDirOnContainer, gcloudConfigDir);
    }

    // check if the system property for testing credentials is populated
    Optional<Path> credentialsFileForTest = getOverrideCredentialsFileForTesting();
    if (credentialsFileForTest.isPresent()) { // this is a unit test
      // mount the file to the container
      Path adcFileOnContainer = getADCFileOnContainer();
      bindMounts.put(adcFileOnContainer, credentialsFileForTest.get());

      // set the env var and gcloud auth credentials using the pet SA key file
      envVars.put("GOOGLE_APPLICATION_CREDENTIALS", adcFileOnContainer.toString());
      command =
          "echo \"Setting the gcloud credentials to match the application default credentials\"; "
              + "gcloud auth activate-service-account --key-file=${GOOGLE_APPLICATION_CREDENTIALS}; "
              + command;
    } else { // this is normal operation
      // check that the ADC match the user or their pet SA
      AppDefaultCredentialUtils.throwIfADCDontMatchContext();

      // if the ADC are set by a file, then make sure that file is mounted to the container and the
      // env var points to it if needed
      Optional<Path> adcCredentialsFile = AppDefaultCredentialUtils.getADCBackingFile();
      if (adcCredentialsFile.isPresent()) {
        if (adcCredentialsFile.get().equals(AppDefaultCredentialUtils.getDefaultGcloudADCFile())) {
          logger.info(
              "ADC backing file is in the default location and is already mounted in the gcloud config directory");
        } else {
          // mount the pet SA key file to the container in the .terra directory
          // e.g. ADC file (host) $HOME/pet-sa-key.json -> (container)
          // CONTAINER_HOME_DIR/.terra/pet-keys/[user id]/application_default_credentials.json
          Path adcFileOnContainer = getADCFileOnContainer();
          bindMounts.put(adcFileOnContainer, adcCredentialsFile.get());

          // set the env var to point to the file, since it's not in the default gcloud location
          envVars.put("GOOGLE_APPLICATION_CREDENTIALS", adcFileOnContainer.toString());
        }
      } else {
        logger.info("ADC set by metadata server.");
      }
    }

    // create and start the docker container
    dockerClientWrapper.startContainer(
        Context.getConfig().getDockerImageId(),
        command,
        CONTAINER_WORKING_DIR,
        envVars,
        bindMounts);

    // read the container logs, which contains the command output, and write them to stdout
    dockerClientWrapper.streamLogsForContainer();

    // block until the container exits
    Integer statusCode = dockerClientWrapper.waitForContainerToExit();
    logger.debug("docker run status code: {}", statusCode);

    // get the process exit code
    Long exitCode = dockerClientWrapper.getProcessExitCode();
    logger.debug("docker inspect exit code: {}", exitCode);

    // delete the container
    dockerClientWrapper.deleteContainer();

    return exitCode.intValue();
  }

  /**
   * Get the global context directory on the container.
   *
   * <p>e.g. (host) $HOME/.terra/ -> (container) CONTAINER_HOME_DIR/.terra/
   *
   * @return absolute path to the global context directory on the container
   */
  private static Path getGlobalContextDirOnContainer() {
    Path globalContextDirName = Context.getContextDir().getFileName();
    return Path.of(CONTAINER_HOME_DIR).resolve(globalContextDirName);
  }

  /**
   * Get the pet SA key file for the given user and workspace on the container.
   *
   * <p>e.g. (host) $HOME/.terra/pet-keys/[user id]/[workspace id] -> (container)
   * CONTAINER_HOME_DIR/.terra/pet-keys/[user id]/[workspace id]
   *
   * @return absolute path to the pet SA key file for the given user and workspace on the container
   */
  private static Path getPetSaKeyFileOnContainer() {
    // get the full path of the key file and global context directory on the host
    Path keyFileOnHost = Context.getPetSaKeyFile();
    Path globalContextDirOnHost = Context.getContextDir();

    // remove the global context directory part of the key file path
    // e.g. keyFileOnHost = $HOME/.terra/pet-keys/[user id]/[workspace id]
    //      globalContextDirOnHost = $HOME/.terra/
    //      relativePathToKeyFile = pet-keys/[user id]/[workspace id]
    Path relativePathToKeyFile = globalContextDirOnHost.relativize(keyFileOnHost);

    // key file path on container = global context dir on container + relative path to key file
    return getGlobalContextDirOnContainer().resolve(relativePathToKeyFile);
  }

  /**
   * Get the application default credentials file for the given user on the container.
   *
   * <p>e.g. (host) $HOME/.config/gcloud/application_default_credentials.json -> (container)
   * CONTAINER_HOME_DIR/.terra/pet-keys/application_default_credentials.json
   *
   * @return absolute path to the application default credentials file for the given user on the
   *     container
   */
  @SuppressFBWarnings(
      value = "NP_NULL_ON_SOME_PATH",
      justification =
          "Pet SA key files are stored in a sub-directory of the .terra context directory, so the file path will always have a parent.")
  private static Path getADCFileOnContainer() {
    // store the ADC credentials in the same directory as the user's pet SA key files
    return getPetSaKeyFileOnContainer()
        .getParent()
        .resolve(APPLICATION_DEFAULT_CREDENTIALS_FILE_NAME);
  }
}
