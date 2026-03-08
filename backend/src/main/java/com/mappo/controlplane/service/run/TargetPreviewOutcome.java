package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.RunPreviewChangeRecord;
import java.util.List;

public record TargetPreviewOutcome(
    String summary,
    List<String> warnings,
    List<RunPreviewChangeRecord> changes
) {
}
