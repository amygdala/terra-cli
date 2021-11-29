package bio.terra.cli.serialization.persisted.resource;

import bio.terra.cli.businessobject.resource.GcsBucketFile;
import bio.terra.cli.serialization.persisted.PDResource;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * External representation of a workspace GCS bucket file resource for writing to disk.
 *
 * <p>This is a POJO class intended for serialization. This JSON format is not user-facing.
 *
 * <p>See the {@link GcsBucketFile} class for a bucket file's internal representation.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonDeserialize(builder = PDGcsBucketFile.Builder.class)
public class PDGcsBucketFile extends PDResource {
  public final String bucketName;
  public final String filePath;

  /** Serialize an instance of the internal class to the disk format. */
  public PDGcsBucketFile(GcsBucketFile internalObj) {
    super(internalObj);
    this.bucketName = internalObj.getBucketName();
    this.filePath = internalObj.getFilePath();
  }

  private PDGcsBucketFile(Builder builder) {
    super(builder);
    this.bucketName = builder.bucketName;
    this.filePath = builder.filePath;
  }

  /** Deserialize the format for writing to disk to the internal representation of the resource. */
  public GcsBucketFile deserializeToInternal() {
    return new GcsBucketFile(this);
  }

  @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
  public static class Builder extends PDResource.Builder {
    private String bucketName;
    private String filePath;

    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder filePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    /** Call the private constructor. */
    public PDGcsBucketFile build() {
      return new PDGcsBucketFile(this);
    }

    /** Default constructor for Jackson. */
    public Builder() {}
  }
}
