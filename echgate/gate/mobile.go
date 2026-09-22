package gate

// gomobile 绑定 facade。
//
// gobind 不支持 struct 参数与 []string 字段（Config 会被整个跳过），
// 故这里提供扁平签名的入口：列表走逗号分隔字符串（与 CLI flag 同形状），
// 返回 (*Server, error)（对象 + 错误是支持的）。
// CLI (`main.go`) 与 gomobile (Android `.aar` / iOS framework) 共用 [Start]。
func StartFlat(
	listenAddr string,
	cfIPsCSV string,
	cfHostsCSV string,
	dohURL string,
	echDomain string,
	echB64 string,
	cacheDir string,
	timeoutMs int64,
) (*Server, error) {
	return Start(Config{
		ListenAddr: listenAddr,
		CfIPs:      SplitList(cfIPsCSV),
		CfHosts:    SplitList(cfHostsCSV),
		DohURL:     dohURL,
		EchDomain:  echDomain,
		EchB64:     echB64,
		CacheDir:   cacheDir,
		TimeoutMs:  timeoutMs,
	})
}
