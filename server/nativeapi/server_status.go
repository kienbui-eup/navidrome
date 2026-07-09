package nativeapi

import (
	"bufio"
	"context"
	"encoding/json"
	"net/http"
	"os"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/vi2play/vi2play/log"
)

var startTime = time.Now()

type CPUInfo struct {
	Model   string    `json:"model"`
	Cores   int       `json:"cores"`
	Load1m  float64   `json:"load1m"`
	Load5m  float64   `json:"load5m"`
	Load15m float64   `json:"load15m"`
}

type MemoryInfo struct {
	Total      uint64  `json:"total"`
	Available  uint64  `json:"available"`
	Used       uint64  `json:"used"`
	Percentage float64 `json:"percentage"`
}

type DiskInfo struct {
	Total      uint64  `json:"total"`
	Available  uint64  `json:"available"`
	Used       uint64  `json:"used"`
	Percentage float64 `json:"percentage"`
}

type AppInfo struct {
	GoVersion     string `json:"goVersion"`
	NumGoroutines int    `json:"numGoroutines"`
	AllocBytes    uint64 `json:"allocBytes"`
	SysBytes      uint64 `json:"sysBytes"`
}

type ServerStatusResponse struct {
	CPU    CPUInfo    `json:"cpu"`
	Memory MemoryInfo `json:"memory"`
	Disk   DiskInfo   `json:"disk"`
	Uptime float64    `json:"uptime"`
	App    AppInfo    `json:"app"`
}

func (api *Router) addServerStatusRoute(r chi.Router) {
	r.Get("/server/status", api.serverStatusHandler)
}

func (api *Router) serverStatusHandler(w http.ResponseWriter, r *http.Request) {
	status := ServerStatusResponse{
		CPU:    getCPUInfo(r.Context()),
		Memory: getMemoryInfo(r.Context()),
		Disk:   getDiskInfo(r.Context()),
		Uptime: getUptime(r.Context()),
		App:    getAppInfo(),
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(status); err != nil {
		log.Error(r.Context(), "failed to encode server status response", err)
		http.Error(w, "internal server error", http.StatusInternalServerError)
	}
}

func getCPUInfo(ctx context.Context) CPUInfo {
	info := CPUInfo{
		Model: "Generic CPU",
		Cores: runtime.NumCPU(),
	}

	// 1. Read model name from /proc/cpuinfo
	if f, err := os.Open("/proc/cpuinfo"); err == nil {
		defer f.Close()
		scanner := bufio.NewScanner(f)
		for scanner.Scan() {
			line := scanner.Text()
			if strings.HasPrefix(line, "model name") {
				parts := strings.Split(line, ":")
				if len(parts) > 1 {
					info.Model = strings.TrimSpace(parts[1])
					break
				}
			}
		}
	}

	// 2. Read load avg from /proc/loadavg
	if f, err := os.Open("/proc/loadavg"); err == nil {
		defer f.Close()
		scanner := bufio.NewScanner(f)
		if scanner.Scan() {
			fields := strings.Fields(scanner.Text())
			if len(fields) >= 3 {
				l1, _ := strconv.ParseFloat(fields[0], 64)
				l5, _ := strconv.ParseFloat(fields[1], 64)
				l15, _ := strconv.ParseFloat(fields[2], 64)
				info.Load1m = l1
				info.Load5m = l5
				info.Load15m = l15
			}
		}
	}

	return info
}

func getMemoryInfo(ctx context.Context) MemoryInfo {
	info := MemoryInfo{}

	// Read /proc/meminfo
	if f, err := os.Open("/proc/meminfo"); err == nil {
		defer f.Close()
		scanner := bufio.NewScanner(f)
		var totalKB, availKB uint64
		for scanner.Scan() {
			line := scanner.Text()
			fields := strings.Fields(line)
			if len(fields) >= 2 {
				key := strings.TrimSuffix(fields[0], ":")
				val, _ := strconv.ParseUint(fields[1], 10, 64)
				if key == "MemTotal" {
					totalKB = val
				} else if key == "MemAvailable" {
					availKB = val
				}
			}
		}

		if totalKB > 0 {
			info.Total = totalKB * 1024
			info.Available = availKB * 1024
			if info.Available == 0 {
				info.Available = info.Total
			}
			info.Used = info.Total - info.Available
			info.Percentage = (float64(info.Used) / float64(info.Total)) * 100.0
		}
	}

	// Fallback for macOS/development
	if info.Total == 0 {
		var m runtime.MemStats
		runtime.ReadMemStats(&m)
		info.Total = m.Sys
		info.Available = m.Sys - m.Alloc
		info.Used = m.Alloc
		info.Percentage = (float64(info.Used) / float64(info.Total)) * 100.0
	}

	return info
}

func getDiskInfo(ctx context.Context) DiskInfo {
	info := DiskInfo{}
	var stat syscall.Statfs_t

	dir := "/mnt/stateful_partition"
	if _, err := os.Stat(dir); err != nil {
		dir = "/"
	}

	if err := syscall.Statfs(dir, &stat); err == nil {
		total := uint64(stat.Blocks) * uint64(stat.Bsize)
		free := uint64(stat.Bavail) * uint64(stat.Bsize)
		if total > 0 {
			info.Total = total
			info.Available = free
			info.Used = total - free
			info.Percentage = (float64(info.Used) / float64(info.Total)) * 100.0
		}
	}

	return info
}

func getUptime(ctx context.Context) float64 {
	// Read /proc/uptime
	if f, err := os.Open("/proc/uptime"); err == nil {
		defer f.Close()
		scanner := bufio.NewScanner(f)
		if scanner.Scan() {
			fields := strings.Fields(scanner.Text())
			if len(fields) > 0 {
				up, _ := strconv.ParseFloat(fields[0], 64)
				if up > 0 {
					return up
				}
			}
		}
	}

	// Fallback to app uptime
	return time.Since(startTime).Seconds()
}

func getAppInfo() AppInfo {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	return AppInfo{
		GoVersion:     runtime.Version(),
		NumGoroutines: runtime.NumGoroutine(),
		AllocBytes:    m.Alloc,
		SysBytes:      m.Sys,
	}
}
