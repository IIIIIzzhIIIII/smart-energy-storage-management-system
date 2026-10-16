"""
命令行演示脚本（不依赖 FastAPI，直接跑通整个预测链路）。

用法（在 python-ems 目录下）：
    python demo.py

效果：生成数据 -> SARIMA+ARCH 预测 -> 打印未来预测 -> 保存图表 forecast_pv.png
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

# 中文字体，避免图表中文显示为方框
plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

import config
from data.loader import load_data
from forecast.sarima_arch import forecast as sarima_forecast


def main():
    df = load_data()
    print(f"数据源: {config.DATA_SOURCE}")
    print(f"样本数: {len(df)}  时间范围: {df['timestamp'].min()} ~ {df['timestamp'].max()}")
    print(df.head())

    series = df.set_index("timestamp")["pv_power_kw"]
    steps = config.FORECAST_STEPS

    mean, lower, upper, vol = sarima_forecast(
        series,
        steps=steps,
        order=config.SARIMA_ORDER,
        seasonal_order=config.SARIMA_SEASONAL_ORDER,
        garch_p=config.GARCH_P,
        garch_q=config.GARCH_Q,
    )

    future = pd.date_range(
        start=series.index[-1] + pd.Timedelta(hours=1),
        periods=steps,
        freq="1h",
    )

    print("\n未来 24 小时光伏功率预测 (SARIMA+ARCH):")
    print(f"{'时间':>16} {'均值(kW)':>10} {'95%CI下界':>10} {'95%CI上界':>10} {'波动率':>8}")
    for t, m, lo, up, v in zip(future, mean, lower, upper, vol):
        print(f"{t.strftime('%m-%d %H:%M'):>16} {m:>10.2f} {lo:>10.2f} {up:>10.2f} {v:>8.2f}")

    # 绘图
    plt.figure(figsize=(12, 5))
    plt.plot(series.index[-48:], series.values[-48:], label="历史", color="#666")
    plt.plot(future, mean, label="预测均值", color="#f5a623")
    plt.fill_between(future, lower, upper, alpha=0.25, color="#f5a623", label="95% 置信区间")
    plt.legend()
    plt.xlabel("时间")
    plt.ylabel("光伏功率 (kW)")
    plt.title("光伏功率预测 (SARIMA + ARCH)")
    plt.tight_layout()
    plt.savefig("forecast_pv.png", dpi=120)
    print("\n已保存图表: forecast_pv.png")


if __name__ == "__main__":
    main()
