package com.mappo.controlplane.application.project;

import com.mappo.controlplane.api.request.ProjectAdoBranchDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoPipelineDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoRepositoryDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoServiceConnectionDiscoveryRequest;
import com.mappo.controlplane.model.ProjectAdoBranchDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoServiceConnectionDiscoveryResultRecord;

public interface AzureDevOpsProjectDeploymentDiscoveryHandler {

    ProjectAdoPipelineDiscoveryResultRecord discoverAdoPipelines(
        String projectId,
        ProjectAdoPipelineDiscoveryRequest request
    );

    ProjectAdoRepositoryDiscoveryResultRecord discoverAdoRepositories(
        String projectId,
        ProjectAdoRepositoryDiscoveryRequest request
    );

    ProjectAdoBranchDiscoveryResultRecord discoverAdoBranches(
        String projectId,
        ProjectAdoBranchDiscoveryRequest request
    );

    ProjectAdoServiceConnectionDiscoveryResultRecord discoverAdoServiceConnections(
        String projectId,
        ProjectAdoServiceConnectionDiscoveryRequest request
    );
}
