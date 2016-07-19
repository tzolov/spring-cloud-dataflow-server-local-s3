/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.server.local.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.aws.core.io.s3.S3MavenResourceLoader;
import org.springframework.cloud.aws.core.io.s3.SimpleStorageResourceLoaderEx;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResourceLoader;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GroupGrantee;
import com.amazonaws.services.s3.model.Permission;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows registering apps stored in maven repositories as well as S3 (Simple Store Storage)
 * http://docs.spring.io/spring-cloud-dataflow/docs/1.0.0.RELEASE/reference/html/getting-started-deploying-spring-cloud-dataflow.html#_deploying_local
 * <p>
 * Credentials would be needed to access the Amazon S3 app store. A
 * credentials provider chain will be used that searches for credentials in
 * this order:
 * <ul>
 * <li>Command line Properties - --aws.accessKeyId and --aws.secretKey</li>
 * <li>Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_KEY</li>
 * <li>Java System Properties - aws.accessKeyId and aws.secretKey</li>
 * <li>Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI</li>
 * <li>Instance Profile Credentials - delivered through the Amazon EC2 metadata service</li>
 * </ul>
 * <p>
 * <p>
 * If no credentials are found in the chain, the server will attempt to
 * work in an anonymous mode where requests aren't signed. For example:
 * <ul>
 * <li>If an Amazon S3 bucket has {@link Permission#Read} permission for the
 * {@link GroupGrantee#AllUsers} group, the SCDF server can get app jar without credentials.</li>
 * </ul>
 * </p>
 * <p>
 * You can force the server to operate in an anonymous mode, and skip the credentials
 * provider chain, by passing in <code>null</code> for the credentials.
 * </p>
 */
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class S3DataFlowServerAutoConfiguration {

  @Value("${aws.accessKeyId:null}")
  private String accessKeyId;

  @Value("${aws.secretKey:null}")
  private String secretKey;

  @Value("${s3.maven:s3://empty/empty}")
  private String s3MavenRepository;

  @Bean
  public MavenResourceLoader mavenResourceLoader(MavenProperties properties) {
    return new MavenResourceLoader(properties);
  }

  @Bean
  public MavenProperties mavenProperties() {
    return new MavenConfigurationProperties();
  }

  @Bean
  public SimpleStorageResourceLoaderEx s3ResourceLoader() {
    return new SimpleStorageResourceLoaderEx(amazonS3Client());
  }

  @Bean
  public S3MavenResourceLoader s3MavenResourceLoader() {
    return new S3MavenResourceLoader(amazonS3Client(), s3MavenRepository);
  }

  @Bean
  public AmazonS3Client amazonS3Client() {

    if (!StringUtils.isEmpty(accessKeyId) && !StringUtils.isEmpty(secretKey)) {
      return new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretKey));
    }

    return new AmazonS3Client();
  }

  @Bean
  public DelegatingResourceLoader delegatingResourceLoader(MavenResourceLoader mavenResourceLoader) {
    Map<String, ResourceLoader> loaders = new HashMap<>();
    loaders.put("s3", s3ResourceLoader());
    loaders.put("s3-maven", s3MavenResourceLoader());
    loaders.put("maven", mavenResourceLoader);
    return new DelegatingResourceLoader(loaders);
  }

  @ConfigurationProperties(prefix = "maven")
  static class MavenConfigurationProperties extends MavenProperties {
  }
}
