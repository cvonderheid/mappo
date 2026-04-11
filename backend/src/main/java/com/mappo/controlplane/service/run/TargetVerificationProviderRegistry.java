package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.health.TargetVerificationProvider;
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
public class TargetVerificationProviderRegistry {

    private final Map<String, TargetVerificationProvider> providers;
    private final ConfigurableListableBeanFactory beanFactory;

    public TargetVerificationProvider getProvider(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetRecord target,
        TargetExecutionContextRecord context,
        boolean runtimeConfigured
    ) {
        return providers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(project, release, target, context, runtimeConfigured))
            .sorted(providerComparator())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("target verification provider not found"));
    }

    private Comparator<Map.Entry<String, TargetVerificationProvider>> providerComparator() {
        return Comparator
            .<Map.Entry<String, TargetVerificationProvider>>comparingInt(entry -> isPrimary(entry.getKey()) ? 0 : 1)
            .thenComparing((left, right) -> AnnotationAwareOrderComparator.INSTANCE.compare(left.getValue(), right.getValue()))
            .thenComparing(Map.Entry::getKey);
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
            && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
