// Package messaging nhận câu trả lời của các processor trên topic ecommerce.privacy.events.
package messaging

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	privacyv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/privacy/v1"
)

const TopicPrivacyEvents = "ecommerce.privacy.events"

var (
	erasureCompletedType = string((&privacyv1.ErasureCompleted{}).ProtoReflect().Descriptor().FullName())
	exportReadyType      = string((&privacyv1.AccessExportReady{}).ProtoReflect().Descriptor().FullName())
)

type ReplyHandler interface {
	ProcessorReply(ctx context.Context, requestID, processor string, retained bool, reason, objectKey string) error
}

// Handler bỏ qua event do chính privacy-service phát (ErasureRequested, ConsentChanged...) trên cùng topic.
// Lưu ý: kafka.Consumer đã mở transaction cho processed_events; use case mở transaction riêng để khoá yêu cầu,
// nên trùng lặp hiếm hoi (commit use case rồi chết trước commit processed_events) được domain bỏ qua vì idempotent.
func Handler(h ReplyHandler) func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		switch env.GetType() {
		case erasureCompletedType:
			ev := &privacyv1.ErasureCompleted{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad ErasureCompleted payload", "err", err)
				return nil
			}
			return h.ProcessorReply(ctx, ev.GetRequestId(), ev.GetProcessor(), ev.GetPartiallyRetained(), ev.GetRetentionReason(), "")
		case exportReadyType:
			ev := &privacyv1.AccessExportReady{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad AccessExportReady payload", "err", err)
				return nil
			}
			return h.ProcessorReply(ctx, ev.GetRequestId(), ev.GetProcessor(), false, "", ev.GetObjectKey())
		}
		return nil
	}
}
