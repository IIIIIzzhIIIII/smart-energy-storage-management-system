"""
D-Vine Copula 联合分布建模与场景采样。

作用：刻画光伏、负荷、电价等多个随机变量之间的**相依结构**（不是各自独立的），
    并基于该结构生成联合场景，供后续 DRO 鲁棒调度使用。

思路：
  SARIMA+ARCH 给出各变量的边缘预测（均值 + 时变波动率）；
  D-Vine Copula 刻画变量间的相关性（如"阴天光伏低、负荷高"）；
  两者结合生成相关的联合场景。

库：pyvinecopulib（D-Vine Copula）
"""
import numpy as np
from scipy.stats import norm
from pyvinecopulib import Vinecop, FitControlsVinecop, to_pseudo_obs


def fit_dvine(data, trunc_lvl=2):
    """
    拟合 D-Vine Copula。

    参数
    ----
    data : (n, d) ndarray 或 DataFrame，d >= 2 个变量（列顺序 = 变量顺序）
    trunc_lvl : 截断层级（树的最大层数）

    返回
    ----
    vc : 拟合好的 Vinecop 对象
    X  : 原始数据 ndarray
    """
    X = np.asarray(data, dtype=float)
    if X.shape[1] < 2:
        raise ValueError("D-Vine Copula 至少需要 2 个变量")
    u = to_pseudo_obs(X)                 # 转伪观测到 [0,1]^d
    controls = FitControlsVinecop(trunc_lvl=trunc_lvl)
    vc = Vinecop.from_data(u, controls=controls)
    return vc, X


def sample_innovations(vc, n_scenarios, seed=None):
    """
    从拟合的 D-Vine Copula 采样，映射为相关的标准正态创新。

    返回 (n_scenarios, d) ndarray，每列近似 N(0,1)，
    且列与列之间保留了 Copula 的秩相依结构（Kendall tau 一致）。
    """
    if seed is not None:
        np.random.seed(seed)
    u = vc.simulate(n_scenarios)                     # (n, d) in [0,1]
    z = norm.ppf(np.clip(u, 1e-6, 1 - 1e-6))         # 逆正态 -> 标准正态
    return z


def generate_scenarios(forecast_pv, forecast_load, vol_pv, vol_load,
                       vc, n_scenarios, price=None):
    """
    基于 Copula 相依结构 + SARIMA 预测生成联合场景。

    参数
    ----
    forecast_pv / forecast_load : (H,) 预测均值（来自 SARIMA）
    vol_pv / vol_load           : (H,) 预测波动率/标准差（来自 ARCH）
    vc                          : 拟合的 D-Vine Copula
    n_scenarios                 : 场景数量
    price                       : (H,) 确定性电价（可选）

    返回
    ----
    scenarios : list[dict]，每个场景含 'pv'、'load'（及可选 'price'）数组 (H,)
    """
    z = sample_innovations(vc, n_scenarios)
    H = len(forecast_pv)
    scenarios = []
    for s in range(n_scenarios):
        # 全局扰动模型：同一场景内创新恒定（代表"全天偏阴/偏晴"这类整体偏移）
        pv = np.clip(forecast_pv + z[s, 0] * vol_pv, 0, None)
        load = np.clip(forecast_load + z[s, 1] * vol_load, 0, None)
        scen = {"pv": pv, "load": load}
        if price is not None:
            scen["price"] = price
        scenarios.append(scen)
    return scenarios
