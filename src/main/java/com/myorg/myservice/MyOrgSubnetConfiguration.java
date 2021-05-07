package com.myorg.myservice;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;

public class MyOrgSubnetConfiguration implements SubnetConfiguration {

	private int cidrMask;
	private String name;
	private SubnetType type;
	private Boolean reserved;

	private MyOrgSubnetConfiguration(int cidr, String name, SubnetType type, Boolean reserved) {
		this.cidrMask = cidr;
		this.name = name;
		this.type = type;
		this.reserved = reserved;
	}

	@Override
	public @Nullable Number getCidrMask() {
		return this.cidrMask;
	}

	@Override
	public @Nullable Boolean getReserved() {
		return this.reserved;
	}

	@Override
	public @NotNull String getName() {
		return this.name;
	}

	@Override
	public @NotNull SubnetType getSubnetType() {
		return this.type;
	}

	public static final class Builder {

		private int cidrMask;
		private String name;
		private SubnetType type = SubnetType.PRIVATE;
		private Boolean reserved = false;

		public Builder cidrMask(int cidrMask) {
			this.cidrMask = cidrMask;
			return this;
		}

		public Builder subnetName(String name) {
			this.name = name;
			return this;
		}

		public Builder reserved(Boolean reserved) {
			this.reserved = reserved;
			return this;
		}

		public Builder subnetType(SubnetType type) {
			this.type = type;
			return this;
		}

		public SubnetConfiguration build() {
			return new MyOrgSubnetConfiguration(cidrMask, name, type, reserved);
		}
	}

}
