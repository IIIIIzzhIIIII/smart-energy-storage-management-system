"""
分布鲁棒优化（DRO）调度（阶段4）。

基于 Copula 生成的光伏/负荷场景集作为"场景模糊集"，求解鲁棒充放电计划：
在场景集的最坏情况下最小化购电成本（min-max）。并与"确定性调度"（仅用点预测）对比。

模型（两阶段）：
  - 第一阶段（here-and-now）：决定各时段 PCS 充放电功率 p[t]（对场景鲁棒）；
  - 第二阶段（recourse）：每个场景下电网功率 g_s[t] 自适应平衡。

变量与约束：
  PCS 功率限幅、SOC 动力学与上下限、逐场景功率平衡。
目标：最小化最坏场景购电成本（min λ, s.t. cost_s <= λ, ∀s）。

库：cvxpy
"""
import numpy as np
import cvxpy as cp


def _shared(H, p_max, E, soc0, soc_min, soc_max):
    """共享决策：PCS 功率 p 与 SOC 轨迹（与场景无关）"""
    p = cp.Variable(H)
    soc = cp.Variable(H + 1)
    constraints = [
        p <= p_max,
        p >= -p_max,
        soc[0] == soc0,
        soc[1:] == soc[:-1] - p * (1.0 / E),  # Δt=1h；放电 p>0 使 SOC 下降
        soc >= soc_min,
        soc <= soc_max,
    ]
    return p, soc, constraints


def deterministic_dispatch(forecast_pv, forecast_load, price,
                           sell_price=0.30, p_max=100, E=200,
                           soc0=0.5, soc_min=0.1, soc_max=0.9):
    """
    确定性调度（基线）：仅使用点预测，最小化购电成本。
    """
    forecast_pv = np.asarray(forecast_pv, dtype=float)
    forecast_load = np.asarray(forecast_load, dtype=float)
    price = np.asarray(price, dtype=float)
    H = len(forecast_pv)

    p, soc, constraints = _shared(H, p_max, E, soc0, soc_min, soc_max)

    g_imp = cp.Variable(H)
    g_exp = cp.Variable(H)
    constraints += [
        forecast_pv + p + g_imp - g_exp == forecast_load,
        g_imp >= 0,
        g_exp >= 0,
    ]
    cost = cp.sum(cp.multiply(price, g_imp) - sell_price * g_exp)

    problem = cp.Problem(cp.Minimize(cost), constraints)
    problem.solve(solver=cp.CLARABEL)

    return {
        "status": problem.status,
        "p": p.value,
        "soc": soc.value,
        "cost": problem.value,
        "grid_import": g_imp.value,
        "grid_export": g_exp.value,
    }


def robust_dispatch(scenarios, price,
                    sell_price=0.30, p_max=100, E=200,
                    soc0=0.5, soc_min=0.1, soc_max=0.9):
    """
    鲁棒调度（场景式 DRO）：最小化最坏场景的购电成本。
    """
    price = np.asarray(price, dtype=float)
    H = len(price)
    p, soc, constraints = _shared(H, p_max, E, soc0, soc_min, soc_max)

    lam = cp.Variable()
    S = len(scenarios)
    for s in range(S):
        pv = np.asarray(scenarios[s]["pv"], dtype=float)
        load = np.asarray(scenarios[s]["load"], dtype=float)
        g_imp = cp.Variable(H)
        g_exp = cp.Variable(H)
        constraints += [
            pv + p + g_imp - g_exp == load,
            g_imp >= 0,
            g_exp >= 0,
        ]
        cost_s = cp.sum(cp.multiply(price, g_imp) - sell_price * g_exp)
        constraints.append(cost_s <= lam)

    problem = cp.Problem(cp.Minimize(lam), constraints)
    problem.solve(solver=cp.CLARABEL)

    return {
        "status": problem.status,
        "p": p.value,
        "soc": soc.value,
        "worst_cost": lam.value,
        "n_scenarios": S,
    }


def plan_to_dict(plan, hours):
    """把调度结果转成便于序列化/绘图的结构"""
    def _arr(a):
        return [round(float(x), 2) for x in (a if a is not None else [])]
    return {
        "hours": hours,
        "p": _arr(plan.get("p")),
        "soc": _arr(plan.get("soc")),
        "cost": round(float(plan.get("cost", plan.get("worst_cost", 0))), 2),
    }
