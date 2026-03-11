package com.mappo.controlplane.service.project;

import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDefinitionProvider;
import com.mappo.controlplane.model.ReleaseRecord;
import java.util.Comparator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectDefinitionProviderRegistry {

    private final Map<String, ProjectDefinitionProvider> providers;
    private final ConfigurableListableBeanFactory beanFactory;

    public ProjectDefinition resolve(ReleaseRecord release) {
        return providers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(release))
            .sorted(providerComparator())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No project definition is mapped for release source type "
                    + (release.sourceType() == null ? "unknown" : release.sourceType().getLiteral())
                    + "."
            ))
            .definition(release);
    }

    private Comparator<Map.Entry<String, ProjectDefinitionProvider>> providerComparator() {
        return Comparator
            .<Map.Entry<String, ProjectDefinitionProvider>>comparingInt(entry -> isPrimary(entry.getKey()) ? 0 : 1)
            .thenComparing((left, right) -> AnnotationAwareOrderComparator.INSTANCE.compare(left.getValue(), right.getValue()))
            .thenComparing(Map.Entry::getKey);
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
            && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
