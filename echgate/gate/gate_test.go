package gate

import (
	"slices"
	"testing"
)

// isEdgeErrorBody：只许 CF 边缘错误页（`error code: N`）命中，其余一律放行。
//
// 用例取自 2026-10-11 实测的真实响应形态：
//   - javchu @162.159.0.1 的 403 body = "error code: 1034\n"（17 字节，text/plain）；
//   - 站点风控是 5484B 的 HTML 大页；正常页是 310KB 的 HTML——
//     都不能误判成「边缘拒绝」（误剔会把可用 IP 白白删掉）。
func TestIsEdgeErrorBody(t *testing.T) {
	cases := []struct {
		name string
		body string
		want bool
	}{
		{"实测 1034（带换行）", "error code: 1034\n", true},
		{"无换行", "error code: 1000", true},
		{"前导空白", "  error code: 1034\n", true},
		{"64B 截断内的完整前缀", "error code: 1034\ntrailing", true},
		{"空 body", "", false},
		{"前缀不完整（截断）", "error cod", false},
		{"词形近似非 CF 页", "error codex: 1034", false},
		{"站点风控大页", `<!DOCTYPE html><html class="no-js"><title>Just a moment...</title></html>`, false},
		{"站点正常页", `<!DOCTYPE HTML><html lang="zht">`, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := isEdgeErrorBody([]byte(c.body)); got != c.want {
				t.Errorf("isEdgeErrorBody(%q) = %v, want %v", c.body, got, c.want)
			}
		})
	}
}

// filterEdgeRejects：剔除集与保留集都必须保持入参顺序。
//
// 入参顺序 = 握手延迟序（拨号优先级），保序被打乱会静默改变每一条出站的
// IP 选择——这正是本函数要防的回归面。用可替换的 edgeProbe 避免真实网络。
func TestFilterEdgeRejectsKeepsInputOrder(t *testing.T) {
	orig := edgeProbe
	defer func() { edgeProbe = orig }()
	edgeProbe = func(ip, sni string) bool { return ip == "b" || ip == "d" }

	kept, rejected := filterEdgeRejects([]string{"a", "b", "c", "d"}, "test.invalid")
	if want := []string{"a", "c"}; !slices.Equal(kept, want) {
		t.Errorf("kept = %v, want %v（须保持入参顺序）", kept, want)
	}
	if want := []string{"b", "d"}; !slices.Equal(rejected, want) {
		t.Errorf("rejected = %v, want %v（须保持入参顺序）", rejected, want)
	}
}

// filterEdgeRejects：无剔除时原样返回；空输入安全。
func TestFilterEdgeRejectsNoReject(t *testing.T) {
	orig := edgeProbe
	defer func() { edgeProbe = orig }()
	edgeProbe = func(ip, sni string) bool { return false }

	kept, rejected := filterEdgeRejects([]string{"a", "b"}, "test.invalid")
	if want := []string{"a", "b"}; !slices.Equal(kept, want) {
		t.Errorf("kept = %v, want %v", kept, want)
	}
	if len(rejected) != 0 {
		t.Errorf("rejected = %v, want 空", rejected)
	}

	kept, rejected = filterEdgeRejects(nil, "test.invalid")
	if len(kept) != 0 || len(rejected) != 0 {
		t.Errorf("空输入应返回空集，得到 kept=%v rejected=%v", kept, rejected)
	}
}

// filterEdgeRejects：全部被剔除时如实返回（调用方 probeCandidates 用
// 「kept 为空」判定是否回退原列表——这个信号不能被吞掉）。
func TestFilterEdgeRejectsAllRejected(t *testing.T) {
	orig := edgeProbe
	defer func() { edgeProbe = orig }()
	edgeProbe = func(ip, sni string) bool { return true }

	kept, rejected := filterEdgeRejects([]string{"x", "y"}, "test.invalid")
	if len(kept) != 0 {
		t.Errorf("kept = %v, want 空（调用方据此回退原列表）", kept)
	}
	if want := []string{"x", "y"}; !slices.Equal(rejected, want) {
		t.Errorf("rejected = %v, want %v", rejected, want)
	}
}
