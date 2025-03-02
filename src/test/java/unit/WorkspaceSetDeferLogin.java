package unit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.cli.serialization.userfacing.UFAuthStatus;
import bio.terra.cli.serialization.userfacing.UFStatus;
import bio.terra.cli.serialization.userfacing.UFWorkspace;
import bio.terra.cli.utils.UserIO;
import harness.TestCommand;
import harness.TestUser;
import harness.baseclasses.SingleWorkspaceUnit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for the `--defer-login` option to skip the login prompt when setting the current workspace.
 */
@Tag("unit")
public class WorkspaceSetDeferLogin extends SingleWorkspaceUnit {
  TestUser workspaceSharee;
  UUID sharedWorkspaceId;

  @BeforeAll
  protected void setupOnce() throws Exception {
    super.setupOnce();

    // `terra workspace create --format=json`
    UFWorkspace createWorkspace =
        TestCommand.runAndParseCommandExpectSuccess(UFWorkspace.class, "workspace", "create");
    sharedWorkspaceId = createWorkspace.id;

    workspaceSharee = TestUser.chooseTestUserWhoIsNot(workspaceCreator);

    // `terra workspace add-user --email=$sharee --role=READER`
    TestCommand.runCommandExpectSuccess(
        "workspace", "add-user", "--email=" + workspaceSharee.email, "--role=READER");
  }

  @AfterAll
  protected void cleanupOnce() throws Exception {
    super.cleanupOnce();

    // `terra workspace set --id=$id`
    TestCommand.runCommandExpectSuccess("workspace", "set", "--id=" + sharedWorkspaceId);

    // `terra workspace delete --quiet`
    TestCommand.runCommandExpectSuccess("workspace", "delete", "--quiet");
  }

  @Test
  @DisplayName("workspace id can be set before logging in, and metadata loads after logging in")
  void workspaceLoadsOnlyAfterLogin() throws IOException {
    // `terra auth revoke`
    TestCommand.runCommandExpectSuccess("auth", "revoke");

    // `terra workspace set --id=$id --defer-login`
    UFWorkspace workspaceSet =
        TestCommand.runAndParseCommandExpectSuccess(
            UFWorkspace.class, "workspace", "set", "--id=" + getWorkspaceId(), "--defer-login");
    assertEquals(
        getWorkspaceId(), workspaceSet.id, "workspace set before login includes workspace id");
    assertNull(
        workspaceSet.googleProjectId,
        "workspace set before login does not include google project id");

    // `terra status`
    UFStatus status = TestCommand.runAndParseCommandExpectSuccess(UFStatus.class, "status");
    assertEquals(
        getWorkspaceId(), status.workspace.id, "status before login includes workspace id");
    assertNull(
        status.workspace.googleProjectId, "status before login does not include google project id");

    // `terra auth status`
    UFAuthStatus authStatus =
        TestCommand.runAndParseCommandExpectSuccess(UFAuthStatus.class, "auth", "status");
    assertNull(authStatus.userEmail, "auth status before login does not include user email");
    assertNull(
        authStatus.serviceAccountEmail, "auth status before login does not include pet SA email");

    workspaceCreator.login();

    // `terra status`
    status = TestCommand.runAndParseCommandExpectSuccess(UFStatus.class, "status");
    assertEquals(getWorkspaceId(), status.workspace.id, "status after login includes workspace id");
    assertNotNull(
        status.workspace.googleProjectId, "status after login includes google project id");

    // `terra auth status`
    authStatus = TestCommand.runAndParseCommandExpectSuccess(UFAuthStatus.class, "auth", "status");
    assertNotNull(authStatus.userEmail, "auth status after login includes user email");
    assertNotNull(authStatus.serviceAccountEmail, "auth status after login includes pet SA email");
  }

  @Test
  @DisplayName(
      "workspace metadata fails to load after logging in as a user without read access, then succeeds with a different workspace that they do have access to")
  void workspaceLoadFailsWithNoAccess() throws IOException {
    // `terra auth revoke`
    TestCommand.runCommandExpectSuccess("auth", "revoke");

    // `terra workspace set --id=$id --defer-login`
    TestCommand.runCommandExpectSuccess(
        "workspace", "set", "--id=" + getWorkspaceId(), "--defer-login");

    // the login should succeed and also print an error message to stderr that the workspace failed
    // to load
    ByteArrayOutputStream stdOutStream = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErrStream = new ByteArrayOutputStream();
    UserIO.initialize(
        new PrintStream(stdOutStream, true, StandardCharsets.UTF_8),
        new PrintStream(stdErrStream, true, StandardCharsets.UTF_8),
        null);
    workspaceSharee.login();
    assertThat(
        "login prints an error message that workspace failed to load",
        stdErrStream.toString(StandardCharsets.UTF_8),
        CoreMatchers.containsStringIgnoringCase(
            "Error loading workspace information for the logged in user (workspace id: "
                + getWorkspaceId()
                + ")."));

    // `terra status`
    UFStatus status = TestCommand.runAndParseCommandExpectSuccess(UFStatus.class, "status");
    assertEquals(
        getWorkspaceId(),
        status.workspace.id,
        "status after login user without access includes workspace id");
    assertNull(
        status.workspace.googleProjectId,
        "status after login user without access does not include google project id");

    // `terra auth status`
    UFAuthStatus authStatus =
        TestCommand.runAndParseCommandExpectSuccess(UFAuthStatus.class, "auth", "status");
    assertNotNull(
        authStatus.userEmail, "auth status after login user without access includes user email");
    assertNull(
        authStatus.serviceAccountEmail,
        "auth status after login user without access does not include pet SA email");

    // `terra resource list`
    String stdErr = TestCommand.runCommandExpectExitCode(2, "resource", "list");
    assertThat(
        "error message includes unauthorized to read workspace resource",
        stdErr,
        CoreMatchers.containsStringIgnoringCase(
            "User "
                + authStatus.userEmail
                + " is not authorized to read resource "
                + getWorkspaceId()
                + " of type workspace"));

    // `terra workspace set --id=$sharedId`
    TestCommand.runCommandExpectSuccess("workspace", "set", "--id=" + sharedWorkspaceId);

    // `terra status`
    status = TestCommand.runAndParseCommandExpectSuccess(UFStatus.class, "status");
    assertEquals(
        sharedWorkspaceId,
        status.workspace.id,
        "status after login user with access includes shared workspace id");
    assertNotNull(
        status.workspace.googleProjectId,
        "status after login user with access includes google project id");

    // `terra auth status`
    authStatus = TestCommand.runAndParseCommandExpectSuccess(UFAuthStatus.class, "auth", "status");
    assertNotNull(
        authStatus.userEmail, "auth status after login user with access includes user email");
    assertNotNull(
        authStatus.serviceAccountEmail,
        "auth status after login user with access includes pet SA email");

    TestCommand.runCommandExpectSuccess("resource", "list");
  }

  @Test
  @DisplayName("suppress login flag does not have any effect if user is already logged in")
  void workspaceLoadsImmediatelyWhenAlreadyLoggedIn() throws IOException {
    workspaceCreator.login();

    // `terra workspace set --id=$id --defer-login`
    UFWorkspace workspaceSet =
        TestCommand.runAndParseCommandExpectSuccess(
            UFWorkspace.class, "workspace", "set", "--id=" + getWorkspaceId(), "--defer-login");
    assertEquals(
        getWorkspaceId(), workspaceSet.id, "workspace set after login includes workspace id");
    assertNotNull(
        workspaceSet.googleProjectId, "workspace set after login includes google project id");

    // `terra status`
    UFStatus status = TestCommand.runAndParseCommandExpectSuccess(UFStatus.class, "status");
    assertEquals(getWorkspaceId(), status.workspace.id, "status after login includes workspace id");
    assertNotNull(
        status.workspace.googleProjectId, "status after login includes google project id");

    // `terra auth status`
    UFAuthStatus authStatus =
        TestCommand.runAndParseCommandExpectSuccess(UFAuthStatus.class, "auth", "status");
    assertNotNull(authStatus.userEmail, "auth status after login includes user email");
    assertNotNull(authStatus.serviceAccountEmail, "auth status after login includes pet SA email");

    // `terra resource list`
    TestCommand.runCommandExpectSuccess("resource", "list");
  }

  @Test
  @DisplayName("workspace set without flag still prompts for login")
  void withoutFlagWorkspaceSetRequiresLogin() {
    // `terra auth revoke`
    TestCommand.runCommandExpectSuccess("auth", "revoke");

    // `terra config set browser MANUAL`
    TestCommand.runCommandExpectSuccess("config", "set", "browser", "MANUAL");

    // `terra workspace set --id=$id`
    ByteArrayInputStream stdIn =
        new ByteArrayInputStream("invalid oauth code".getBytes(StandardCharsets.UTF_8));
    TestCommand.Result cmd =
        TestCommand.runCommand(stdIn, "workspace", "set", "--id=" + getWorkspaceId());
    assertThat(
        "stdout includes login prompt",
        cmd.stdOut,
        CoreMatchers.containsString(
            "Please open the following address in a browser on any machine"));
  }
}
