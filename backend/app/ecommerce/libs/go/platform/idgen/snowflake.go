package idgen

import (
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/bwmarrin/snowflake"
)

type Generator struct{ node *snowflake.Node }

func New(node int64) (*Generator, error) {
	n, err := snowflake.NewNode(node)
	if err != nil {
		return nil, err
	}
	return &Generator{n}, nil
}

func (g *Generator) Next() int64 { return g.node.Generate().Int64() }

func NodeFromEnv() (int64, error) {
	if raw := strings.TrimSpace(os.Getenv("NODE_ID")); raw != "" {
		n, err := strconv.ParseInt(raw, 10, 64)
		if err != nil || n < 0 || n > 1023 {
			return 0, fmt.Errorf("NODE_ID phải trong 0..1023, nhận %q", raw)
		}
		return n, nil
	}
	host := os.Getenv("HOSTNAME")
	if host == "" {
		host = strconv.Itoa(os.Getpid())
	}
	h := sha256.Sum256([]byte(host))
	return int64(binary.BigEndian.Uint32(h[:4]) % 1024), nil
}

func Valid(s string) bool {
	if s == "" || len(s) > 19 {
		return false
	}
	for _, c := range s {
		if c < '0' || c > '9' {
			return false
		}
	}
	return true
}

func Arg(s string) string {
	if Valid(s) {
		return s
	}
	return "-1"
}

type IDs struct{ Gen *Generator }

func (i IDs) NewID() string { return strconv.FormatInt(i.Gen.Next(), 10) }

func FromEnv() (*Generator, error) {
	node, err := NodeFromEnv()
	if err != nil {
		return nil, err
	}
	return New(node)
}
