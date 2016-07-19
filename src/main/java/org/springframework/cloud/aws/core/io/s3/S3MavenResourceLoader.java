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
package org.springframework.cloud.aws.core.io.s3;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import com.amazonaws.services.s3.AmazonS3;

public class S3MavenResourceLoader extends SimpleStorageResourceLoaderEx {

  private final S3MavenResourcePathResolver s3MavenUtil;

  public S3MavenResourceLoader(AmazonS3 amazonS3, String s3MavenRepo, ResourceLoader delegate) {
    super(amazonS3, delegate);
    this.s3MavenUtil = new S3MavenResourcePathResolver(amazonS3, s3MavenRepo);
  }

  public S3MavenResourceLoader(AmazonS3 amazonS3, String s3MavenRepo, ClassLoader classLoader) {
    super(amazonS3, classLoader);
    this.s3MavenUtil = new S3MavenResourcePathResolver(amazonS3, s3MavenRepo);
  }

  public S3MavenResourceLoader(AmazonS3 amazonS3, String s3MavenRepo) {
    this(amazonS3, s3MavenRepo, ClassUtils.getDefaultClassLoader());
  }

  @Override
  public Resource getResource(String location) {
    return super.getResource(this.s3MavenUtil.getLatestResourcePath(location.replace("s3-maven://", "").trim()));
  }
}