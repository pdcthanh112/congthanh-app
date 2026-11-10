package domain_test

import (
	"testing"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

func TestAllocatePrefersLargestWarehouseAndSplits(t *testing.T) {
	stocks := []domain.Stock{
		{ID: 1, WarehouseID: "A", Quantity: 3},
		{ID: 2, WarehouseID: "B", Quantity: 5, Reserved: 1},
		{ID: 3, WarehouseID: "C", Quantity: 9, Discontinued: true},
	}
	al := domain.Allocate(domain.Item{ProductID: "p", Quantity: 6}, stocks)
	if !al.Complete() || len(al.Items) != 2 || al.Items[0].WarehouseID != "B" || al.Items[0].Quantity != 4 || al.Items[1].Quantity != 2 {
		t.Fatalf("allocation = %+v", al)
	}
	short := domain.Allocate(domain.Item{ProductID: "p", Quantity: 8}, stocks)
	if short.Complete() || short.Line.Reserved != 7 || short.Line.Error == "" {
		t.Fatalf("short allocation = %+v", short)
	}
}

func TestStockInvariants(t *testing.T) {
	s := domain.Stock{Quantity: 5}
	if err := s.Reserve(6); domain.KindOf(err) != domain.KindConflict {
		t.Fatal("over-reserve must fail")
	}
	_ = s.Reserve(4)
	if err := s.Adjust(-2); domain.KindOf(err) != domain.KindConflict {
		t.Fatal("cannot drop below reserved")
	}
	s.Commit(3, 4)
	if s.Quantity != 2 || s.Reserved != 0 {
		t.Fatalf("after commit %+v", s)
	}
}

func TestComputeLevel(t *testing.T) {
	cases := map[domain.LevelStatus][]domain.Stock{
		domain.LevelOutOfStock:   nil,
		domain.LevelBackorder:    {{Quantity: 0, InTransit: 5}},
		domain.LevelLowStock:     {{Quantity: 3, ReorderPoint: 5}},
		domain.LevelInStock:      {{Quantity: 30, ReorderPoint: 5}},
		domain.LevelDiscontinued: {{Quantity: 30, Discontinued: true}},
	}
	for want, st := range cases {
		if got := domain.ComputeLevel("p", st).Status; got != want {
			t.Errorf("want %s got %s", want, got)
		}
	}
}

func TestMergeItems(t *testing.T) {
	m, err := domain.MergeItems([]domain.Item{{"b", 1}, {"a", 2}, {"b", 3}})
	if err != nil || len(m) != 2 || m[0].ProductID != "a" || m[1].Quantity != 4 {
		t.Fatalf("merge = %+v %v", m, err)
	}
	if _, err := domain.MergeItems([]domain.Item{{"a", 0}}); domain.KindOf(err) != domain.KindInvalid {
		t.Fatal("zero quantity must be invalid")
	}
}
