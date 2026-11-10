package db

import (
	"github.com/pressly/goose/v3/database"
)

func mustStore(table string) database.Store {
	s, err := database.NewStore(database.DialectPostgres, table)
	if err != nil {
		panic(err)
	}
	return s
}
