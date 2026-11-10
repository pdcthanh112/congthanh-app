package com.congthanh.orderservice.adapter.out.grpc;

import com.congthanh.inventoryservice.grpc.CommitReservationRequest;
import com.congthanh.inventoryservice.grpc.InventoryItem;
import com.congthanh.inventoryservice.grpc.InventoryServiceGrpc;
import com.congthanh.inventoryservice.grpc.ReleaseInventoryRequest;
import com.congthanh.inventoryservice.grpc.ReserveInventoryRequest;
import com.congthanh.orderservice.application.port.out.Inventory;
import com.congthanh.orderservice.domain.model.OrderLine;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** inventory-service (Go). Chỉ được gọi từ activity Temporal, nên retry/timeout do Temporal quyết định. */
@Component
class InventoryGrpcAdapter implements Inventory {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    InventoryGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = InventoryServiceGrpc.newBlockingStub(channels.createChannel("inventory"));
    }

    @Override
    public Reservation reserve(String reservationId, String customerId, List<OrderLine> lines) {
        try {
            var res = stub.withDeadlineAfter(5, TimeUnit.SECONDS).reserveInventory(ReserveInventoryRequest.newBuilder()
                    .setReservationId(reservationId)
                    .setCustomerId(customerId)
                    .setReservationType(ReserveInventoryRequest.ReservationType.ORDER)
                    .addAllItems(items(lines))
                    .build());
            return new Reservation(res.getSuccess(), res.getReservationId(), res.getMessage());
        } catch (StatusRuntimeException e) {
            throw Protos.translate("inventory-service", e);
        }
    }

    @Override
    public void release(String reservationId, String reason) {
        try {
            stub.withDeadlineAfter(5, TimeUnit.SECONDS).releaseInventory(ReleaseInventoryRequest.newBuilder()
                    .setReservationId(reservationId)
                    .setReason(reason == null ? "" : reason)
                    .setReleaseType(ReleaseInventoryRequest.ReleaseType.CANCEL)
                    .build());
        } catch (StatusRuntimeException e) {
            // Chưa từng giữ hàng (NOT_FOUND) thì trả hàng là no-op: bù trừ phải idempotent.
            if (e.getStatus().getCode() != io.grpc.Status.Code.NOT_FOUND) {
                throw Protos.translate("inventory-service", e);
            }
        }
    }

    @Override
    public void commit(String reservationId, String orderId, List<OrderLine> lines) {
        try {
            var res = stub.withDeadlineAfter(5, TimeUnit.SECONDS).commitReservation(CommitReservationRequest.newBuilder()
                    .setReservationId(reservationId).setOrderId(orderId).addAllItems(items(lines)).build());
            if (!res.getSuccess()) {
                throw com.congthanh.orderservice.domain.exception.OrderException.conflict(
                        "inventory-service refused commit: " + res.getMessage());
            }
        } catch (StatusRuntimeException e) {
            throw Protos.translate("inventory-service", e);
        }
    }

    private static List<InventoryItem> items(List<OrderLine> lines) {
        return lines.stream().map(l -> InventoryItem.newBuilder()
                .setProductId(l.productId()).setQuantity(l.quantity()).build()).toList();
    }
}
