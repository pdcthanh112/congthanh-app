// Package integration_test chạy use case thuế trên Postgres thật (bỏ qua khi thiếu TEST_DATABASE_URL).
package integration_test

import (
	"context"
	"testing"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db/dbtest"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/migrations"
)

func TestTaxFlow(t *testing.T) {
	svc := app.NewService(postgres.New(dbtest.New(t, "tax", migrations.FS)))
	ctx := context.Background()

	classes, err := svc.ListClasses(ctx)
	if err != nil || len(classes) != 3 {
		t.Fatalf("seed classes: %v %v", classes, err)
	}
	std := classes[0].ID

	if _, err := svc.CreateClass(ctx, "Standard"); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatalf("duplicate class: want conflict, got %v", err)
	}
	if _, err := svc.CreateRate(ctx, domain.TaxRate{TaxClassID: 999, CountryCode: "US", RatePercent: 1}); apperr.KindOf(err) != apperr.KindNotFound {
		t.Fatalf("unknown class: want not found, got %v", err)
	}
	// San Francisco: 7.25% của bang + thuế địa phương.
	sf, err := svc.CreateRate(ctx, domain.TaxRate{TaxClassID: std, CountryCode: "us", RegionCode: "ca", PostalCode: "94105", RatePercent: 8.625})
	if err != nil || sf.CountryCode != "US" || sf.RegionCode != "CA" {
		t.Fatalf("create rate: %+v %v", sf, err)
	}
	if _, err := svc.CreateRate(ctx, domain.TaxRate{TaxClassID: std, CountryCode: "US", RegionCode: "CA", PostalCode: "94105", RatePercent: 9}); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatalf("duplicate rate: want conflict, got %v", err)
	}

	rates, err := svc.Rates(ctx, []int64{std, 424242}, domain.Address{CountryCode: "US", RegionCode: "CA", PostalCode: "94105-1001"})
	if err != nil || rates[0].RatePercent != 8.625 || !rates[0].Matched || rates[1].Matched {
		t.Fatalf("rates: %+v %v", rates, err)
	}

	out, total, err := svc.Calculate(ctx, domain.Address{CountryCode: "US", RegionCode: "NY"}, []domain.Line{
		{LineID: "p1", TaxClassID: std, TaxableMinor: 10000, Currency: "USD"},
		{LineID: "p2", TaxClassID: classes[1].ID, TaxableMinor: 10000, Currency: "USD"},
	})
	if err != nil || out[0].TaxMinor != 400 || out[1].TaxMinor != 0 || total != 400 {
		t.Fatalf("calculate: %+v total=%d %v", out, total, err)
	}

	sf.RatePercent = 8.5
	if err := svc.UpdateRate(ctx, sf); err != nil {
		t.Fatal(err)
	}
	if got, _ := svc.GetRate(ctx, sf.ID); got.RatePercent != 8.5 {
		t.Fatalf("update rate: %+v", got)
	}
	if err := svc.DeleteClass(ctx, std); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.GetRate(ctx, sf.ID); apperr.KindOf(err) != apperr.KindNotFound {
		t.Fatalf("rate must cascade with class, got %v", err)
	}
	list, total2, err := svc.ListRates(ctx, 0, 10)
	if err != nil || total2 != 2 || len(list) != 2 {
		t.Fatalf("list: %d %d %v", len(list), total2, err)
	}
}
