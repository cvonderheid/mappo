package com.mappo.controlplane.service.run;

import com.mappo.controlplane.model.StageErrorRecord;
import lombok.Getter;

@Getter
public class TargetPreviewException extends RuntimeException {

    private final StageErrorRecord error;

    public TargetPreviewException(String message, StageErrorRecord error) {
        super(message);
        this.error = error;
    }
}
