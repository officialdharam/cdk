package com.myorg.myservice;

import java.util.HashMap;
import java.util.Map;

import com.myorg.MyOrgConstants;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;

public class MyOrgDeployment extends App {

	static Environment makeEnv(String account, String region) {
		return Environment.builder().account(account).region(region).build();
	}

	public static void main(final String argv[]) {

		Environment env = makeEnv("<your amazon account number>", "us-east-1");
		String context = "beta";
		try {
			MyOrgDeployment app = new MyOrgDeployment();

			Map<String, String> vpcConfig = new HashMap<>();
			vpcConfig.put(MyOrgConstants.CIDR, "172.31.0.0/16");
			vpcConfig.put(MyOrgConstants.MAX_AZ, "2");
			vpcConfig.put(MyOrgConstants.PUBLIC_IP, String.valueOf(true));

			new ContinuousIntegrationService(app, "beta", env, context, vpcConfig);

			// load balancer

			app.synth();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
