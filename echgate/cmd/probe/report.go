package main

import (
	"fmt"
	"io"
	"slices"
	"strings"
)

// ECHInfo 本次运行用的 ECH 配置来源（-ech 时才有）。
type ECHInfo struct {
	Source string `json:"source"`
	Bytes  int    `json:"bytes"`
}

// Fetch 一次"握手后取小资源"的结果（-fetch 时才有）。
type Fetch struct {
	Proto   string `json:"proto,omitempty"` // 实际协商到的协议（HTTP/2.0 / HTTP/3.0）
	Status  int    `json:"status,omitempty"`
	TTFBMs  int64  `json:"ttfb_ms,omitempty"`
	TotalMs int64  `json:"total_ms,omitempty"`
	Bytes   int    `json:"bytes,omitempty"`
	Err     string `json:"err,omitempty"`
}

// ModeResult 一个（目标 IP × 模式）格的握手统计。
type ModeResult struct {
	Mode   string   `json:"mode"`
	N      int      `json:"n"`
	OK     int      `json:"ok"`
	UseECH bool     `json:"use_ech,omitempty"` // 该模式是否带 ECH（决定 ECHOK 是否有意义）
	ECHOK  int      `json:"ech_accepted,omitempty"`
	MS     []int64  `json:"ms,omitempty"`     // 成功次数的握手耗时（ms，尝试顺序）
	Errors []string `json:"errors,omitempty"` // 失败样本（去重、≤3 条）
	Fetch  *Fetch   `json:"fetch,omitempty"`
}

// IPResult 单个目标 IP 的全部模式。
type IPResult struct {
	IP    string       `json:"ip"`
	Modes []ModeResult `json:"modes"`
}

// ModeSummary 跨目标的按模式汇总（写文档直接抄这一段）。
type ModeSummary struct {
	Mode   string `json:"mode"`
	N      int    `json:"n"`
	OK     int    `json:"ok"`
	UseECH bool   `json:"use_ech,omitempty"`
	ECHOK  int    `json:"ech_accepted,omitempty"`
	P50    int64  `json:"p50_ms"`
	Min    int64  `json:"min_ms"`
	Max    int64  `json:"max_ms"`
}

// Report 一次完整运行的结果（-json 时整份序列化）。
type Report struct {
	Time      string        `json:"time"`
	SNI       string        `json:"sni"`
	Port      int           `json:"port"`
	N         int           `json:"n"`
	ECH       *ECHInfo      `json:"ech,omitempty"`
	FetchPath string        `json:"fetch_path,omitempty"`
	Targets   []IPResult    `json:"targets"`
	Summary   []ModeSummary `json:"summary"`
}

// summarize 跨目标按模式聚合：总数、成功数、耗时 p50/min/max。
// 模式顺序按首次出现的顺序（即 modesFor 的定义序）。
func summarize(targets []IPResult) []ModeSummary {
	type agg struct {
		ok, n int
		ech   bool
		echOK int
		ms    []int64
	}
	order := make([]string, 0, 4)
	aggs := make(map[string]*agg, 4)
	for _, t := range targets {
		for _, m := range t.Modes {
			a := aggs[m.Mode]
			if a == nil {
				a = &agg{}
				aggs[m.Mode] = a
				order = append(order, m.Mode)
			}
			a.ok += m.OK
			a.n += m.N
			a.ech = a.ech || m.UseECH
			a.echOK += m.ECHOK
			a.ms = append(a.ms, m.MS...)
		}
	}
	out := make([]ModeSummary, 0, len(order))
	for _, name := range order {
		a := aggs[name]
		lo, hi := minMaxOf(a.ms)
		out = append(out, ModeSummary{
			Mode: name, OK: a.ok, N: a.n, UseECH: a.ech, ECHOK: a.echOK,
			P50: medianOf(a.ms), Min: lo, Max: hi,
		})
	}
	return out
}

// medianOf 取中位数：偶数样本时为 s[len/2]（偏大的一侧；探测样本量下
// 与偏小侧无实质差异，固定成一种约定避免歧义）。
func medianOf(v []int64) int64 {
	if len(v) == 0 {
		return 0
	}
	s := slices.Clone(v)
	slices.Sort(s)
	return s[len(s)/2]
}

func minMaxOf(v []int64) (int64, int64) {
	if len(v) == 0 {
		return 0, 0
	}
	lo, hi := v[0], v[0]
	for _, x := range v[1:] {
		if x < lo {
			lo = x
		}
		if x > hi {
			hi = x
		}
	}
	return lo, hi
}

// rateText “10/10 (100%)”式呈现；n=0 时为 “-”。
func rateText(ok, n int) string {
	if n == 0 {
		return "-"
	}
	return fmt.Sprintf("%d/%d (%.0f%%)", ok, n, float64(ok)*100/float64(n))
}

// msText “p50=132ms (98–301)”式呈现；无样本时为 “-”。
func msText(ms []int64) string {
	if len(ms) == 0 {
		return "-"
	}
	lo, hi := minMaxOf(ms)
	return fmt.Sprintf("p50=%dms (%d–%d)", medianOf(ms), lo, hi)
}

// printText 人读输出：逐目标细表 + 跨目标汇总。
func printText(w io.Writer, r *Report) {
	echoLine := ""
	if r.ECH != nil {
		echoLine = fmt.Sprintf("  ECH: %s（%d 字节）\n", r.ECH.Source, r.ECH.Bytes)
	}
	fetchLine := "(未开 -fetch，只测握手)"
	if r.FetchPath != "" {
		fetchLine = "GET " + r.FetchPath
	}
	fmt.Fprintf(w, "probe %s  sni=%s port=%d n=%d  %s\n%s",
		r.Time, r.SNI, r.Port, r.N, fetchLine, echoLine)
	fmt.Fprintln(w, "\n== 逐目标 ==")
	for _, t := range r.Targets {
		fmt.Fprintf(w, "%s\n", t.IP)
		for _, m := range t.Modes {
			fmt.Fprintf(w, "  %-9s %-12s 握手 %s", m.Mode, rateText(m.OK, m.N), msText(m.MS))
			if m.UseECH {
				fmt.Fprintf(w, "  ECH 接受 %d/%d", m.ECHOK, m.OK)
			}
			if m.Fetch != nil {
				f := m.Fetch
				if f.Err != "" {
					fmt.Fprintf(w, "\n  %-9s   GET 失败：%s", "", shortErr(f.Err))
				} else {
					fmt.Fprintf(w, "\n  %-9s   GET → %d %s ttfb=%dms total=%dms %dB",
						"", f.Status, f.Proto, f.TTFBMs, f.TotalMs, f.Bytes)
				}
			}
			if len(m.Errors) > 0 {
				for _, e := range m.Errors {
					fmt.Fprintf(w, "\n  %-9s   err: %s", "", shortErr(e))
				}
			}
			fmt.Fprintln(w)
		}
	}
	fmt.Fprintln(w, "== 汇总（跨 IP）==")
	for _, s := range r.Summary {
		fmt.Fprintf(w, "  %-9s %-12s 握手 p50=%dms (min %d / max %d)",
			s.Mode, rateText(s.OK, s.N), s.P50, s.Min, s.Max)
		if s.UseECH {
			fmt.Fprintf(w, "  ECH 接受 %d/%d", s.ECHOK, s.OK)
		}
		fmt.Fprintln(w)
	}
}

// shortErr 压缩错误文本（去掉换行、限长），防表格被长错误刷碎。
func shortErr(s string) string {
	s = strings.ReplaceAll(s, "\n", " ")
	const max = 120
	if len(s) > max {
		s = s[:max] + "…"
	}
	return s
}
