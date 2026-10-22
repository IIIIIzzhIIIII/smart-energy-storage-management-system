"""
全局配置：数据源切换、模型参数、天气配置。

改这里即可在「合成数据 / 公开数据集」之间一键切换，
算法代码(data/loader.py 及 forecast/)零改动。

敏感配置（天气 API Key 等）请写入同目录下的 .env 文件或环境变量，
该 .env 已被 .gitignore 忽略，切勿提交到仓库。
"""
import os
import sys

# 加载本地 .env（不存在则忽略），用于注入天气 API Key 等敏感配置。
# 开发态：config.py 所在目录；冻结态：python-ems.exe 所在目录。
try:
    from dotenv import load_dotenv

    if getattr(sys, "frozen", False):
        BASE_DIR = os.path.dirname(os.path.abspath(sys.executable))
    else:
        BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    load_dotenv(os.path.join(BASE_DIR, ".env"))
except ImportError:  # 未安装 python-dotenv 时退化为直接读取系统环境变量
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# ============ 数据源 ============
# 可选值: "synthetic"（自造合成数据） / "csv"（公开数据集，快速切换）
DATA_SOURCE = "synthetic"

# 当 DATA_SOURCE == "csv" 时，填写 CSV 路径及各列在原始文件中的列名
CSV_PATH = "data/public_dataset.csv"
# 冻结后工作目录可能变化，相对路径统一按 BASE_DIR 解析
if not os.path.isabs(CSV_PATH):
    CSV_PATH = os.path.join(BASE_DIR, CSV_PATH)
CSV_COLUMNS = {
    "timestamp": "timestamp",       # 原始CSV中的时间列名
    "pv_power_kw": "pv",            # 光伏功率列名
    "load_power_kw": "load",        # 负荷功率列名
    "price_yuan_kwh": "price",      # 电价列名（无此列则填 None）
}

# ============ 合成数据参数 ============
SYNTHETIC_DAYS = 90          # 生成天数（SARIMA 需要足够历史）
SYNTHETIC_FREQ = "1h"        # 采样频率（统一按小时，日周期 s=24）

# ============ 预测模型参数 ============
FORECAST_STEPS = 24          # 预测步数（小时）
SARIMA_ORDER = (1, 1, 1)                 # (p, d, q)
SARIMA_SEASONAL_ORDER = (1, 1, 1, 24)    # (P, D, Q, s)，s=24 日周期
GARCH_P = 1
GARCH_Q = 1

# ============ 天气配置（阶段1.5使用）============
# 敏感值从环境变量 / .env 读取，切勿硬编码到源码里提交
WEATHER = {
    "api_key": os.getenv("WEATHER_API_KEY", ""),
    # 和风天气「个人专属 API Host」：登录 console.qweather.com -> 左侧「设置」
    "api_host": os.getenv("WEATHER_API_HOST", "https://devapi.qweather.com"),
    "latitude": 39.92,       # 终端设备纬度（按实际修改）
    "longitude": 116.41,     # 终端设备经度（按实际修改）
    "cache_minutes": 10,     # 缓存间隔（分钟），避免频繁调用
}

# ============ 电价（与后端 ReportService 保持一致）============
PRICE_PEAK = 0.95
PRICE_FLAT = 0.60
PRICE_VALLEY = 0.35
