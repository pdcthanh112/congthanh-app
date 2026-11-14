package com.congthanh.commonservice.grpc;

import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.exception.PermissionException;
import io.grpc.Status;
import io.grpc.StatusException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainGrpcExceptionHandlerTest {

    private final DomainGrpcExceptionHandler handler = new DomainGrpcExceptionHandler();

    @Test
    void mapsDomainExceptionToStatusWithErrorCodeTrailer() {
        StatusException e = handler.handleException(new NotFoundException("order 7 not found"));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(e.getStatus().getDescription()).isEqualTo("order 7 not found");
        assertThat(e.getTrailers().get(DomainGrpcExceptionHandler.ERROR_CODE)).isEqualTo("NOT_FOUND");
    }

    @Test
    void forbiddenMapsToPermissionDenied() {
        assertThat(handler.handleException(new PermissionException("no")).getStatus().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void leavesOtherExceptionsToTheNextHandler() {
        assertThat(handler.handleException(new IllegalStateException("boom"))).isNull();
    }
}
