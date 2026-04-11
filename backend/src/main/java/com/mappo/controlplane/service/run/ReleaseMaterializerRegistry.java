package com.mappo.controlplane.service.run;

import com.mappo.controlplane.domain.execution.ReleaseMaterializer;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.Comparator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReleaseMaterializerRegistry {

    private final Map<String, ReleaseMaterializer<?>> materializers;
    private final ConfigurableListableBeanFactory beanFactory;

    public <T> T materialize(
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        boolean runtimeConfigured,
        Class<T> expectedType
    ) {
        ReleaseMaterializer<?> materializer = materializers.entrySet().stream()
            .filter(entry -> entry.getValue().supports(project, release, runtimeConfigured))
            .filter(entry -> expectedType.isAssignableFrom(entry.getValue().materializedType()))
            .sorted(materializerComparator())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "release materializer not found for project " + project.id() + " and type " + expectedType.getSimpleName()
            ));
        return expectedType.cast(materializer.materialize(project, release, target));
    }

    private Comparator<Map.Entry<String, ReleaseMaterializer<?>>> materializerComparator() {
        return Comparator
            .<Map.Entry<String, ReleaseMaterializer<?>>>comparingInt(entry -> isPrimary(entry.getKey()) ? 0 : 1)
            .thenComparing((left, right) -> AnnotationAwareOrderComparator.INSTANCE.compare(left.getValue(), right.getValue()))
            .thenComparing(Map.Entry::getKey);
    }

    private boolean isPrimary(String beanName) {
        return beanFactory.containsBeanDefinition(beanName)
            && beanFactory.getBeanDefinition(beanName).isPrimary();
    }
}
