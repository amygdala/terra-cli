package bio.terra.cli.serialization.userfacing;

import bio.terra.cli.businessobject.WorkspaceUser;
import bio.terra.cli.businessobject.WorkspaceUser.Role;
import bio.terra.cli.utils.UserIO;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * External representation of a workspace user (i.e. someone who the workspace is shared with) for
 * command input/output.
 *
 * <p>This is a POJO class intended for serialization. This JSON format is user-facing.
 *
 * <p>See the {@link WorkspaceUser} class for a workspace user's internal representation.
 */
@JsonDeserialize(builder = UFWorkspaceUser.Builder.class)
public class UFWorkspaceUser {
  public final String email;
  public final List<Role> roles;

  public UFWorkspaceUser(WorkspaceUser internalObj) {
    this.email = internalObj.getEmail();
    this.roles = internalObj.getRoles();
  }

  /** Constructor for Jackson deserialization during testing. */
  private UFWorkspaceUser(Builder builder) {
    this.email = builder.email;
    this.roles = builder.roles;
  }

  /** Print out this object in text format. */
  public void print() {
    PrintStream OUT = UserIO.getOut();
    String rolesStr = roles.stream().map(Role::toString).collect(Collectors.joining(","));
    OUT.println(email + ": " + rolesStr);
  }

  @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
  public static class Builder {
    private String email;
    private List<Role> roles;

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder roles(List<Role> roles) {
      this.roles = roles;
      return this;
    }

    /** Call the private constructor. */
    public UFWorkspaceUser build() {
      return new UFWorkspaceUser(this);
    }

    /** Default constructor for Jackson. */
    public Builder() {}
  }
}
