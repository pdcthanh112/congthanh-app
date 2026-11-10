package domain

import "fmt"

type ProductDetail struct {
	ID    string  `json:"id"`
	Name  string  `json:"name"`
	Slug  string  `json:"slug"`
	SKU   string  `json:"sku"`
	Price float64 `json:"price"`
	Image string  `json:"image"`
}

type ItemDetail struct {
	ID       int64         `json:"id"`
	Quantity int32         `json:"quantity"`
	Product  ProductDetail `json:"product"`
}

type ValidationLine struct {
	ItemID            int64  `json:"itemId"`
	ProductID         string `json:"productId"`
	Quantity          int32  `json:"quantity"`
	AvailableQuantity int32  `json:"availableQuantity"`
	Valid             bool   `json:"valid"`
	Error             string `json:"error,omitempty"`
}

type Validation struct {
	CartID int64            `json:"cartId"`
	Valid  bool             `json:"valid"`
	Items  []ValidationLine `json:"items"`
}

// Evaluate đối chiếu item với tồn kho (avail = nil: không kiểm) và sản phẩm không bán được.
func Evaluate(c Cart, avail map[string]int32, invalid map[string]string) Validation {
	v := Validation{CartID: c.ID, Valid: len(c.Items) > 0, Items: []ValidationLine{}}
	for _, it := range c.Items {
		l := ValidationLine{ItemID: it.ID, ProductID: it.ProductID, Quantity: it.Quantity, Valid: true}
		if avail != nil {
			l.AvailableQuantity = avail[it.ProductID]
			if l.AvailableQuantity < it.Quantity {
				l.Valid, l.Error = false, fmt.Sprintf("only %d left in stock", l.AvailableQuantity)
			}
		}
		if msg, bad := invalid[it.ProductID]; bad {
			l.Valid, l.Error = false, msg
		}
		v.Valid = v.Valid && l.Valid
		v.Items = append(v.Items, l)
	}
	return v
}
