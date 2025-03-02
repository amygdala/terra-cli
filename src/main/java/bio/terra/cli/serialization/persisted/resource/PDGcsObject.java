package bio.terra.cli.serialization.persisted.resource;

import bio.terra.cli.businessobject.resource.GcsObject;
import bio.terra.cli.serialization.persisted.PDResource;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * External representation of a workspace GCS bucket object resource for writing to disk.
 *
 * <p>This is a POJO class intended for serialization. This JSON format is not user-facing.
 *
 * <p>See the {@link GcsObject} class for a bucket object's internal representation.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonDeserialize(builder = PDGcsObject.Builder.class)
public class PDGcsObject extends PDResource {
  public final String bucketName;
  public final String objectName;

  /** Serialize an instance of the internal class to the disk format. */
  public PDGcsObject(GcsObject internalObj) {
    super(internalObj);
    this.bucketName = internalObj.getBucketName();
    this.objectName = internalObj.getObjectName();
  }

  private PDGcsObject(Builder builder) {
    super(builder);
    this.bucketName = builder.bucketName;
    this.objectName = builder.objectName;
  }

  /** Deserialize the format for writing to disk to the internal representation of the resource. */
  public GcsObject deserializeToInternal() {
    return new GcsObject(this);
  }

  @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
  public static class Builder extends PDResource.Builder {
    private String bucketName;
    private String objectName;

    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder objectName(String objectName) {
      this.objectName = objectName;
      return this;
    }

    /** Call the private constructor. */
    public PDGcsObject build() {
      return new PDGcsObject(this);
    }

    /** Default constructor for Jackson. */
    public Builder() {}
  }
}
