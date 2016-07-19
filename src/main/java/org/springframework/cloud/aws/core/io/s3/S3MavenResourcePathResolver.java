package org.springframework.cloud.aws.core.io.s3;

import org.springframework.lang.UsesJava8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given an s3 maven repository (such as s3://walbrook-maven/snapshot) and maven artifact coordinates
 * (such as io.pivotal.walbrook:balance-source:0.0.3-SNAPSHOT), the {@link S3MavenResourcePathResolver#getLatestResourcePath(String)}
 * resolves the absolute resource path in S3.
 * <p>
 * For example the above maven repo and artifact coordinates will be resolved to an absolute S3 location like this:
 * s3://walbrook-maven/snapshot/io/pivotal/walbrook/balance-source/0.0.3-SNAPSHOT/balance-source-0.0.3-20160718.163849-6.jar
 * <p>
 * Where balance-source-0.0.3-20160718.163849-6.jar is the most recent artifact version in the /0.0.3-SNAPSHOT/ S3 folder.
 */
@UsesJava8
public class S3MavenResourcePathResolver {

  private static final String S3_PROTOCOL_PREFIX = "s3://";
  private static final String PATH_DELIMITER = "/";
  private static final String KEY_FORMAT = "%s/%s";
  private static final String RESOURCE_FORMAT = "%s(.*)";
  private static final String COLUMN = ":";
  private static final String DOT = ".";
  private static Logger logger = LoggerFactory.getLogger(S3MavenResourcePathResolver.class);
  private volatile AmazonS3 amazonS3;

  private volatile String bucketName;

  private volatile String baseDirectory;

  /**
   * @param amazonS3      Amazon S3 client
   * @param bucketName    maven repository S3 bucket name
   * @param baseDirectory Usually 'snapshot' or 'release'.
   */

  public S3MavenResourcePathResolver(AmazonS3 amazonS3, String bucketName, String baseDirectory) {
    this.amazonS3 = amazonS3;
    this.bucketName = bucketName;
    this.baseDirectory = baseDirectory;
  }

  /**
   * @param amazonS3          Amazon S3 client
   * @param mavenS3Repository S3 maven repository location in the format s3://bucket-name/base-directory.
   *                          Usually the base-directory is 'release' or 'snapshot'.
   */
  public S3MavenResourcePathResolver(AmazonS3 amazonS3, String mavenS3Repository) {
    this.amazonS3 = amazonS3;
    this.bucketName = SimpleStorageNameUtils.getBucketNameFromLocation(mavenS3Repository);
    this.baseDirectory = SimpleStorageNameUtils.getObjectNameFromLocation(mavenS3Repository);
  }

  public static void main(String[] args) {
    S3MavenResourcePathResolver resolver = new S3MavenResourcePathResolver(new AmazonS3Client(), "s3://walbrook-maven/snapshot/");
    resolver.getLatestResourcePath("io.pivotal.walbrook:balance-source:0.0.3-SNAPSHOT");
  }

  /**
   * @param mavenResource maven resource coordinates in format: <groupId>:<artifactId>:<version>. Group Id is in the format io.pivotal.my-package ...
   * @return Returns the absolute S3 path of the most recent version of this maven resource.
   */
  public String getLatestResourcePath(String mavenResource) {
    String[] mavenResourceParts = mavenResource.split(COLUMN);
    String groupId = mavenResourceParts[0];
    String artifactId = mavenResourceParts[1];
    String version = mavenResourceParts[2];
    String mavenResourceDir = groupId.replace(DOT, PATH_DELIMITER) + PATH_DELIMITER + artifactId + PATH_DELIMITER + version;

    List<String> resources = listDirectory(mavenResourceDir + PATH_DELIMITER);

    logger.info(resources.toString());

    String mostRecentResourceJarFilename = resources.stream().filter(
        p -> p.endsWith(".jar") && !p.endsWith("javadoc.jar") && !p.endsWith("sources.jar"))
        .reduce((r1, r2) -> (getResourceTime(mavenResourceDir, r1) > getResourceTime(mavenResourceDir, r2)) ? r1 : r2).get();

    String absolutePath = S3_PROTOCOL_PREFIX + bucketName + PATH_DELIMITER
        + baseDirectory + PATH_DELIMITER + mavenResourceDir + PATH_DELIMITER + mostRecentResourceJarFilename;

    logger.info(absolutePath);

    return absolutePath;
  }

  private long getResourceTime(String mavenResourceS3Directory, String resource) {
    return getObjectMetadata(mavenResourceS3Directory + PATH_DELIMITER + resource).getLastModified().getTime();
  }

  private List<String> listDirectory(String directory) throws RuntimeException {
    List<String> directoryContents = new ArrayList<String>();

    try {
      String prefix = getKey(directory);
      Pattern pattern = Pattern.compile(String.format(RESOURCE_FORMAT, prefix));

      ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
          .withBucketName(this.bucketName)
          .withPrefix(prefix)
          .withDelimiter(PATH_DELIMITER);

      ObjectListing objectListing;

      objectListing = this.amazonS3.listObjects(listObjectsRequest);
      directoryContents.addAll(getResourceNames(objectListing, pattern));

      while (objectListing.isTruncated()) {
        objectListing = this.amazonS3.listObjects(listObjectsRequest);
        directoryContents.addAll(getResourceNames(objectListing, pattern));
      }

      return directoryContents;
    } catch (AmazonServiceException e) {
      throw new RuntimeException(String.format("'%s' does not exist", directory), e);
    }
  }

  private String getKey(String resourceName) {
    return String.format(KEY_FORMAT, this.baseDirectory, resourceName);
  }

  private List<String> getResourceNames(ObjectListing objectListing, Pattern pattern) {
    List<String> resourceNames = new ArrayList<String>();

    for (String commonPrefix : objectListing.getCommonPrefixes()) {
      resourceNames.add(getResourceName(commonPrefix, pattern));
    }

    for (S3ObjectSummary s3ObjectSummary : objectListing.getObjectSummaries()) {
      resourceNames.add(getResourceName(s3ObjectSummary.getKey(), pattern));
    }

    return resourceNames;
  }

  private ObjectMetadata getObjectMetadata(String resourceName) {
    return this.amazonS3.getObjectMetadata(this.bucketName, getKey(resourceName));
  }

  private String getResourceName(String key, Pattern pattern) {
    Matcher matcher = pattern.matcher(key);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return key;
  }
}
