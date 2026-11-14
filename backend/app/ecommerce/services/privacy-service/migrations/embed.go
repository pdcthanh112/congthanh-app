// Package migrations nhúng các file SQL (goose) vào binary.
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
