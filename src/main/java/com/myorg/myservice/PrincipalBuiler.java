package com.myorg.myservice;

import software.amazon.awscdk.services.iam.IPrincipal;
import software.amazon.awscdk.services.iam.ServicePrincipal;

public class PrincipalBuiler {

	public static IPrincipal createPipelinePrincipal(String region) {
		return ServicePrincipal.Builder.create("codepipeline.amazonaws.com").region(region).build();
	}

	public static IPrincipal createCodebuildPrincipal(String region) {
		return ServicePrincipal.Builder.create("codebuild.amazonaws.com").region(region).build();
	}

	public static IPrincipal createCodedeployPrincipal(String region) {
		return ServicePrincipal.Builder.create("codedeploy.amazonaws.com").region(region).build();
	}

}
