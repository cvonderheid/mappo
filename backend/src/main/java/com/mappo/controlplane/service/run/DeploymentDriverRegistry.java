package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.domain.execution.DeploymentPreviewDriver;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeploymentDriverRegistry {

    private final Map<String, DeploymentDriver> deploymentDrivers;
    private final Map<String, DeploymentPreviewDriver> previewDrivers;
    private final ConfigurableListableBeanFactory beanFactory;

    public Optional<DeploymentDriver> findDriver(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return deploymentDrivers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(project, release, runtimeConfigured))
            .sorted(driverComparator())
            .map(Map.Entry::getValue)
            .findFirst();
    }

    public Optional<DeploymentPreviewDriver> findPreviewDriver(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return previewDrivers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(project, release, runtimeConfigured))
            .sorted(driverComparator())
            .map(Map.Entry::getValue)
            .findFirst();
    }

    private <T> Comparator<Map.Entry<String, T>> driverComparator() {
        return Comparator
            .<Map.Entry<String, T>>comparingInt(entry -> isPrimary(entry.getKey()) ? 0 : 1)
            .thenComparing((left, right) -> AnnotationAwareOrderComparator.INSTANCE.compare(left.getValue(), right.getValue()))
            .thenComparing(Map.Entry::getKey);
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
            && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
