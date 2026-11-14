// Package privacy giúp service Go trả lời yêu cầu xoá dữ liệu của privacy-service (CCPA/CPRA, ADR 0027).
package privacy

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"log/slog"

	"github.com/jackc/pgx/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	privacyv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/privacy/v1"
)

// Topic mà privacy-service phát yêu cầu và nhận câu trả lời.
const Topic = "ecommerce.privacy.events"

var erasureRequested = string((&privacyv1.ErasureRequested{}).ProtoReflect().Descriptor().FullName())

// EraseFunc xoá/ẩn danh dữ liệu của khách trong tx. retained khác rỗng = lý do pháp lý cho phần phải giữ lại.
type EraseFunc func(ctx context.Context, tx pgx.Tx, subjectID, emailSHA256 string) (retained string, err error)

// ErasedID là định danh thay thế ổn định, không đảo ngược (cùng công thức với Java/TS).
func ErasedID(subjectID string) string {
	sum := sha256.Sum256([]byte(subjectID))
	return "erased-" + hex.EncodeToString(sum[:])[:16]
}

// Handler trả về kafka.Handler: xoá trong transaction của consumer (đã chống trùng bằng processed_events) và ghi
// ErasureCompleted vào outbox cùng transaction, nên không bao giờ báo xong khi chưa xoá.
func Handler(source, processor string, erase EraseFunc) func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
		if env.GetType() != erasureRequested {
			return nil
		}
		ev := &privacyv1.ErasureRequested{}
		if err := env.GetPayload().UnmarshalTo(ev); err != nil {
			slog.WarnContext(ctx, "bad ErasureRequested payload", "err", err)
			return nil
		}
		if ev.GetSubjectId() == "" {
			return nil
		}
		retained, err := erase(ctx, tx, ev.GetSubjectId(), ev.GetEmailSha256())
		if err != nil {
			return err
		}
		return outbox.Write(ctx, tx, source, outbox.Message{AggregateType: "privacy", AggregateID: ev.GetSubjectId(),
			Payload: &privacyv1.ErasureCompleted{RequestId: ev.GetRequestId(), SubjectId: ev.GetSubjectId(),
				Processor: processor, PartiallyRetained: retained != "", RetentionReason: retained}})
	}
}
