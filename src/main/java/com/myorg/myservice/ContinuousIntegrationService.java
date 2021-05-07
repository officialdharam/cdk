package com.myorg.myservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.myorg.MyOrgConstants;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.PipelineProject;
import software.amazon.awscdk.services.codecommit.IRepository;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.codedeploy.AutoRollbackConfig;
import software.amazon.awscdk.services.codedeploy.InstanceTagSet;
import software.amazon.awscdk.services.codedeploy.LoadBalancer;
import software.amazon.awscdk.services.codedeploy.ServerApplication;
import software.amazon.awscdk.services.codedeploy.ServerApplicationProps;
import software.amazon.awscdk.services.codedeploy.ServerDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.ServerDeploymentGroup;
import software.amazon.awscdk.services.codedeploy.ServerDeploymentGroupProps;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeDeployServerDeployAction;
import software.amazon.awscdk.services.ec2.IPeer;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.LookupMachineImage;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.IpAddressType;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.iam.AccountRootPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IPrincipal;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;

/**
 * One service which builds a pipeline stack
 * 
 * @author dharam
 *
 */
public class ContinuousIntegrationService extends Stack {

	private static final String ADMIN_CIDR_1 = "217.67.238.176/32";
	private static final String ADMIN_CIDR_2 = "217.67.238.175/32";
	IPrincipal buildPrincipal = null;
	IPrincipal pipelinePrincipal = null;
	IPrincipal deployPrincipal = null;

	public ContinuousIntegrationService(@NotNull Construct scope, @NotNull String id, Environment env, String context,
			Map<String, String> vpcConfig) {

		super(scope, id, StackProps.builder().env(env).build());

		buildPrincipal = PrincipalBuiler.createCodebuildPrincipal(env.getRegion());
		pipelinePrincipal = PrincipalBuiler.createPipelinePrincipal(env.getRegion());
		deployPrincipal = PrincipalBuiler.createCodedeployPrincipal(env.getRegion());

		Key kmsKey = createKmsKey(context, pipelinePrincipal, buildPrincipal, deployPrincipal);
		Bucket bucket = createS3Bucket(context, kmsKey);

		IVpc vpc = createVpc(context, vpcConfig);
		SecurityGroup sg = createSg(vpc, context);
		AutoScalingGroup asg = createAsg(context, vpc, env.getRegion());
		asg.addSecurityGroup(sg);

		ApplicationTargetGroup atg = createApplicationTargetGroup(context, vpc, asg);
		// SOURCE
		IRepository sourceRepo = Repository.fromRepositoryName(this, MyOrgConstants.name(context, "pipeline-repo"),
				"slingsonic");

		// BUILD
		PipelineProject buildProject = createBuildProject(context);
		buildProject.addToRolePolicy(PolicyStatement.Builder.create().effect(Effect.ALLOW).resources(Arrays.asList("*"))
				.actions(Arrays.asList("ssm:Describe*", "ssm:Get*", "ssm:List*")).build());

		Artifact sourceOutput = new Artifact(MyOrgConstants.name(context, "pipeline-source-output"));
		Artifact buildOutput = new Artifact(MyOrgConstants.name(context, "pipeline-build-output"));

		// DEPLOY
		ServerApplication application = createServerApplication(context);
		ServerDeploymentGroup deploymentGroup = createDeploymentGroup(context, asg, atg, application);

		// Assemble pipeline
		assemblePipeline(context, bucket, sourceRepo, buildProject, deploymentGroup, sourceOutput, buildOutput);

		// Add load balancer
		createLoadBalancer(vpc, context, sg, atg);

	}

	private ServerApplication createServerApplication(String context) {
		return new ServerApplication(this, MyOrgConstants.name(context, "application"),
				ServerApplicationProps.builder().applicationName(MyOrgConstants.name(context, "application")).build());
	}

	private void assemblePipeline(String context, Bucket bucket, IRepository sourceRepo, PipelineProject buildProject,
			ServerDeploymentGroup deploymentGroup, Artifact sourceOutput, Artifact buildOutput) {
		Pipeline.Builder.create(this, MyOrgConstants.name(context, "pipeline"))
				.pipelineName(MyOrgConstants.name(context, "pipeline")).stages(Arrays.asList(
						// source stage
						StageProps.builder().stageName(MyOrgConstants.name(context, "pipeline-source"))
								.actions(Arrays.asList(CodeCommitSourceAction.Builder.create().actionName("Source")
										.repository(sourceRepo).output(sourceOutput).build()))
								.build(),
						// build stage
						StageProps.builder().stageName(MyOrgConstants.name(context, "pipeline-build"))
								.actions(Arrays.asList(
										CodeBuildAction.Builder.create().actionName("Build").project(buildProject)
												.input(sourceOutput).outputs(Arrays.asList(buildOutput)).build()))
								.build(),
						// deploy stage
						StageProps.builder().stageName(MyOrgConstants.name(context, "pipeline-deploy"))
								.actions(Arrays.asList(
										CodeDeployServerDeployAction.Builder.create().deploymentGroup(deploymentGroup)
												.actionName("Deploy").input(buildOutput).build()))
								.build()))

				.artifactBucket(bucket).build();
	}

	private PipelineProject createBuildProject(String context) {
		return PipelineProject.Builder.create(this, MyOrgConstants.name(context, "pipeline-build"))
				.projectName(MyOrgConstants.name(context, "pipeline-build"))
				.buildSpec(BuildSpec.fromSourceFilename("buildspec-" + context + ".yml"))
				.description("Code build for environment : " + context)
				.environment(BuildEnvironment.builder().buildImage(LinuxBuildImage.AMAZON_LINUX_2).build()).build();
	}

	@SuppressWarnings("unchecked")
	private ServerDeploymentGroup createDeploymentGroup(String context, AutoScalingGroup asg,
			ApplicationTargetGroup atg, ServerApplication application) {

		HashMap<String, List<String>> instanceTagSet = new HashMap<String, List<String>>();
		instanceTagSet.put("Name", Arrays.asList(MyOrgConstants.instanceNameTag(context)));
		instanceTagSet.put("name", Arrays.asList(MyOrgConstants.instanceNameTag(context)));

		return new ServerDeploymentGroup(this, MyOrgConstants.name(context, "deployment-group"),
				ServerDeploymentGroupProps.builder()
						.deploymentGroupName(MyOrgConstants.name(context, "deployment-group"))
						.deploymentConfig(ServerDeploymentConfig.HALF_AT_A_TIME)
						.autoRollback(AutoRollbackConfig.builder().failedDeployment(Boolean.TRUE).build())
						.installAgent(true).application(application).autoScalingGroups(Arrays.asList(asg))
						.loadBalancer(LoadBalancer.application(atg)).ec2InstanceTags(new InstanceTagSet(instanceTagSet))
						.build());
	}

	private Vpc createVpc(String context, Map<String, String> vpcConfig) {
		return Vpc.Builder.create(this, MyOrgConstants.name(context, "vpc")).cidr(vpcConfig.get(MyOrgConstants.CIDR))
				.enableDnsHostnames(Boolean.parseBoolean(vpcConfig.get(MyOrgConstants.PUBLIC_IP)))
				.enableDnsSupport(true).maxAzs(Integer.valueOf(vpcConfig.get(MyOrgConstants.MAX_AZ)))
				.subnetConfiguration(Arrays.asList(new SubnetConfiguration[] { new MyOrgSubnetConfiguration.Builder()
						.cidrMask(24).subnetName(MyOrgConstants.name(context, "sn-public"))
						.subnetType(SubnetType.PUBLIC).build() }))
				.build();
	}

	private ApplicationLoadBalancer createLoadBalancer(IVpc vpc, String context, SecurityGroup sg,
			ApplicationTargetGroup atg) {
		ApplicationLoadBalancer loadBalancer = ApplicationLoadBalancer.Builder
				.create(this, MyOrgConstants.name(context, "alb")).vpc(vpc).internetFacing(Boolean.TRUE)
				.loadBalancerName(MyOrgConstants.name(context, "alb")).securityGroup(sg)
				.ipAddressType(IpAddressType.IPV4).idleTimeout(Duration.seconds(60)).http2Enabled(Boolean.TRUE).build();

		loadBalancer.addListener(MyOrgConstants.name(context, "listener-http"),
				BaseApplicationListenerProps.builder().defaultTargetGroups(Arrays.asList(atg)).open(Boolean.TRUE)
						.protocol(ApplicationProtocol.HTTP).port(80).build());
//		lb.addListener(MyOrgConstants.name(context, "listener-https"),
//				BaseApplicationListenerProps.builder().defaultTargetGroups(Arrays.asList(atg)).open(Boolean.TRUE)
//						.protocol(ApplicationProtocol.HTTPS).port(443).build());
		return loadBalancer;
	}

	private ApplicationTargetGroup createApplicationTargetGroup(String context, IVpc vpc, AutoScalingGroup asg) {
		HealthCheck healthCheck = HealthCheck.builder().enabled(Boolean.TRUE).healthyHttpCodes("200")
				.unhealthyThresholdCount(5).interval(Duration.seconds(25)).timeout(Duration.seconds(20)).path("/")
				.port("8080").protocol(Protocol.HTTP).build();

		return ApplicationTargetGroup.Builder.create(this, MyOrgConstants.name(context, "tg"))
				.targetGroupName(MyOrgConstants.name(context, "tg")).vpc(vpc).port(8080)
				.deregistrationDelay(Duration.seconds(120)).targetType(TargetType.INSTANCE)
				.healthCheck(healthCheck).targets(Arrays.asList(asg)).build();
	}

	private AutoScalingGroup createAsg(String context, IVpc vpc, String region) {
		return AutoScalingGroup.Builder.create(this, MyOrgConstants.name(context, "asg")).associatePublicIpAddress(true)
				.vpc(vpc).instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO)).minCapacity(1)
				.desiredCapacity(2).maxCapacity(3).userData(UserData.custom(userData(region)))
				.machineImage(LookupMachineImage.Builder.create().name("slingsonic-ami").build()).build();

	}

	private SecurityGroup createSg(IVpc vpc, String context) {

		SecurityGroup securityGroup = SecurityGroup.Builder.create(this, MyOrgConstants.name(context, "sg"))
				.allowAllOutbound(true).securityGroupName(MyOrgConstants.name(context, "sg"))
				.description(
						"allow ssh from admin desktop, public access to port 80, sg access to port 3306, allow all outbound")
				.vpc(vpc).build();

		allowDBAccessFromSg(vpc.getVpcCidrBlock());
		allowHttpFromAllIpAddresses();
		allowHttpsFromAllIpAddresses();
		allowSshFromAdminIpAddresses();
		allowDbAccessFromAdminIpAddresses();
		allowTomcatFromAllIpAddresses();

		for (Rule ingress : ingressRules) {
			securityGroup.addIngressRule(ingress.peer, ingress.port, ingress.description);
		}
		return securityGroup;
	}

	private static final String userData(String region) {
		StringBuilder sb = new StringBuilder();
		sb.append("#!/bin/bash").append(System.getProperty("line.separator")).append("export AWS_DEFAULT_REGION=")
				.append(region);
		return sb.toString();

	}

	private Key createKmsKey(String context, IPrincipal... principals) {
		// create key
		Key applicationKey = Key.Builder.create(this, MyOrgConstants.name(context, "kmskey"))
				.alias(MyOrgConstants.name(context, "pipeline-kms-key")).description("Key for server side encryption")
				.policy(PolicyDocument.Builder.create().statements(Arrays.asList(
						PolicyStatement.Builder.create().actions(Arrays.asList("kms:*")).resources(Arrays.asList("*"))
								.effect(Effect.ALLOW).principals(Arrays.asList(new AccountRootPrincipal())).build(),
						PolicyStatement.Builder.create()
								.actions(Arrays.asList("kms:Decrypt", "kms:ReEncrypt", "kms:DescribeKey", "kms:Encrypt",
										"kms:GenerateDataKey"))
								.resources(Arrays.asList("*")).effect(Effect.ALLOW)
								.principals(Arrays.asList(principals[0])).build(),
						PolicyStatement.Builder.create()
								.actions(Arrays.asList("kms:Decrypt", "kms:ReEncrypt", "kms:DescribeKey", "kms:Encrypt",
										"kms:GenerateDataKey"))
								.resources(Arrays.asList("*")).effect(Effect.ALLOW)
								.principals(Arrays.asList(principals[1])).build(),
						PolicyStatement.Builder.create()
								.actions(Arrays.asList("kms:Decrypt", "kms:ReEncrypt", "kms:DescribeKey", "kms:Encrypt",
										"kms:GenerateDataKey"))
								.resources(Arrays.asList("*")).effect(Effect.ALLOW)
								.principals(Arrays.asList(principals[2])).build())

				).build()).build();

		return applicationKey;

	}

	private Bucket createS3Bucket(String context, Key applicationKey) {
		// create S3 bucket for buildArtifacts
		Bucket pipelineBucket = Bucket.Builder.create(this, MyOrgConstants.name(context, "pipeline-bucket"))
				.bucketName(MyOrgConstants.name(context, "pipeline-bucket")).encryption(BucketEncryption.KMS)
				.encryptionKey(applicationKey).versioned(true).build();

		return pipelineBucket;
	}

	private List<Rule> ingressRules = new ArrayList<>();

	public void allowHttpFromAllIpAddresses() {
		Rule rule = new Rule(Peer.anyIpv4(), Port.tcp(80), "allowing http access from all IPs");
		ingressRules.add(rule);
	}
	
	public void allowTomcatFromAllIpAddresses() {
		Rule rule = new Rule(Peer.anyIpv4(), Port.tcp(8080), "allowing tomcat from all IPs");
		ingressRules.add(rule);
	}

	public void allowHttpsFromAllIpAddresses() {
		Rule rule = new Rule(Peer.anyIpv4(), Port.tcp(443), "allowing https access from all IPs");
		ingressRules.add(rule);
	}
	
	public void allowDbAccessFromAdminIpAddresses() {
		Rule rule1 = new Rule(Peer.ipv4(ADMIN_CIDR_1), Port.tcp(3306), "allowing db access from admin");
		Rule rule2 = new Rule(Peer.ipv4(ADMIN_CIDR_2), Port.tcp(3306), "allowing db access from admin");
		ingressRules.add(rule1);
		ingressRules.add(rule2);
	}
	
	public void allowSshFromAdminIpAddresses() {
		Rule rule1 = new Rule(Peer.ipv4(ADMIN_CIDR_1), Port.tcp(22), "allowing ssh from admin");
		Rule rule2 = new Rule(Peer.ipv4(ADMIN_CIDR_2), Port.tcp(22), "allowing ssh from admin");
		ingressRules.add(rule1);
		ingressRules.add(rule2);
	}

	public void allowDBAccessFromSg(String cidr) {
		Rule rule = new Rule(Peer.ipv4(cidr), Port.tcp(3306), "allowing db access from security group");
		ingressRules.add(rule);
	}

	private static class Rule {
		private IPeer peer;
		private Port port;
		private String description;

		public Rule(IPeer peer, Port port, String description) {
			super();
			this.peer = peer;
			this.port = port;
			this.description = description;
		}

	}
}
