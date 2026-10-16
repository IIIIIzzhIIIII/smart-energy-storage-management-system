"""
阶段3+4 联合演示：预测 -> D-Vine Copula 场景生成 -> DRO 鲁棒调度 -> 与确定性调度对比。

用法（在 python-ems 目录下）：
    python demo_dispatch.py

效果：打印确定性 vs 鲁棒调度的 PCS 计划与成本，保存图表 dispatch_plan.png
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

import config
from data.loader import load_data
from forecast.sarima_arch import forecast as sarima_forecast
from copula.dvine import fit_dvine, generate_scenarios
from dispatch.dro import deterministic_dispatch, robust_dispatch

# 中文字体
plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False


def price_of(hour):
    if hour >= 23 or hour < 7:
        return 0.35
    if 10 <= hour < 12 or 17 <= hour < 21:
        return 0.95
    return 0.60


def main():
    df = load_data()
    print(f"数据源: {config.DATA_SOURCE}, 样本: {len(df)}")

    H = 24      # 调度时长（小时）
    S = 50      # 场景数

    # ---- 1) SARIMA+ARCH 预测 ----
    ts = df.set_index("timestamp")
    pv_series = ts["pv_power_kw"]
    load_series = ts["load_power_kw"]

    pv_mean, _, _, pv_vol = sarima_forecast(
        pv_series, H, config.SARIMA_ORDER, config.SARIMA_SEASONAL_ORDER, config.GARCH_P, config.GARCH_Q)
    load_mean, _, _, load_vol = sarima_forecast(
        load_series, H, config.SARIMA_ORDER, config.SARIMA_SEASONAL_ORDER, config.GARCH_P, config.GARCH_Q)
    pv_vol = np.maximum(pv_vol, 1.0)
    load_vol = np.maximum(load_vol, 1.0)

    future = pd.date_range(ts.index[-1] + pd.Timedelta(hours=1), periods=H, freq="1h")
    hours = [t.strftime("%H:00") for t in future]
    price = np.array([price_of(t.hour) for t in future])

    # ---- 2) D-Vine Copula：对创新(残差)建模 ----
    resid = ts.copy()
    resid["hour"] = resid.index.hour
    for col in ["pv_power_kw", "load_power_kw"]:
        resid[col + "_resid"] = resid[col] - resid.groupby("hour")[col].transform("mean")
    copula_data = resid[["pv_power_kw_resid", "load_power_kw_resid", "price_yuan_kwh"]].values
    vc, _ = fit_dvine(copula_data)
    print(f"D-Vine Copula 拟合完成: 维度={vc.dim}, 参数数={vc.npars}")

    # ---- 3) 生成联合场景 ----
    scenarios = generate_scenarios(pv_mean, load_mean, pv_vol, load_vol, vc, S, price)

    # ---- 4) 确定性 vs 鲁棒调度 ----
    det = deterministic_dispatch(pv_mean, load_mean, price)
    rob = robust_dispatch(scenarios, price)
    print(f"\n确定性调度: status={det['status']}, 成本={det['cost']:.2f} 元")
    print(f"鲁棒调度(DRO): status={rob['status']}, 最坏场景成本={rob['worst_cost']:.2f} 元")

    print("\n 时段  | 电价 | 确定性PCS | 鲁棒PCS")
    print("-" * 40)
    for t in range(H):
        print(f"{hours[t]:>5} | {price[t]:.2f} | {det['p'][t]:>9.1f} | {rob['p'][t]:>8.1f}")

    # ---- 5) 绘图 ----
    fig, axes = plt.subplots(2, 2, figsize=(14, 8))

    ax = axes[0][0]
    ax.plot(hours, pv_mean, color="#f5a623", label="光伏预测")
    for s in scenarios[:10]:
        ax.plot(hours, s["pv"], color="#f5a623", alpha=0.15)
    ax.set_title("光伏场景（D-Vine Copula 采样）")
    ax.legend()
    ax.tick_params(axis="x", rotation=45)

    ax = axes[0][1]
    ax.plot(hours, load_mean, color="#ff7043", label="负荷预测")
    for s in scenarios[:10]:
        ax.plot(hours, s["load"], color="#ff7043", alpha=0.15)
    ax.set_title("负荷场景")
    ax.legend()
    ax.tick_params(axis="x", rotation=45)

    ax = axes[1][0]
    ax.plot(hours, det["p"], marker="o", color="#00b4d8", label="确定性调度")
    ax.plot(hours, rob["p"], marker="o", color="#00e5ff", label="鲁棒调度(DRO)")
    ax.axhline(0, color="#666", linewidth=1)
    ax.set_title("PCS 功率计划（正=放电, 负=充电）")
    ax.legend()
    ax.tick_params(axis="x", rotation=45)

    ax = axes[1][1]
    ax.plot(hours, det["soc"][:-1], color="#00b4d8", label="确定性 SOC")
    ax.plot(hours, rob["soc"][:-1], color="#00e5ff", label="鲁棒 SOC")
    ax.set_title("储能 SOC 轨迹")
    ax.legend()
    ax.tick_params(axis="x", rotation=45)

    plt.tight_layout()
    plt.savefig("dispatch_plan.png", dpi=120)
    print("\n已保存图表: dispatch_plan.png")


if __name__ == "__main__":
    main()
