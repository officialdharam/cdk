package com.myorg;

public class MyOrgConstants {

	public static final String CIDR = "cidr";
	public static final String PUBLIC_IP = "public_ip";
	public static final String MAX_AZ = "max_az";

	public static final String TAG_ROOT = "slingsonic";
	public static final String TAG_ROOT_KEY = "namespace";

	public static final String DEPLOYMENT = "DEPLOYMENT";
	public static final String PREFIX = "ss";

	public static final String instanceNameTag(String context) {
		return PREFIX + "-instance-" + context;
	}

	public static final String name(String context, String name) {
		return PREFIX + "-" + context + "-" + name;
	}

}
