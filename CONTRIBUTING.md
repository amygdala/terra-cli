# terra-cli

1. [Setup development environment](#setup-development-environment)
    * [Logging](#logging)
    * [Troubleshooting](#troubleshooting)
2. [Publish a release](#publish-a-release)
    * [download-install.sh](#download-installsh)
    * [install.sh](#installsh)
    * [terra](#terra)
3. [Testing](#testing)
    * [Two types of tests](#two-types-of-tests)
    * [Run tests](#run-tests)
    * [Docker and Tests](#docker-and-tests)
    * [Override default server](#override-default-server)
    * [Override default Docker image](#override-default-docker-image)
    * [Override context directory](#override-context-directory)
    * [Setup test users](#setup-test-users)
    * [Automated tests](#automated-tests)
4. [Docker](#docker)
    * [Pull an existing image](#pull-an-existing-image)
    * [Build a new image](#build-a-new-image)
    * [Publish a new image](#publish-a-new-image)
    * [Update the default image](#update-the-default-image)
5. [Code structure](#code-structure)
    * [Top-level package](#top-level-package)
    * [Supported tools](#supported-tools)
        * [Adding a new supported tool](#add-a-new-supported-tool)
    * [Commands](#commands)
    * [Serialization](#serialization)
    * [Terra and cloud services](#terra-and-cloud-services)
    * [Servers](#servers)
6. [Command style guide](#command-style-guide)
    * [Options instead of parameters](#options-instead-of-parameters)
    * [Always specify a description](#always-specify-a-description)
    * [Alphabetize command lists](#alphabetize-command-lists)
    * [User readable exception messages](#user-readable-exception-messages)
    * [Singular command group names](#singular-command-group-names)

-----

### Setup development environment
The TERRA_CLI_DOCKER_MODE environment variable controls Docker support. Set it to
   DOCKER_NOT_AVAILABLE (default) to skip pulling the Docker image
   or DOCKER_AVAILABLE to pull the image (requires Docker to be installed and running).
Then, from the top-level directory, run:
```
source tools/local-dev.sh
terra
```

#### Logging
Logging is turned off by default. Modify the level with the `terra config set logging` command. Available levels are
listed in the command usage.

#### Troubleshooting
- Wipe the global context directory. `rm -R $HOME/.terra`.
- Re-run the setup script. `source tools/local-dev.sh`.


### Publish a release
A release includes a GitHub release of the `terra-cli` repository and a corresponding Docker image pushed to GCR.

The GitHub action that runs on-merge to the `main` branch automatically builds the code and creates a GitHub release.
So, this section about publishing a release manually is only intended for testing the release process, releasing a fix
before code is merged (e.g. as a GitHub pre-release), or debugging errors in the GitHub action.

To publish a release manually, from the current local code:
1. Create a tag (e.g. `test123`) and push it to the remote repository. The tag should not include any uppercase letters.
    ```
    > git tag -a test123 -m "testing version 123"
    > git push --tags
    ```
2. Update the version in `build.gradle`.
    ```
    version = 'test123'
    ```
3. Login to GitHub and run the `tools/publish-release.sh` script. This will publish a pre-release, which does not
affect the "Latest release" tag.
    ```
    > gh auth login
    > ./tools/publish-release.sh test123
    ```
    To publish a regular release, add `true` as a second argument.
     ```
    > gh auth login
    > ./tools/publish-release.sh test123 true
    ```

Note that GitHub automatically attaches an archive of the source code to the release. If you have local changes that
are not yet committed, then they may not be reflected in the source code archive, but they will be included in the
install package. We don't use the source code archive for install.

Three shell scripts are published for users.

##### `download-install.sh`
This is convenience script that:
- Downloads the latest (or specific version) of the install package
- Unarchives it
- Runs the `install.sh` script included inside
- Deletes the install package

It is published as a separate file in each GitHub release.
The intent is to have a one-line install command  `curl -L download-install.sh | bash`.

Note that this installs the CLI in the current directory. Afterwards, the user can optionally add it to their `$PATH`.

##### `install.sh`
This is an installer script that:
- Moves all the JARs to `$HOME/.terra/lib`
- Moves the `terra` run script and the `README.md` file outside the unarchived install package directory
- Deletes the unarchived install package directory
- Sets the Docker image id to the default
- Pulls the default Docker image id

It is included in the `terra-cli.tar` install package in each GitHub release.
It needs to be run from the same directory: `./install.sh`

##### `terra`
This is the run script that wraps the Java call to the CLI.
- It looks for the JARs on the classpath in the `$HOME/.terra/lib` directory.
- This script is generated by the Gradle application plugin, so any changes should be made there.

It is included in the `terra-cli.tar` install package in each GitHub release.
This is the script users can add to their `$PATH` to invoke the CLI more easily from another directory.


### Testing

#### Two types of tests
There are two types of CLI tests:
- Unit tests call commands directly in Java. They run against source code; no CLI installation is required.
Example unit test code:
```
    // `terra auth status --format=json`
    TestCommand.Result cmd = TestCommand.runCommand("auth", "status", "--format=json");
```
- Integration tests call commands from a bash script run in a separate process. They run against a CLI installation,
either one built directly from source code via `./gradlew install` or one built from the latest GitHub release.
Example integration test code:
```
    // run a script that includes a Nextflow workflow
    int exitCode = new TestBashScript().runScript("NextflowRnaseq.sh");
```

While it's possible to mix both types of testing in the same JUnit method, that should be avoided because then the
test is running commands against two different versions of the code (the source code directly in the same process
as the test, and the installed code in a separate process from the test). This could be confusing to track down errors.

Both types of tests:
- Use the same code to authenticate a test user without requiring browser interaction.
- Override the context directory to `build/test-context/`, so that tests don't overwrite the context for an existing
CLI installation on the same machine.

#### Run tests
- Run unit tests directly against the source code:
`./gradlew runTestsWithTag -PtestTag=unit`
- Run integration tests against an installation built from source code:
`./gradlew runTestsWithTag -PtestTag=integration`
- Run integration tests against an installation built from the latest GitHub release:
`./gradlew runTestsWithTag -PtestTag=integration -PtestInstallFromGitHub`

- Run a single test by specifying the `--tests` option:
`./gradlew runTestsWithTag -PtestTag=unit --tests "unit.Workspace.createFailsWithoutSpendAccess" --info`

#### Docker and Tests
The tests require the Docker daemon to be running (install mode DOCKER_AVAILABLE).

#### Override default server
The tests run against the `broad-dev` server by default. You can run them against a different server
by specifying the Gradle `server` property. e.g.:
`./gradlew runTestsWithTag -PtestTag=unit -Pserver=verily-devel`

#### Override default Docker image
The tests use the default Docker image by default. This is the image in GCR that corresponds the current version in
`build.gradle`. This default image does not include any changes to the `docker/` directory that have not yet been
released. You can run the tests with a different Docker image by specifying the Gradle `dockerImage` property. e.g.:
`./gradlew runTestsWithTag -PtestTag=unit -PdockerImage=terra-cli/local:7094e3f`

The on-PR-push GitHub action uses this flag to run against a locally built image if there are any changes to the
`docker/` directory.

#### Override context directory
The `.terra` context directory is stored in the user's home directory (`$HOME`) by default.
You can override this default by setting the Gradle `contextDir` property to a valid directory. e.g.:
`./gradlew runTestsWithTag -PtestTag=unit -PcontextDir=$HOME/context/for/tests`
If the property does not point to a valid directory, then the CLI will throw a `SystemException`.

This option is intended for tests, so that they don't overwrite the context for an installation on the same machine.

Note that this override does not apply to installed JAR dependencies. So if you run integration tests against a CLI
installation built from the latest GitHub release, the dependent libraries will overwrite an existing `$HOME/.terra/lib`
directory, though logs, credentials, and context files will be written to the specified directory. (This doesn't apply 
to unit tests or integration tests run against a CLI installation built directly from source code, because their 
dependent libraries are all in the Gradle build directory.)

You can also override the context directory in normal operation by specifying the `TERRA_CONTEXT_PARENT_DIR`
environment variable. This can be helpful for debugging without clobbering an existing installation. e.g.:
```
export TERRA_CONTEXT_PARENT_DIR="/Desktop/cli-testing"
terra config list
```

#### Setup test users
Tests use domain-wide delegation (i.e. Harry Potter users). This avoids the Google OAuth flow, which requires
interacting with a browser. Before running tests against a Terra server, the test users need to be setup there.
Setup happens exclusively in SAM, so if there are multiple Terra servers that all talk to the same SAM instance,
then you only need to do this setup once.

The CLI uses the test users defined in test config (eg `testconfig/broad.json`). This includes:
- Have permission to use the default WSM spend profile via the `cli-test-users` SAM group.
- Have permission to use the default WSM spend profile directly on the SAM resource.
- Do not have permission to use the default WSM spend profile.

The script to setup the initial set of test users on the SAM dev instance is in `tools/setup-test-users.sh`.
Note that the current testing setup uses pre-defined users in the `test.firecloud.org` domain. There would be
some refactoring involved in varying this domain.

Note that the script takes an ADMIN groupemail as a required argument. This should be the email address of a
SAM group that contains several admin emails (e.g. developer-admins group on the dev SAM deployment at the
Broad contains the corporate emails of all PF team developers as of Sept 23, 2021). This is to prevent the
team from losing access if the person who originally ran this script is not available.

If the current server requires users to be invited before they can register, then the user who runs this
script must be an admin user (i.e. a member of the `fc-admins` Google group in the SAM Gsuite). The script
invites all the test users if they do not already exist in SAM, and this requires admin permissions.

You can see the available test users on the users admin [page](https://admin.google.com/ac/users) with a
`test.firecloud.org` GSuite account.

#### Automated tests
All unit and integration tests are run nightly via GitHub action against two environments: `broad-dev` and
`verily-devel`. On test completion, a Slack notification is sent to the Broad `#platform-foundation-alerts` channel.
If you kick off a full test run manually, it will not send a notification.

Running the tests locally on your machine and via GitHub actions uses the same set of test users. While the nightly CLI
tests should not leak resources (e.g. workspaces, SAM groups), this often happens when debugging something locally.
There is a GitHub action specifically for cleaning up these leaked resources. It can be triggered manually from the
GitHub repo UI. There is an option to run the cleanup in `dry-run` mode, which should show whether there are any leaked
resources without actually deleting them.

Some tests may start failing once the number of leaked resources gets too high. Usually, this is a
`terra workspace list` test that does not page through more than ~30 workspaces. We could fix this test to be more
resilient, but it's been a useful reminder to kick off the cleanup GitHub action, so we haven't done that yet. If
you see unexpected failures around listing workspaces, try kicking off the cleanup action and re-running.

#### Test config per deployment

By default, tests run against Broad deployment. To run against a different deployment:

- Create a new file under [Test config](https://github.com/DataBiosphere/terra-cli/tree/main/src/test/resources/testconfigs)
- Create a new `render-config.sh` which renders config for your deployment. Put the configs in a new directory under `rendered`, eg `rendered/<mydeployment>`. The name of this directory must match the name of the testConfig in the next step.
- Run tests with `-PtestConfig=<testconfigfilenamewithout.json>`

For example, consider the project that external resources are created in. The Broad deployment uses a project in Broad
GCP org; Verily deployment uses a project in Verily GCP org.

### Docker
The `docker/` directory contains files required to build the Docker image.
All files in the `scripts/` sub-directory are copied to the image, into a sub-directory that is on the `$PATH`, 
and made executable.

Merging a PR and installing should take care of all this Docker image stuff for you, so these notes are mostly useful
for debugging/development when you need to make an image available outside of that normal process.
- The `tools/local-dev.sh` and `install.sh` scripts pull the default image.
- The `tools/publish-release.sh` script builds and publishes a new image. It also updates the image path that the CLI
uses to point to this newly published image.

#### Pull an existing image
The gcr.io/terra-cli-dev registry is public readable, so anyone should be able to pull images.

To use a specific Docker image from GCR:
1. Pull the image with that tag.
    ```
    > docker pull gcr.io/terra-cli-dev/terra-cli/v0.0:b5fdce0
    ```
2. Update the image id that the CLI uses.
    ```
    > terra config set image --image=gcr.io/terra-cli-dev/terra-cli/v0.0:b5fdce0
    ```

#### Build a new image
For any change in the `docker/` directory to take effect:
1. Build a new image. This uses a short Git hash for the current commit as the tag. See the script comments for more
options.
    ```
    > ./tools/build-docker.sh
   
    Generating an image tag from the Git commit hash
    Building the image
    [...Docker output...]
    Successfully built 6558c3bcb316
    Successfully tagged terra-cli/local:92d6e09
    terra-cli/local:92d6e09 successfully built
    ```
2. Update the image id that the CLI uses. (See output of previous command for image name and tag.)
    ```
    > terra config set image --image=terra-cli/local:b5fdce0
    ```

#### Publish a new image
To publish a new image to GCR:
1. Build the image (see above).
2. Render the CI credentials from Vault, in order to upload to GCR.
    ```
    > ./tools/render-config.sh
    ```
3. Push it to GCR. (See output of build command for local image tag.) See the script comments for more options.
    ```
    > ./tools/publish-docker.sh 92d6e09 "terra-cli/test" 92d6e09
      
    Logging in to docker using the CI service account key file
    Login Succeeded
    Tagging the local docker image with the name to use in GCR
    Logging into to gcloud and configuring docker with the CI service account
    Activated service account credentials for: [dev-ci-sa@broad-dsde-dev.iam.gserviceaccount.com]
    Pushing the image to GCR
    [...Docker push output...]
    92d6e09: digest: sha256:f419d97735749573baff95247d0918d174cb683089c9b1370e7c99817b9b6d67 size: 2211
    Restoring the current gcloud user
    Updated property [core/account].
    gcr.io/terra-cli-dev/terra-cli/test:92d6e09 successfully pushed to GCR
    ```
4. Pull the image from GCR (see above). This is so that the name and tag on your local image matches what it will
look like for someone who did not build the image.

#### Update the default image
It's best to do this as part of a release, but if it's necessary to update the default image manually:
1. Publish the image (see above).
2. Update the `DockerAppsRunner.defaultImageId` method in the Java code to return a hard-coded string.


### Code structure
Below is an outline of the package structure. More details are included in the sub-sections below.
```
bio.terra.cli      
    apps           # external supported tools
    businessobject # internal state classes and business logic
    command        # command definitions
    exception      # exception classes that map to an exit code
    serialization  # serialization format classes for command input/output and writing to disk
    service        # helper/wrapper classes for talking to Terra and cloud services
    utils          # uncategorized
```

* [Business logic](#business-logic)
* [Supported tools](#supported-tools)
    * [Adding a new supported tool](#add-a-new-supported-tool)
* [Commands](#commands)
* [Serialization](#serialization)
* [Terra and cloud services](#terra-and-cloud-services)
* [Servers](#servers)

#### Business logic
The `businessobjects` package contains objects that represent the internal state (e.g. `Config`, `Server`, `User`, 
`Workspace`). Since the CLI Java code exits after each command, the `Context` class persists the state on disk in the
context directory `$HOME/.terra`.

#### Supported tools
The `apps` package contains (external) tools that the CLI supports.
Currently these tools can be called from the top-level, so it looks the same as it would if you called it on your 
local terminal, only with a `terra` prefix. For example:
```
terra gsutil ls
terra bq version
terra nextflow run hello
```

The list of supported tools that can be called is specified in an enum in the `terra app list` class.

##### Add a new supported tool
To add a new supported tool:
   1. Install the app in the `docker/Dockerfile`
   2. Build the new image (see instructions in section above).
   3. Test that the install worked by calling the app through the `terra app execute` command.
   (e.g. `terra app execute dsub --version`). This command just runs the Docker container and 
   executes the command, without requiring any new Java code. This `terra app execute` command
   is intended for debugging only; this won't be how users call the tool.
   4. Add a new command class in the `src/main/java/bio/terra/cli/command/app/passthrough` package.
   Copy/paste an existing class in that same package as a starting point.
   5. Add it to the list of tools shown by `terra app list` by adding the new command class to
   the list of sub-commands in the `@Command` annotation of the `Main.class`. This means you can
   invoke the command by prefixing it with terra (e.g. `terra dsub -version`).
   6. When you run e.g. `terra dsub -version`, the CLI:
      - Launches a Docker container
      - Runs the `terra_init.sh` script in the `docker/scripts` directory, which activates the user’s
      pet service account and sets the workspace project
      - Runs the `dsub` command
   7. You can pass environment variables through to the Docker container by populating a `Map` and
   passing it to the `DockerAppsRunner.runToolCommand` method. Two environment variables are always
   passed:
       - `GOOGLE_CLOUD_PROJECT` = the workspace project id
       - `GOOGLE_APPLICATION_CREDENTIALS` = the pet service account key file
   8.  You can mount directories on the host machine to the Docker container by populating a second
   `Map` and passing it to the same `DockerAppsRunner.runToolCommand` method. The current working
   directory is always mounted to the Docker container.
   9. Publish the new Docker image and update the default image that the CLI uses to the new version
   (see instructions in section above).

#### Commands
The `command` package contains the hierarchy of the commands as they appear to the user.
The directory structure matches the command hierarchy. The only exceptions to this are the pass-through app
commands (e.g. `terra gsutil`), which are at the top level of the hierarchy, but in the `app/passthrough` sub-directory.

`Main` is the top-level command and child commands are defined in class annotations.
Most of the top-level commands (e.g. `auth`, `server`, `workspace`) are strictly for grouping; the command itself 
doesn't do anything.

#### Serialization
There are 4 types of objects.
- Internal state and business logic
  - `businessobject` package
  - May be a part of the state (e.g. `Workspace`, `User`) or just contain business logic (e.g. `WorkspaceUser`)
- Serialization format for writing to disk (`.terra/context.json`)
  - `serialization.persisted` package
  - Prefixed with "PD" (e.g. `PDWorkspace`, `PDUser`)
- Serialization format for command input/ouput (json format)
  - `serialization.userfacing` package
  - Prefixed with "UF" (e.g. `UFWorkspace`, `UFUser`)
- Create/update parameters
  - `serialization.userfacing.inputs` package
  - Most of these parameter classes are not actually being used for user-facing input. I put them in a sub-package 
  under `serialization.userfacing` because I think we might want to expose them to users in the future. e.g. By passing 
  in a json file instead of specifying lots of options, as we do now for bucket lifecycle rules.

#### Terra and cloud services
The `service` package contains classes that communicate with Terra and cloud services.
They contain retries and other error-handling that is specific to each service.
This functionality is not CLI-specific and could be moved into the service's client library or a helper client library,
in the future.

#### Servers
The `src/main/java/resources/servers/` directory contains the server specification files.
Each file specifies a Terra environment, or set of connected Terra services, and maps to an instance of the 
`ServerSpecification` class.

The `ServerSpecification` class closely follows the Test Runner class of the same name.
There's no need to keep these classes exactly in sync, but that may be a good place to consult when expanding the 
class here.

To add a new server specification, create a new file in this directory and add the file name to the `all-servers.json` 
file.


### Command style guide
Below are guidelines for adding or modifying commands. The goal is to have a consistent presentation across commands.
These are not hard rules, but should apply to most commands. Always choose better usability over following a rule here.

#### Options instead of parameters
Use options instead of parameters, wherever possible. 
e.g. `terra workspace add-user --email=user@gmail.com --role=READER`
instead of `terra workspace add-user user@gmail.com READER`.

This makes it easier to maintain backwards compatibility when adding new arguments. It also makes it easier to read
commands with multiple arguments, without having to remember the order.

The exception to this rule is the `config set` command, which takes one parameter instead of an option.

All option names should start with two dashes. e.g. `--email`

#### Always specify a description
Specify a description for all commands and options. Write it like a sentence: end with a period and capitalize the first
letter of the first word. e.g. `Add a user or group to the workspace.`, `Group name.`

Use a verb for the first word of a command or command group description. e.g. `Manage spend profiles.`

#### Alphabetize command lists
Alphabetize commands by their name (not their Java class name, though that is usually identical) when specifying
a list of sub-commands. picocli does not do this automatically.

#### User readable exception messages
`UserActionableException`s are expected in the course of normal use. Their messages should be readable to the user.
`SystemException`s and any other exceptions are not expected in the course of normal use. They indicate a bug or error 
that should be reported, so there's no need to make these messages readable to the user.

#### Singular command group names
Use singular command group names instead of plural. e.g. `terra resource` instead of `terra resources`.
