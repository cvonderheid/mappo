package com.mappo.controlplane.service.runtime;

import com.mappo.controlplane.domain.health.RuntimeHealthProvider;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuntimeHealthProviderRegistry {

    private final Map<String, RuntimeHealthProvider> providers;
    private final ConfigurableListableBeanFactory beanFactory;

    public Optional<RuntimeHealthProvider> find(TargetRuntimeProbeContextRecord target) {
        return providers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(target))
            .sorted(providerComparator())
            .map(Map.Entry::getValue)
            .findFirst();
    }

    public Optional<RuntimeHealthProvider> findConfigured(TargetRuntimeProbeContextRecord target) {
        return find(target).filter(RuntimeHealthProvider::isConfigured);
    }

    private Comparator<Map.Entry<String, RuntimeHealthProvider>> providerComparator() {
        return Comparator
            .<Map.Entry<String, RuntimeHealthProvider>>comparingInt(entry -> isPrimary(entry.getKey()) ? 0 : 1)
            .thenComparing((left, right) -> AnnotationAwareOrderComparator.INSTANCE.compare(left.getValue(), right.getValue()))
            .thenComparing(Map.Entry::getKey);
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
            && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
