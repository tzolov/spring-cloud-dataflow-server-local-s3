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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.aws.core.support.documentation.RuntimeUse;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.ClassUtils;

import com.amazonaws.services.s3.AmazonS3;

/**
 * This class overloads the @{@link SimpleStorageResourceLoader} to allow creation of @{@link SimpleStorageResource}
 * instances with overridden {@link SimpleStorageResource#getFilename()} method.
 * <p>
 * The S3 ObjectKey specification: <a href="http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html#object-keys">object-keys</a>
 * permits the "/" character as part of the object key. For the file systems though this character means folder separator.
 * The "/" character in the name causes problems with the {@link org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader#getResource(String)}
 * implementation. Later assumes that the file name excludes any folder information. In result it fails to cache of the remote S3 object.
 */
public class SimpleStorageResourceLoaderEx implements ResourceLoader, InitializingBean {

  private final AmazonS3 amazonS3;
  private final ResourceLoader delegate;

  /**
   * <b>IMPORTANT:</b> If a task executor is set with an unbounded queue there will be a huge memory consumption. The
   * reason is that each multipart of 5MB will be put in the queue to be uploaded. Therefore a bounded queue is recommended.
   */
  private TaskExecutor taskExecutor;

  public SimpleStorageResourceLoaderEx(AmazonS3 amazonS3, ResourceLoader delegate) {
    this.amazonS3 = amazonS3;
    this.delegate = delegate;
  }

  public SimpleStorageResourceLoaderEx(AmazonS3 amazonS3, ClassLoader classLoader) {
    this.amazonS3 = amazonS3;
    this.delegate = new DefaultResourceLoader(classLoader);
  }

  public SimpleStorageResourceLoaderEx(AmazonS3 amazonS3) {
    this(amazonS3, ClassUtils.getDefaultClassLoader());
  }

  @RuntimeUse
  public void setTaskExecutor(TaskExecutor taskExecutor) {
    this.taskExecutor = taskExecutor;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (this.taskExecutor == null) {
      this.taskExecutor = new SyncTaskExecutor();
    }
  }

  @Override
  public Resource getResource(String location) {

    if (SimpleStorageNameUtils.isSimpleStorageResource(location)) {
      return new SimpleStorageResource(this.amazonS3, SimpleStorageNameUtils.getBucketNameFromLocation(location),
          SimpleStorageNameUtils.getObjectNameFromLocation(location), this.taskExecutor,
          SimpleStorageNameUtils.getVersionIdFromLocation(location)) {

        /**
         * S3 ObjectKey specification permits the "/" character as part of the object key. The {@link org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader#getResource(String)}
         * implementation uses the resource filename to create local copy of the remote S3 object and it fails due to the "/" in the name.
         */
        @Override public String getFilename() {
          return super.getFilename().replace("/", "_");
        }
      };
    }

    return this.delegate.getResource(location);
  }

  @Override
  public ClassLoader getClassLoader() {
    return this.delegate.getClassLoader();
  }
}