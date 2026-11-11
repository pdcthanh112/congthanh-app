// Package app: use case của pricing-service. Phụ thuộc Repository/Cache qua interface (port).
package app

import (
	"context"
	"log/slog"
	"slices"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/domain"
)

const (
	MaxProductsPerRequest = 500
	MaxPricesPerUpsert    = 1000
	DefaultCurrency       = "USD"
)

type Repository interface {
	ListsFor(ctx context.Context, currency string) ([]domain.PriceList, error)
	Candidates(ctx context.Context, lists []domain.PriceList, productIDs []string, at time.Time) (map[string][]domain.Candidate, error)
	Lowest30d(ctx context.Context, productIDs []string, since time.Time) (map[string]int64, error)

	PriceLists(ctx context.Context) ([]domain.PriceList, error)
	PriceListByCode(ctx context.Context, code string) (domain.PriceList, error)
	CreatePriceList(ctx context.Context, l domain.PriceList) (domain.PriceList, error)
	UpdatePriceList(ctx context.Context, l domain.PriceList) error
	UpsertPrices(ctx context.Context, l domain.PriceList, ps []domain.Price, by string) (changed int, err error)
	DeletePrice(ctx context.Context, l domain.PriceList, productID, by string) error
	PricesInList(ctx context.Context, listID int64, productID string, limit, offset int) ([]domain.Price, error)
	BasePrices(ctx context.Context, listID int64, productIDs []string) (map[string]int64, error)
	CreateSchedules(ctx context.Context, ss []domain.Schedule) error
	CancelSchedule(ctx context.Context, id string) (domain.Schedule, error)
	Schedules(ctx context.Context, f ScheduleFilter) ([]domain.Schedule, error)
	History(ctx context.Context, productID string, limit int) ([]HistoryEntry, error)
	AdvanceSchedules(ctx context.Context, now time.Time) (started, ended int, err error)
}

// Cache: kết quả Resolve ngắn hạn (Redis). Generation tăng mỗi khi giá đổi để vô hiệu toàn bộ.
type Cache interface {
	Generation(ctx context.Context) (int64, error)
	Bump(ctx context.Context) error
	GetMany(ctx context.Context, keys []string) (map[string]domain.Resolved, error)
	SetMany(ctx context.Context, items map[string]domain.Resolved, ttl time.Duration) error
}

type IDGen interface{ NewID() string }

type Clock func() time.Time

type ScheduleFilter struct {
	ProductID string
	Status    domain.ScheduleStatus
	Limit     int
}

type HistoryEntry struct {
	PriceList string    `json:"priceList"`
	ProductID string    `json:"productId"`
	Old       *int64    `json:"old,omitempty"`
	New       int64     `json:"new"`
	Reason    string    `json:"reason"`
	ChangedBy string    `json:"changedBy"`
	ChangedAt time.Time `json:"changedAt"`
}

type Context struct {
	Currency string
	Groups   []string
	At       time.Time // zero = bây giờ
}

type Service struct {
	repo  Repository
	cache Cache // nil = không cache
	ids   IDGen
	now   Clock
	ttl   time.Duration
}

func NewService(repo Repository, cache Cache, ids IDGen, now Clock) *Service {
	if now == nil {
		now = time.Now
	}
	return &Service{repo: repo, cache: cache, ids: ids, now: now, ttl: 30 * time.Second}
}

func (c Context) normalized(now time.Time) (Context, bool) {
	live := c.At.IsZero()
	if live {
		c.At = now
	}
	c.Currency = strings.ToUpper(strings.TrimSpace(c.Currency))
	if c.Currency == "" {
		c.Currency = DefaultCurrency
	}
	gs := make([]string, 0, len(c.Groups))
	for _, g := range c.Groups {
		if g = strings.ToUpper(strings.TrimSpace(g)); g != "" {
			gs = append(gs, g)
		}
	}
	slices.Sort(gs)
	c.Groups = slices.Compact(gs)
	return c, live
}

func dedupe(ids []string) []string {
	out := make([]string, 0, len(ids))
	seen := make(map[string]bool, len(ids))
	for _, id := range ids {
		if id = strings.TrimSpace(id); id != "" && !seen[id] {
			seen[id] = true
			out = append(out, id)
		}
	}
	return out
}

func cacheKey(gen int64, c Context, productID string) string {
	return "pricing:" + itoa(gen) + ":" + c.Currency + ":" + strings.Join(c.Groups, ",") + ":" + productID
}

func itoa(n int64) string {
	if n == 0 {
		return "0"
	}
	var b [20]byte
	i := len(b)
	neg := n < 0
	if neg {
		n = -n
	}
	for n > 0 {
		i--
		b[i] = byte('0' + n%10)
		n /= 10
	}
	if neg {
		i--
		b[i] = '-'
	}
	return string(b[i:])
}

// Prices: giá hiện hành của nhiều sản phẩm (theo lô). Sản phẩm chưa định giá không có trong kết quả.
func (s *Service) Prices(ctx context.Context, productIDs []string, pc Context) (map[string]domain.Resolved, error) {
	ids := dedupe(productIDs)
	if len(ids) > MaxProductsPerRequest {
		return nil, apperr.Invalid("at most %d products per request", MaxProductsPerRequest)
	}
	out := make(map[string]domain.Resolved, len(ids))
	if len(ids) == 0 {
		return out, nil
	}
	c, live := pc.normalized(s.now())

	// Chỉ cache giá "bây giờ"; truy vấn theo thời điểm khác (xem trước đợt sale) luôn đọc DB.
	var gen int64 = -1
	if s.cache != nil && live {
		if g, err := s.cache.Generation(ctx); err == nil {
			gen = g
			keys := make([]string, len(ids))
			for i, id := range ids {
				keys[i] = cacheKey(gen, c, id)
			}
			if hit, err := s.cache.GetMany(ctx, keys); err == nil {
				for _, r := range hit {
					out[r.ProductID] = r
				}
			}
		} else {
			slog.WarnContext(ctx, "price cache unavailable", "err", err)
		}
	}
	missing := make([]string, 0, len(ids))
	for _, id := range ids {
		if _, ok := out[id]; !ok {
			missing = append(missing, id)
		}
	}
	if len(missing) == 0 {
		return out, nil
	}

	all, err := s.repo.ListsFor(ctx, c.Currency)
	if err != nil {
		return nil, err
	}
	lists := make([]domain.PriceList, 0, len(all))
	for _, l := range all {
		if l.AppliesTo(c.Currency, c.Groups, c.At) {
			lists = append(lists, l)
		}
	}
	if len(lists) == 0 {
		return out, nil
	}
	cands, err := s.repo.Candidates(ctx, lists, missing, c.At)
	if err != nil {
		return nil, err
	}
	lowest, err := s.repo.Lowest30d(ctx, missing, c.At.Add(-30*24*time.Hour))
	if err != nil {
		return nil, err
	}
	fresh := make(map[string]domain.Resolved, len(missing))
	ttl := s.ttl
	for _, id := range missing {
		r, ok := domain.Resolve(id, c.Currency, cands[id], c.At)
		if !ok {
			continue
		}
		low := r.Amount
		if h, ok := lowest[id]; ok && h < low {
			low = h
		}
		r.Lowest30d = &low
		out[id] = r
		if gen >= 0 {
			fresh[cacheKey(gen, c, id)] = r
			// Không để cache sống quá lúc đợt sale kết thúc.
			if r.SaleEndsAt != nil {
				if left := r.SaleEndsAt.Sub(c.At); left < ttl {
					ttl = max(left, time.Second)
				}
			}
		}
	}
	if len(fresh) > 0 {
		if err := s.cache.SetMany(ctx, fresh, ttl); err != nil {
			slog.WarnContext(ctx, "price cache write failed", "err", err)
		}
	}
	return out, nil
}

type Line struct {
	LineID    string `json:"lineId"`
	ProductID string `json:"productId"`
	Quantity  int    `json:"quantity"`
}

type QuotedLine struct {
	Line
	UnitPrice int64  `json:"unitPrice"`
	LineTotal int64  `json:"lineTotal"`
	CompareAt int64  `json:"compareAt"`
	PriceList string `json:"priceList"`
}

type Quote struct {
	Currency string       `json:"currency"`
	Lines    []QuotedLine `json:"lines"`
	Subtotal int64        `json:"subtotal"`
	Unpriced []string     `json:"unpricedProductIds"`
}

// QuoteLines chốt giá từng dòng (checkout). Không dùng cache để giá luôn mới nhất.
func (s *Service) QuoteLines(ctx context.Context, lines []Line, pc Context) (Quote, error) {
	if len(lines) == 0 {
		return Quote{}, apperr.Invalid("lines are required")
	}
	ids := make([]string, 0, len(lines))
	for _, l := range lines {
		if l.Quantity <= 0 || l.Quantity > 10_000 {
			return Quote{}, apperr.Invalid("quantity of %s must be between 1 and 10000", l.ProductID)
		}
		ids = append(ids, l.ProductID)
	}
	c, _ := pc.normalized(s.now())
	noCache := *s
	noCache.cache = nil
	prices, err := noCache.Prices(ctx, ids, Context{Currency: c.Currency, Groups: c.Groups, At: c.At})
	if err != nil {
		return Quote{}, err
	}
	q := Quote{Currency: c.Currency, Unpriced: []string{}}
	for _, l := range lines {
		p, ok := prices[strings.TrimSpace(l.ProductID)]
		if !ok {
			q.Unpriced = append(q.Unpriced, l.ProductID)
			continue
		}
		total := p.Amount * int64(l.Quantity)
		q.Lines = append(q.Lines, QuotedLine{Line: l, UnitPrice: p.Amount, LineTotal: total, CompareAt: p.CompareAt, PriceList: p.PriceList})
		q.Subtotal += total
	}
	return q, nil
}

// ---------- quản trị ----------

func (s *Service) Invalidate(ctx context.Context) {
	if s.cache != nil {
		if err := s.cache.Bump(ctx); err != nil {
			slog.WarnContext(ctx, "price cache invalidation failed", "err", err)
		}
	}
}

func (s *Service) PriceLists(ctx context.Context) ([]domain.PriceList, error) {
	return s.repo.PriceLists(ctx)
}

func (s *Service) CreatePriceList(ctx context.Context, l domain.PriceList) (domain.PriceList, error) {
	if err := l.Normalize(); err != nil {
		return l, err
	}
	out, err := s.repo.CreatePriceList(ctx, l)
	if err == nil {
		s.Invalidate(ctx)
	}
	return out, err
}

func (s *Service) UpdatePriceList(ctx context.Context, l domain.PriceList) error {
	if err := l.Normalize(); err != nil {
		return err
	}
	cur, err := s.repo.PriceListByCode(ctx, l.Code)
	if err != nil {
		return err
	}
	if cur.Code == domain.DefaultList && !l.Active {
		return apperr.Invalid("the default price list cannot be deactivated")
	}
	l.ID = cur.ID
	if err := s.repo.UpdatePriceList(ctx, l); err != nil {
		return err
	}
	s.Invalidate(ctx)
	return nil
}

func (s *Service) UpsertPrices(ctx context.Context, listCode string, ps []domain.Price, by string) (int, error) {
	if len(ps) == 0 || len(ps) > MaxPricesPerUpsert {
		return 0, apperr.Invalid("send between 1 and %d prices", MaxPricesPerUpsert)
	}
	for i := range ps {
		ps[i].ProductID = strings.TrimSpace(ps[i].ProductID)
		if err := ps[i].Validate(); err != nil {
			return 0, err
		}
	}
	l, err := s.repo.PriceListByCode(ctx, listCode)
	if err != nil {
		return 0, err
	}
	n, err := s.repo.UpsertPrices(ctx, l, ps, by)
	if err == nil && n > 0 {
		s.Invalidate(ctx)
	}
	return n, err
}

func (s *Service) DeletePrice(ctx context.Context, listCode, productID, by string) error {
	l, err := s.repo.PriceListByCode(ctx, listCode)
	if err != nil {
		return err
	}
	if err := s.repo.DeletePrice(ctx, l, productID, by); err != nil {
		return err
	}
	s.Invalidate(ctx)
	return nil
}

func (s *Service) PricesInList(ctx context.Context, listCode, productID string, limit, offset int) ([]domain.Price, error) {
	l, err := s.repo.PriceListByCode(ctx, listCode)
	if err != nil {
		return nil, err
	}
	if limit <= 0 || limit > 500 {
		limit = 100
	}
	return s.repo.PricesInList(ctx, l.ID, productID, limit, max(offset, 0))
}

type NewSchedule struct {
	PriceList  string          `json:"priceList"`
	ProductIDs []string        `json:"productIds"`
	Kind       domain.SaleKind `json:"kind"`
	Amount     *int64          `json:"amount"`     // giá cố định cho mọi sản phẩm
	PercentOff *float64        `json:"percentOff"` // hoặc giảm % từ giá thường của từng sản phẩm
	StartsAt   time.Time       `json:"startsAt"`
	EndsAt     *time.Time      `json:"endsAt"`
	Reason     string          `json:"reason"`
}

// CreateSchedules: một đợt sale cho nhiều sản phẩm (tối đa 1000), giá cố định hoặc giảm theo %.
func (s *Service) CreateSchedules(ctx context.Context, in NewSchedule, by string) ([]domain.Schedule, error) {
	ids := dedupe(in.ProductIDs)
	switch {
	case len(ids) == 0 || len(ids) > MaxPricesPerUpsert:
		return nil, apperr.Invalid("send between 1 and %d productIds", MaxPricesPerUpsert)
	case (in.Amount == nil) == (in.PercentOff == nil):
		return nil, apperr.Invalid("set exactly one of amount or percentOff")
	}
	code := in.PriceList
	if code == "" {
		code = domain.DefaultList
	}
	l, err := s.repo.PriceListByCode(ctx, code)
	if err != nil {
		return nil, err
	}
	var base map[string]int64
	if in.PercentOff != nil {
		if base, err = s.repo.BasePrices(ctx, l.ID, ids); err != nil {
			return nil, err
		}
	}
	now := s.now()
	out := make([]domain.Schedule, 0, len(ids))
	for _, id := range ids {
		sc := domain.Schedule{ID: s.ids.NewID(), ListID: l.ID, ListCode: l.Code, ProductID: id, Kind: in.Kind,
			StartsAt: in.StartsAt.UTC(), EndsAt: in.EndsAt, Status: domain.Scheduled, Reason: strings.TrimSpace(in.Reason), CreatedBy: by}
		if in.Amount != nil {
			sc.Amount = *in.Amount
		} else {
			b, ok := base[id]
			if !ok {
				return nil, apperr.Invalid("product %s has no regular price in %s", id, l.Code)
			}
			if sc.Amount, err = domain.PercentOff(b, *in.PercentOff); err != nil {
				return nil, err
			}
		}
		if err := sc.Validate(now); err != nil {
			return nil, err
		}
		out = append(out, sc)
	}
	if err := s.repo.CreateSchedules(ctx, out); err != nil {
		return nil, err
	}
	s.Invalidate(ctx)
	return out, nil
}

func (s *Service) CancelSchedule(ctx context.Context, id string) (domain.Schedule, error) {
	sc, err := s.repo.CancelSchedule(ctx, id)
	if err == nil {
		s.Invalidate(ctx)
	}
	return sc, err
}

func (s *Service) Schedules(ctx context.Context, f ScheduleFilter) ([]domain.Schedule, error) {
	if f.Limit <= 0 || f.Limit > 500 {
		f.Limit = 100
	}
	return s.repo.Schedules(ctx, f)
}

func (s *Service) History(ctx context.Context, productID string, limit int) ([]HistoryEntry, error) {
	if limit <= 0 || limit > 500 {
		limit = 100
	}
	return s.repo.History(ctx, productID, limit)
}

// Tick: chuyển trạng thái đợt sale đến giờ và phát PriceChanged (chạy định kỳ, an toàn khi nhiều pod).
func (s *Service) Tick(ctx context.Context) error {
	started, ended, err := s.repo.AdvanceSchedules(ctx, s.now())
	if err != nil {
		return err
	}
	if started+ended > 0 {
		slog.InfoContext(ctx, "price schedules advanced", "started", started, "ended", ended)
		s.Invalidate(ctx)
	}
	return nil
}

// RunScheduler chạy Tick mỗi interval đến khi ctx huỷ.
func (s *Service) RunScheduler(interval time.Duration) func(context.Context) error {
	return func(ctx context.Context) error {
		t := time.NewTicker(interval)
		defer t.Stop()
		for {
			if err := s.Tick(ctx); err != nil && ctx.Err() == nil {
				slog.ErrorContext(ctx, "price schedule tick failed", "err", err)
			}
			select {
			case <-ctx.Done():
				return nil
			case <-t.C:
			}
		}
	}
}
