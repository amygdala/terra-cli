package unit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cli.businessobject.Context;
import bio.terra.cli.businessobject.User;
import bio.terra.cli.service.GoogleOauth;
import bio.terra.cli.service.SamService;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import harness.TestCommand;
import harness.TestUsers;
import harness.baseclasses.ClearContextUnit;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for the authentication part of the test harness, and the state of the credential store on
 * disk.
 */
@Tag("unit")
public class AuthLoginLogout extends ClearContextUnit {
  @Test
  @DisplayName("test user login updates global context")
  void loginTestUser() throws IOException {
    // select a test user and login
    TestUsers testUser = TestUsers.chooseTestUser();
    testUser.login();

    // check that the credential exists in the store on disk
    DataStore<StoredCredential> dataStore = TestUsers.getCredentialStore();
    assertEquals(1, dataStore.keySet().size(), "credential store only contains one entry");
    assertTrue(
        dataStore.containsKey(GoogleOauth.CREDENTIAL_STORE_KEY),
        "credential store contains hard-coded single user key");
    StoredCredential storedCredential = dataStore.get(GoogleOauth.CREDENTIAL_STORE_KEY);
    assertThat(storedCredential.getAccessToken(), CoreMatchers.not(emptyOrNullString()));

    // check that the current user in the global context = the test user
    Optional<User> currentUser = Context.getUser();
    assertTrue(currentUser.isPresent(), "current user set in global context");
    assertThat(
        "test user email matches the current user set in global context",
        testUser.email,
        equalToIgnoringCase(currentUser.get().getEmail()));
  }

  @Test
  @DisplayName("test user logout updates global context")
  void logoutTestUser() throws IOException {
    // select a test user and login
    TestUsers testUser = TestUsers.chooseTestUser();
    testUser.login();

    // `terra auth revoke`
    TestCommand.runCommandExpectSuccess("auth", "revoke");

    // check that the credential store on disk is empty
    DataStore<StoredCredential> dataStore = TestUsers.getCredentialStore();
    assertEquals(0, dataStore.keySet().size(), "credential store is empty");

    // read the global context in from disk again to check what got persisted
    // check that the current user in the global context is unset
    Optional<User> currentUser = Context.getUser();
    assertFalse(currentUser.isPresent(), "current user unset in global context");
  }

  @Test
  @DisplayName("all test users enabled in SAM")
  void checkEnabled() throws IOException {
    // check that each test user is enabled in SAM
    for (TestUsers testUser : Arrays.asList(TestUsers.values())) {
      // login the user, so we have their credentials
      testUser.login();

      // build a SAM client with the test user's credentials
      SamService samService = new SamService(Context.getServer(), Context.requireUser());

      // check that the user is enabled
      UserStatusInfo userStatusInfo = samService.getUserInfo();
      assertTrue(userStatusInfo.getEnabled(), "test user is enabled in SAM");

      // `terra auth revoke`
      TestCommand.runCommandExpectSuccess("auth", "revoke");
    }
  }
}
