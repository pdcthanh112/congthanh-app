package com.congthanh.commonservice.grpc;

import com.congthanh.commonservice.error.DomainException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

/**
 * Lỗi nghiệp vụ thoát khỏi gRPC service thành status tương ứng, kèm trailer {@code error-code}.
 * Lỗi khác trả null để handler kế tiếp (hoặc mặc định của Spring gRPC) xử lý.
 */
public class DomainGrpcExceptionHandler implements GrpcExceptionHandler {

    public static final Metadata.Key<String> ERROR_CODE = Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public StatusException handleException(Throwable exception) {
        if (!(exception instanceof DomainException e)) {
            return null;
        }
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE, e.code());
        return Status.fromCode(Status.Code.valueOf(e.errorKind().grpcCode()))
                .withDescription(e.getMessage())
                .asException(trailers);
    }
}
