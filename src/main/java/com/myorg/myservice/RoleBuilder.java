package com.myorg.myservice;

import java.util.Arrays;

import com.myorg.MyOrgConstants;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.IPrincipal;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;

public class RoleBuilder {

	public static Role createCodeBuildRole(Construct scope, String context, IPrincipal pipelinePrincipal,
			IPrincipal buildPrincipal) {

		Role codebuildRole = BuildRole.roleForCodeBuild(scope, context, pipelinePrincipal);

		codebuildRole.getAssumeRolePolicy()
				.addStatements(PolicyStatement.Builder.create().actions(Arrays.asList("sts:AssumeRole"))
						.principals(Arrays.asList(pipelinePrincipal, buildPrincipal)).effect(Effect.ALLOW).build());

		return Role.Builder.create(codebuildRole, "CodebuildRole").assumedBy(pipelinePrincipal).build();
	}

	public static Role createPipelineRole(Construct scope, String context, IPrincipal pipelinePrincipal,
			IPrincipal deployPrincipal) {

		Role codedeployRole = BuildRole.roleForCodePipeline(scope, context, pipelinePrincipal);

		codedeployRole.getAssumeRolePolicy()
				.addStatements(PolicyStatement.Builder.create().actions(Arrays.asList("sts:AssumeRole"))
						.principals(Arrays.asList(pipelinePrincipal)).effect(Effect.ALLOW).build());

		return Role.Builder.create(codedeployRole, "CodepipelineRole").assumedBy(pipelinePrincipal).build();

	}

	public static Role createInstanceRole(Construct scope, String context, IPrincipal pipelinePrincipal,
			IPrincipal deployPrincipal) {

		Role instanceRole = BuildRole.roleForAutoScaling(scope, context, pipelinePrincipal);

		instanceRole.getAssumeRolePolicy()
				.addStatements(PolicyStatement.Builder.create().actions(Arrays.asList("sts:AssumeRole"))
						.principals(Arrays.asList(pipelinePrincipal)).effect(Effect.ALLOW).build());

		return Role.Builder.create(instanceRole, "InstanceRole").assumedBy(pipelinePrincipal).build();

	}

	public static Role createCodeDeployRole(Construct scope, String context, IPrincipal pipelinePrincipal,
			IPrincipal deployPrincipal) {

		Role codedeployRole = BuildRole.roleForCodeDeploy(scope, context, pipelinePrincipal);

		codedeployRole.getAssumeRolePolicy()
				.addStatements(PolicyStatement.Builder.create().actions(Arrays.asList("sts:AssumeRole"))
						.principals(Arrays.asList(pipelinePrincipal, deployPrincipal)).effect(Effect.ALLOW).build());

		return Role.Builder.create(codedeployRole, "CodedeployRole").assumedBy(pipelinePrincipal).build();
	}

	private static class BuildRole {
		static Role roleForCodeBuild(final Construct scope, String context, IPrincipal principal) {
			return new Role(scope, MyOrgConstants.name(context, "role-build"), RoleProps.builder().description(
					"this role contains all policy needed for code build. e.g.: access to S3, access to parameter store. access to code commit")
					.managedPolicies(Arrays.asList(
							new IManagedPolicy[] { ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMReadOnlyAccess"),
									ManagedPolicy.fromAwsManagedPolicyName("AmazonS3FullAccess"),
									ManagedPolicy.fromAwsManagedPolicyName("AWSCodeCommitReadOnly") }))
					.roleName(MyOrgConstants.name(context, "role-build")).assumedBy(principal).build());
		}

		static Role roleForCodePipeline(final Construct scope, String context, IPrincipal principal) {
			return new Role(scope, MyOrgConstants.name(context, "role-pipeline"), RoleProps.builder().description(
					"this role contains all policy needed for code pipeline. e.g.: access to S3, access to parameter store. access to code commit")
					.managedPolicies(Arrays.asList(
							new IManagedPolicy[] { ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMReadOnlyAccess"),
									ManagedPolicy.fromAwsManagedPolicyName("AmazonS3FullAccess"),
									ManagedPolicy.fromAwsManagedPolicyName("AWSCodeCommitReadOnly"),
									ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployRole") }))
					.roleName(MyOrgConstants.name(context, "role-pipeline")).assumedBy(principal).build());
		}

		static Role roleForAutoScaling(final Construct scope, String context, IPrincipal principal) {
			return new Role(scope, MyOrgConstants.name(context, "role-instance"), RoleProps.builder().description(
					"this role contains all policy needed for code pipeline. e.g.: access to S3, access to parameter store. access to code commit")
					.managedPolicies(Arrays.asList(new IManagedPolicy[] {
							ManagedPolicy.fromAwsManagedPolicyName("AutoScalingServiceRolePolicy"),
							ManagedPolicy.fromAwsManagedPolicyName("AmazonRDSFullAccess"),
							ManagedPolicy.fromAwsManagedPolicyName("AmazonS3FullAccess") }))
					.roleName(MyOrgConstants.name(context, "role-instance")).assumedBy(principal).build());
		}

		static Role roleForCodeDeploy(final Construct scope, String context, IPrincipal principal) {
			return new Role(scope, MyOrgConstants.name(context, "role-deploy"), RoleProps.builder().description(
					"this role contains all policy needed for code deploy. e.g autoscaling, ec2, sns, cloudwatch, elb, tags")
					.managedPolicies(Arrays.asList(
							new IManagedPolicy[] { ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployRole") }))
					.roleName(MyOrgConstants.name(context, "role-deploy")).assumedBy(principal).build());
		}
	}
}
