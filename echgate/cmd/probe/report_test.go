package main

import "testing"

func TestMedianOf(t *testing.T) {
	cases := []struct {
		in   []int64
		want int64
	}{
		{nil, 0},
		{[]int64{5}, 5},
		{[]int64{1, 3, 2}, 2},
		{[]int64{10, 1, 5, 3}, 5}, // sorted [1,3,5,10] → s[4/2]=s[2]=5（偶数取偏大侧，见实现注释）
		{[]int64{301, 98, 132, 150}, 150},
	}
	for _, c := range cases {
		if got := medianOf(c.in); got != c.want {
			t.Errorf("medianOf(%v) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestRateText(t *testing.T) {
	cases := []struct {
		ok, n int
		want  string
	}{
		{0, 0, "-"},
		{10, 10, "10/10 (100%)"},
		{9, 10, "9/10 (90%)"},
		{0, 5, "0/5 (0%)"},
	}
	for _, c := range cases {
		if got := rateText(c.ok, c.n); got != c.want {
			t.Errorf("rateText(%d,%d) = %q, want %q", c.ok, c.n, got, c.want)
		}
	}
}

func TestSummarize(t *testing.T) {
	targets := []IPResult{
		{
			IP: "10.0.0.1",
			Modes: []ModeResult{
				{Mode: "h2+ech", N: 3, OK: 3, UseECH: true, ECHOK: 3, MS: []int64{100, 110, 120}},
				{Mode: "h3+ech", N: 3, OK: 2, UseECH: true, ECHOK: 2, MS: []int64{90, 95}, Errors: []string{"timeout"}},
			},
		},
		{
			IP: "10.0.0.2",
			Modes: []ModeResult{
				{Mode: "h2+ech", N: 2, OK: 1, UseECH: true, ECHOK: 1, MS: []int64{130}, Errors: []string{"EOF"}},
				{Mode: "h3+ech", N: 2, OK: 2, UseECH: true, ECHOK: 1, MS: []int64{80, 84}},
			},
		},
	}
	got := summarize(targets)
	if len(got) != 2 {
		t.Fatalf("summarize 返回 %d 组，want 2", len(got))
	}
	h2 := got[0]
	if h2.Mode != "h2+ech" || h2.OK != 4 || h2.N != 5 {
		t.Errorf("h2+ech 聚合 = %+v, want OK=4 N=5", h2)
	}
	if !h2.UseECH || h2.ECHOK != 4 {
		t.Errorf("h2+ech ECH 聚合 = %+v, want UseECH=true ECHOK=4", h2)
	}
	// sorted [100,110,120,130] → s[4/2]=s[2]=120
	if h2.P50 != 120 || h2.Min != 100 || h2.Max != 130 {
		t.Errorf("h2+ech 延迟 = p50=%d min=%d max=%d, want 120/100/130", h2.P50, h2.Min, h2.Max)
	}
	h3 := got[1]
	if h3.Mode != "h3+ech" || h3.OK != 4 || h3.N != 5 {
		t.Errorf("h3+ech 聚合 = %+v, want OK=4 N=5", h3)
	}
	if h3.ECHOK != 3 {
		t.Errorf("h3+ech ECH 聚合 ECHOK = %d, want 3", h3.ECHOK)
	}
	// sorted [80,84,90,95] → s[4/2]=s[2]=90
	if h3.P50 != 90 || h3.Min != 80 || h3.Max != 95 {
		t.Errorf("h3+ech 延迟 = p50=%d min=%d max=%d, want 90/80/95", h3.P50, h3.Min, h3.Max)
	}
}

func TestAppendErr(t *testing.T) {
	var errs []string
	errs = appendErr(errs, "a")
	errs = appendErr(errs, "a") // 去重
	errs = appendErr(errs, "b")
	errs = appendErr(errs, "c")
	errs = appendErr(errs, "d") // 超 3 条截断
	if len(errs) != 3 || errs[0] != "a" || errs[1] != "b" || errs[2] != "c" {
		t.Errorf("appendErr = %v, want [a b c]", errs)
	}
}

func TestModesFor(t *testing.T) {
	withECH := modesFor(true)
	if len(withECH) != 3 || withECH[0].Name != "h2+ech" || withECH[1].Name != "h3+ech" || withECH[2].Name != "h3-clear" {
		t.Errorf("useECH 模式表异常：%+v", withECH)
	}
	if withECH[2].ECH {
		t.Error("h3-clear 不应带 ECH")
	}
	plain := modesFor(false)
	if len(plain) != 2 || plain[0].Name != "h2" || plain[1].Name != "h3" {
		t.Errorf("无 ECH 模式表异常：%+v", plain)
	}
}

func TestExtractECHParam(t *testing.T) {
	rec := `1 . alpn="h3,h2" ipv4hint="104.18.0.1" ech="AEX+DQBB" ipv6hint="::1"`
	if got := extractECHParam(rec); got != "AEX+DQBB" {
		t.Errorf("extractECHParam = %q, want AEX+DQBB", got)
	}
	if got := extractECHParam(`1 . alpn="h3"`); got != "" {
		t.Errorf("无 ech 参数应返回空，得到 %q", got)
	}
}
