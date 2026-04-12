package com.mappo.controlplane.service.project;

import com.mappo.controlplane.api.request.ProjectAdoBranchDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoPipelineDiscoveryRequest;
import com.mappo.controlplane.api.request.ProjectAdoRepositoryDiscoveryRequest;
import com.mappo.controlplane.application.project.AzureDevOpsProjectDeploymentDiscoveryHandler;
import com.mappo.controlplane.model.ProjectAdoBranchDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoPipelineDiscoveryResultRecord;
import com.mappo.controlplane.model.ProjectAdoRepositoryDiscoveryResultRecord;
import org.springframework.stereotype.Service;

@Service
public class ProjectDeploymentDriverDiscoveryService {

    private final AzureDevOpsProjectDeploymentDiscoveryHandler azureDevOpsProjectDeploymentDiscoveryHandler;

    public ProjectDeploymentDriverDiscoveryService(
        AzureDevOpsProjectDeploymentDiscoveryHandler azureDevOpsProjectDeploymentDiscoveryHandler
    ) {
        this.azureDevOpsProjectDeploymentDiscoveryHandler = azureDevOpsProjectDeploymentDiscoveryHandler;
    }

    public ProjectAdoPipelineDiscoveryResultRecord discoverAdoPipelines(
        String projectId,
        ProjectAdoPipelineDiscoveryRequest request
    ) {
        return azureDevOpsProjectDeploymentDiscoveryHandler.discoverAdoPipelines(projectId, request);
    }

    public ProjectAdoRepositoryDiscoveryResultRecord discoverAdoRepositories(
        String projectId,
        ProjectAdoRepositoryDiscoveryRequest request
    ) {
        return azureDevOpsProjectDeploymentDiscoveryHandler.discoverAdoRepositories(projectId, request);
    }

    public ProjectAdoBranchDiscoveryResultRecord discoverAdoBranches(
        String projectId,
        ProjectAdoBranchDiscoveryRequest request
    ) {
        return azureDevOpsProjectDeploymentDiscoveryHandler.discoverAdoBranches(projectId, request);
    }
}
