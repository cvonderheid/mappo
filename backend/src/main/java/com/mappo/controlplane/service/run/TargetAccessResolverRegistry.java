package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.access.TargetAccessResolver;
import com.mappo.controlplane.domain.access.TargetAccessValidation;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import java.util.Comparator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TargetAccessResolverRegistry {

    private final Map<String, TargetAccessResolver> resolvers;
    private final ConfigurableListableBeanFactory beanFactory;

    public TargetAccessResolver getResolver(
        ProjectDefinition project,
        ReleaseRecord release,
        boolean azureConfigured
    ) {
        return resolvers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(project, release, azureConfigured))
            .sorted(resolverComparator())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("target access resolver not found"));
    }

    public TargetAccessValidation validate(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean azureConfigured
    ) {
        return getResolver(project, release, azureConfigured)
            .validate(project, release, target, context, azureConfigured);
    }

    private Comparator<Map.Entry<String, TargetAccessResolver>> resolverComparator() {
        return Comparator
            .<Map.Entry<String, TargetAccessResolver>>comparingInt(entry -> isPrimary(entry.getKey()) ? 0 : 1)
            .thenComparing((left, right) -> AnnotationAwareOrderComparator.INSTANCE.compare(left.getValue(), right.getValue()))
            .thenComparing(Map.Entry::getKey);
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
            && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
