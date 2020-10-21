#!/bin/bash

# PRE-REQUISITES
# The Azure subscription, the source and destination resource groups, the source and destination Azure Service Bus namespaces,
# and the Shared Access Policy must already all exist. This script will NOT create any of those resources.

# Provide values for all these variables
azure_subscription_id=""
resource_group_name_source=""
resource_group_name_destination=""
namespace_name_source=""
namespace_name_destination=""
shared_access_policy_name=""  # MUST be same in all namespaces used in failover URI, hence only specified once here

# Get source namespace primary and secondary keys
primary_key="$(az servicebus namespace authorization-rule keys list --subscription "$azure_subscription_id" -g "$resource_group_name_source" --namespace-name "$namespace_name_source" -n "$shared_access_policy_name" -o tsv --query 'primaryKey')"
secondary_key="$(az servicebus namespace authorization-rule keys list --subscription "$azure_subscription_id" -g "$resource_group_name_source" --namespace-name "$namespace_name_source" -n "$shared_access_policy_name" -o tsv --query 'secondaryKey')"

# Set to destination namespace
az servicebus namespace authorization-rule keys renew --subscription "$azure_subscription_id" \
	-g "$resource_group_name_destination" --namespace-name "$namespace_name_destination" -n "$shared_access_policy_name" \
	--key PrimaryKey --key-value "$primary_key" --verbose

az servicebus namespace authorization-rule keys renew --subscription "$azure_subscription_id" \
	-g "$resource_group_name_destination" --namespace-name "$namespace_name_destination" -n "$shared_access_policy_name" \
	--key SecondaryKey --key-value "$secondary_key" --verbose

echo "Done"