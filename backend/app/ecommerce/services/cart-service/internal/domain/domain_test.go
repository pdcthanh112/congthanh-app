package domain

import "testing"

func TestPolicy(t *testing.T) {
	p := Policy{MaxItemQuantity: 10, MaxItemsPerCart: 2}
	cases := []struct {
		count        int
		current, add int32
		ok           bool
	}{
		{0, 0, 1, true},
		{2, 0, 1, false}, // giỏ đầy, sản phẩm mới
		{2, 3, 1, true},  // giỏ đầy nhưng cộng dồn sản phẩm cũ
		{1, 8, 3, false}, // vượt số lượng mỗi sản phẩm
		{0, 0, 0, false},
	}
	for _, c := range cases {
		if err := p.CheckAdd(c.count, c.current, c.add); (err == nil) != c.ok {
			t.Errorf("CheckAdd(%d,%d,%d) = %v", c.count, c.current, c.add, err)
		}
	}
}

func TestCartStatus(t *testing.T) {
	c, err := NewCart(" A ", "u1", true)
	if err != nil || c.Name != "A" {
		t.Fatal(c, err)
	}
	if err := c.SetStatus(StatusInactive); err != nil || c.IsDefault {
		t.Fatal("inactive cart must lose default")
	}
	if c.MakeDefault() == nil {
		t.Fatal("inactive cart cannot be default")
	}
	if _, err := NewCart("", "u1", false); KindOf(err) != KindInvalid {
		t.Fatal("empty name must be invalid")
	}
}

func TestEvaluate(t *testing.T) {
	c := Cart{ID: 1, Items: []Item{{ID: 1, ProductID: "a", Quantity: 2}, {ID: 2, ProductID: "b", Quantity: 2}, {ID: 3, ProductID: "c", Quantity: 1}}}
	v := Evaluate(c, map[string]int32{"a": 5, "b": 1, "c": 9}, map[string]string{"c": "discontinued"})
	if v.Valid || !v.Items[0].Valid || v.Items[1].Valid || v.Items[2].Error != "discontinued" {
		t.Fatalf("%+v", v)
	}
	if Evaluate(Cart{ID: 2}, nil, nil).Valid {
		t.Fatal("empty cart is not valid")
	}
}
