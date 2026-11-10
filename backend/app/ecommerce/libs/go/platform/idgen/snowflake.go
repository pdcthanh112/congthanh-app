// Package idgen sinh ID snowflake 64-bit, tương thích kiểu bigint mà các service Java dùng.
package idgen

import "github.com/bwmarrin/snowflake"

type Generator struct{ node *snowflake.Node }

// New nhận node id 0..1023 (Java cũ dùng worker-id + datacenter-id; ở đây gộp lại).
func New(node int64) (*Generator, error) {
	n, err := snowflake.NewNode(node)
	if err != nil {
		return nil, err
	}
	return &Generator{n}, nil
}

func (g *Generator) Next() int64 { return g.node.Generate().Int64() }
