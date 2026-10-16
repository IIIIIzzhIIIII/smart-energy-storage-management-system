"""
合成数据生成器：生成贴近真实的光伏 / 负荷 / 电价小时级时序。

设计要点（保证 SARIMA/ARCH/Copula/DRO 都能训练）：
- 光伏：白天正弦曲线（日出日落），夜间为 0，受"逐日云量 + 逐时扰动"影响；
- 负荷：早晚高峰、午间次峰、深夜低谷，工作日高于周末；
- 电价：峰 / 平 / 谷三段，与后端 ReportService 保持一致。
"""
import numpy as np
import pandas as pd


def _pv_power(hour, cloud):
    """光伏功率(kW)：6~19 点正弦，正午最强，受云量衰减。额定 50kW。"""
    if hour < 6 or hour > 19:
        return 0.0
    t = (hour - 6) / 13.0 * np.pi
    irradiance = np.sin(t) * 1000.0          # W/m2
    power = irradiance / 1000.0 * 50.0        # 50kW 额定
    return max(0.0, power * cloud)


def _load_power(hour, weekday):
    """负荷功率(kW)：早晚高峰、午间次峰、深夜低谷，周末略低。"""
    if 7 <= hour <= 9:
        base = 320
    elif 11 <= hour <= 13:
        base = 300
    elif 18 <= hour <= 22:
        base = 360
    elif hour >= 23 or hour < 5:
        base = 90
    else:
        base = 150
    factor = 1.0 if weekday < 5 else 0.85
    return base * factor


def _price(hour):
    """峰平谷电价(元/kWh)。"""
    if hour >= 23 or hour < 7:
        return 0.35
    if 10 <= hour < 12 or 17 <= hour < 21:
        return 0.95
    return 0.60


def generate_synthetic(days=90, freq="1h", seed=42):
    """
    生成 days 天、freq 频率的合成数据。

    返回 DataFrame，列为统一 schema：
        timestamp | pv_power_kw | load_power_kw | price_yuan_kwh
    """
    rng = np.random.default_rng(seed)

    # 逐日云量基准（0=晴 ~ 1=阴），体现"某几天是阴天"的天气连续性
    day_cloud = rng.uniform(0.2, 1.0, size=days)

    idx = pd.date_range(
        end=pd.Timestamp.now().normalize(),
        periods=days * 24,
        freq=freq,
    )

    rows = []
    for i, ts in enumerate(idx):
        day_idx = i // 24
        hour = ts.hour
        weekday = ts.weekday()

        cloud = float(np.clip(day_cloud[day_idx] + rng.normal(0, 0.12), 0.1, 1.0))
        pv = _pv_power(hour, cloud) + rng.normal(0, 3)
        load = _load_power(hour, weekday) + rng.normal(0, 12)

        rows.append({
            "timestamp": ts,
            "pv_power_kw": round(max(0.0, pv), 2),
            "load_power_kw": round(max(0.0, load), 2),
            "price_yuan_kwh": _price(hour),
        })

    return pd.DataFrame(rows)


if __name__ == "__main__":
    df = generate_synthetic(days=7)
    print(df.head(10))
    print("...")
    print(df.describe())
