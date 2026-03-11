package com.mappo.controlplane.domain.project;

import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;

public final class BuiltinProjects {

    public static final String AZURE_MANAGED_APP_DEPLOYMENT_STACK = "azure-managed-app-deployment-stack";
    public static final String AZURE_MANAGED_APP_TEMPLATE_SPEC = "azure-managed-app-template-spec";

    private BuiltinProjects() {
    }

    public static String defaultProjectIdFor(MappoReleaseSourceType sourceType) {
        if (sourceType == MappoReleaseSourceType.template_spec) {
            return AZURE_MANAGED_APP_TEMPLATE_SPEC;
        }
        return AZURE_MANAGED_APP_DEPLOYMENT_STACK;
    }
}
