package bio.terra.cli.service;

import bio.terra.cli.businessobject.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembershipV2;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyResponseEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility methods for talking to the future Spend Profile Manager service. */
public class SpendProfileManagerService {
  private static final Logger logger = LoggerFactory.getLogger(SamService.class);

  // there currently is no SPM service, so this class just wraps calls to SAM
  // keep a reference to the SAM service instance here
  private final SamService samService;

  // these are the resource type and id of the default spend profile used by WSM. currently there is
  // only one SAM resource used. in the future, if this varies per environment, move this resource
  // id into the server specification
  private static final String SPEND_PROFILE_RESOURCE_TYPE = "spend-profile";

  /**
   * Factory method for class that talks to the SPM service. The user must be authenticated. Methods
   * in this class will use its credentials to call authenticated endpoints. This factory method
   * uses the current context's server and user.
   */
  public static SpendProfileManagerService fromContext() {
    return new SpendProfileManagerService(SamService.fromContext());
  }

  private SpendProfileManagerService(SamService samService) {
    this.samService = samService;
  }

  /**
   * These are the policies for the WSM default spend profile resource. They can be looked up
   * dynamically by calling the SAM "/api/resources/v1/{resourceTypeName}/{resourceId}/policies" GET
   * endpoint. They are hard-coded here to show the possible values in the CLI usage help. (And in
   * the eventual SPM service, this may be an enum in their API?)
   */
  public enum SpendProfilePolicy {
    OWNER,
    USER;

    /** Helper method to get the SAM string that corresponds to this spend profile policy. */
    public String getSamPolicy() {
      return name().toLowerCase();
    }
  }

  /**
   * Add the specified email to the WSM default spend profile resource in SAM.
   *
   * @param email email of the user or group to add
   */
  public void enableUserForDefaultSpendProfile(SpendProfilePolicy policy, String email) {
    samService.addUserToResourceOrInviteUser(
        SPEND_PROFILE_RESOURCE_TYPE,
        Context.getServer().getWsmDefaultSpendProfile(),
        policy.getSamPolicy(),
        email);
  }

  /**
   * Remove the specified email from the WSM default spend profile resource in SAM.
   *
   * @param email email of the user or group to remove
   */
  public void disableUserForDefaultSpendProfile(SpendProfilePolicy policy, String email) {
    samService.removeUserFromResource(
        SPEND_PROFILE_RESOURCE_TYPE,
        Context.getServer().getWsmDefaultSpendProfile(),
        policy.getSamPolicy(),
        email);
  }

  /**
   * List the members of the WSM default spend profile resource in SAM.
   *
   * @return a list of policies with their member emails
   */
  public List<AccessPolicyResponseEntry> listUsersOfDefaultSpendProfile() {
    return samService.listPoliciesForResource(
        SPEND_PROFILE_RESOURCE_TYPE, Context.getServer().getWsmDefaultSpendProfile());
  }

  /** Create a new SAM resource for the WSM default spend profile of the current server. */
  public void createDefaultSpendProfile() {
    // create two policies (owner, user) and make sure the current user is an owner
    Map<String, AccessPolicyMembershipV2> policies = new HashMap<>();
    policies.put(
        "owner",
        new AccessPolicyMembershipV2()
            .addRolesItem("owner")
            .addMemberEmailsItem(Context.requireUser().getEmail()));
    policies.put("user", new AccessPolicyMembershipV2().addRolesItem("user"));

    samService.createResource(
        SPEND_PROFILE_RESOURCE_TYPE, Context.getServer().getWsmDefaultSpendProfile(), policies);
  }

  /** Delete the SAM resource for the WSM default spend profile of the current server. */
  public void deleteDefaultSpendProfile() {
    samService.deleteResource(
        SPEND_PROFILE_RESOURCE_TYPE, Context.getServer().getWsmDefaultSpendProfile());
  }
}
