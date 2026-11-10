package domain

import (
	"strings"
	"time"
)

type Warehouse struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	Location  string    `json:"location"`
	Address   string    `json:"address"`
	Active    bool      `json:"isActive"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

func NewWarehouse(id, name, location, address string, active bool) (Warehouse, error) {
	id, name = strings.TrimSpace(id), strings.TrimSpace(name)
	if id == "" || name == "" {
		return Warehouse{}, Invalid("id and name are required")
	}
	return Warehouse{ID: id, Name: name, Location: location, Address: address, Active: active}, nil
}
