package com.mappo.controlplane.persistence.secretreference;

import static com.mappo.controlplane.jooq.Tables.SECRET_REFERENCES;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.service.secretreference.SecretReferenceMutationRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SecretReferenceCommandRepository {

    private final DSLContext dsl;

    public String createSecretReference(SecretReferenceMutationRecord mutation) {
        try {
            Long createdId = dsl.insertInto(SECRET_REFERENCES)
                .set(SECRET_REFERENCES.NAME, normalize(mutation.name()))
                .set(SECRET_REFERENCES.PROVIDER, normalize(mutation.provider().name()))
                .set(SECRET_REFERENCES.USAGE, normalize(mutation.usage().name()))
                .set(SECRET_REFERENCES.MODE, normalize(mutation.mode().name()))
                .set(SECRET_REFERENCES.BACKEND_REF, normalize(mutation.backendRef()))
                .set(SECRET_REFERENCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .returningResult(SECRET_REFERENCES.ID)
                .fetchOne(SECRET_REFERENCES.ID);
            if (createdId == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "secret reference was not created");
            }
            return String.valueOf(createdId);
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference already exists: " + normalize(mutation.name()));
        }
    }

    public void updateSecretReference(SecretReferenceMutationRecord mutation) {
        Long secretReferenceId = requiredSecretReferenceId(mutation.id());
        int updated = dsl.update(SECRET_REFERENCES)
            .set(SECRET_REFERENCES.NAME, normalize(mutation.name()))
            .set(SECRET_REFERENCES.PROVIDER, normalize(mutation.provider().name()))
            .set(SECRET_REFERENCES.USAGE, normalize(mutation.usage().name()))
            .set(SECRET_REFERENCES.MODE, normalize(mutation.mode().name()))
            .set(SECRET_REFERENCES.BACKEND_REF, normalize(mutation.backendRef()))
            .set(SECRET_REFERENCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(SECRET_REFERENCES.ID.eq(secretReferenceId))
            .execute();
        if (updated <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference not found: " + normalize(mutation.id()));
        }
    }

    public void deleteSecretReference(String secretReferenceId) {
        Long parsedSecretReferenceId = requiredSecretReferenceId(secretReferenceId);
        int deleted = dsl.deleteFrom(SECRET_REFERENCES)
            .where(SECRET_REFERENCES.ID.eq(parsedSecretReferenceId))
            .execute();
        if (deleted <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference not found: " + normalize(secretReferenceId));
        }
    }

    private Long requiredSecretReferenceId(String value) {
        String normalized = normalize(value);
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "secret reference not found: " + normalized);
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
