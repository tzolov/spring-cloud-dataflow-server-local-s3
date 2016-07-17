package org.springframework.cloud.dataflow.server.local.s3;

import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.core.io.Resource;

/**
 * Created by tzoloc on 7/16/16.
 */
public class Main {
  public static void main(String[] args) {
    DelegatingResourceLoader rl = new S3DataFlowServerAutoConfiguration().delegatingResourceLoader(null);
    Resource r = rl.getResource("s3://walbrook-maven/snapshot/io/pivotal/walbrook/balance-source/0.0.3-SNAPSHOT/balance-source-0.0.3-20160714.133004-1.jar");
//    Resource r = rl.getResource("s3://walbrook-maven/balance-source.jar");
    System.out.println(r);
  }
}
