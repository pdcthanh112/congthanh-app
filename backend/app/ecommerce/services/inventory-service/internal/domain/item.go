package domain

import (
	"cmp"
	"slices"
)

type Item struct {
	ProductID string `json:"productId"`
	Quantity  int32  `json:"quantity"`
}

// MergeItems gộp dòng trùng sản phẩm và sắp theo product_id, để mọi transaction khoá
// các dòng tồn kho theo cùng thứ tự (tránh deadlock).
func MergeItems(items []Item) ([]Item, error) {
	m := map[string]int32{}
	for _, it := range items {
		if it.ProductID == "" {
			return nil, Invalid("productId is required")
		}
		if it.Quantity <= 0 {
			return nil, Invalid("quantity for %s must be > 0", it.ProductID)
		}
		m[it.ProductID] += it.Quantity
	}
	out := make([]Item, 0, len(m))
	for id, q := range m {
		out = append(out, Item{ProductID: id, Quantity: q})
	}
	slices.SortFunc(out, func(a, b Item) int { return cmp.Compare(a.ProductID, b.ProductID) })
	return out, nil
}
