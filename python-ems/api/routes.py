"""
FastAPI 路由：对外提供预测接口，供 Java 后端通过 HTTP 调用。

接口：
    GET  /api/health    健康检查
    POST /api/forecast  预测（series=pv/load/price, steps=预测步数）
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

import numpy as np
import config
from data.loader import load_data
from forecast.sarima_arch import forecast as sarima_forecast
from copula.dvine import fit_dvine, generate_scenarios
from dispatch.dro import deterministic_dispatch, robust_dispatch

router = APIRouter()

# 序列名 -> DataFrame 列名
SERIES_COLUMNS = {
    "pv": "pv_power_kw",
    "load": "load_power_kw",
    "price": "price_yuan_kwh",
}


class ForecastRequest(BaseModel):
    series: str = "pv"       # pv / load / price
    steps: int = 24


@router.get("/health")
def health():
    return {"status": "ok", "service": "python-ems"}


@router.post("/forecast")
def forecast(req: ForecastRequest):
    if req.series not in SERIES_COLUMNS:
        raise HTTPException(status_code=400, detail="series 必须是 pv / load / price")
    if req.steps <= 0 or req.steps > 168:
        raise HTTPException(status_code=400, detail="steps 必须在 1 ~ 168 之间")

    df = load_data()
    series = df.set_index("timestamp")[SERIES_COLUMNS[req.series]]

    mean, lower, upper, vol = sarima_forecast(
        series,
        steps=req.steps,
        order=config.SARIMA_ORDER,
        seasonal_order=config.SARIMA_SEASONAL_ORDER,
        garch_p=config.GARCH_P,
        garch_q=config.GARCH_Q,
    )

    last_ts = series.index[-1]
    future = pd_date_range_after(last_ts, req.steps)

    return {
        "series": req.series,
        "data_source": config.DATA_SOURCE,
        "timestamps": [t.strftime("%Y-%m-%d %H:%M:%S") for t in future],
        "mean": [round(float(x), 2) for x in mean],
        "lower": [round(float(x), 2) for x in lower],
        "upper": [round(float(x), 2) for x in upper],
        "vol": [round(float(x), 4) for x in vol],
    }


def pd_date_range_after(last_ts, steps):
    import pandas as pd
    return pd.date_range(start=last_ts + pd.Timedelta(hours=1), periods=steps, freq="1h")


class PlanRequest(BaseModel):
    steps: int = 24          # 调度时长（小时）
    n_scenarios: int = 50    # Copula 场景数
    sell_price: float = 0.30 # 上网电价(元/kWh)
    p_max: float = 100       # PCS 最大功率(kW)
    E: float = 200           # 储能容量(kWh)
    soc0: float = 0.5        # 初始 SOC


def _price_of(hour):
    if hour >= 23 or hour < 7:
        return 0.35
    if 10 <= hour < 12 or 17 <= hour < 21:
        return 0.95
    return 0.60


@router.post("/dispatch/plan")
def dispatch_plan(req: PlanRequest):
    """完整 DRO 调度链路：SARIMA预测 -> D-Vine Copula 场景 -> 确定性/鲁棒调度"""
    import pandas as pd

    df = load_data()
    ts = df.set_index("timestamp")
    pv_series = ts["pv_power_kw"]
    load_series = ts["load_power_kw"]

    # 1) 预测
    pv_mean, _, _, pv_vol = sarima_forecast(
        pv_series, req.steps, config.SARIMA_ORDER, config.SARIMA_SEASONAL_ORDER, config.GARCH_P, config.GARCH_Q)
    load_mean, _, _, load_vol = sarima_forecast(
        load_series, req.steps, config.SARIMA_ORDER, config.SARIMA_SEASONAL_ORDER, config.GARCH_P, config.GARCH_Q)
    pv_vol = np.maximum(pv_vol, 1.0)
    load_vol = np.maximum(load_vol, 1.0)

    future = pd_date_range_after(ts.index[-1], req.steps)
    hours = [t.strftime("%H:00") for t in future]
    price = np.array([_price_of(t.hour) for t in future])

    # 2) Copula（对创新/残差建模）
    resid = ts.copy()
    resid["hour"] = resid.index.hour
    for col in ["pv_power_kw", "load_power_kw"]:
        resid[col + "_resid"] = resid[col] - resid.groupby("hour")[col].transform("mean")
    copula_data = resid[["pv_power_kw_resid", "load_power_kw_resid", "price_yuan_kwh"]].values
    vc, _ = fit_dvine(copula_data)

    # 3) 场景
    scenarios = generate_scenarios(pv_mean, load_mean, pv_vol, load_vol, vc, req.n_scenarios, price)

    # 4) 调度
    det = deterministic_dispatch(pv_mean, load_mean, price, sell_price=req.sell_price,
                                 p_max=req.p_max, E=req.E, soc0=req.soc0)
    rob = robust_dispatch(scenarios, price, sell_price=req.sell_price,
                          p_max=req.p_max, E=req.E, soc0=req.soc0)

    def _lst(a):
        return [round(float(x), 2) for x in (a if a is not None else [])]

    return {
        "data_source": config.DATA_SOURCE,
        "horizon": req.steps,
        "hours": hours,
        "price": [round(float(x), 2) for x in price],
        "forecast": {"pv": _lst(pv_mean), "load": _lst(load_mean)},
        "n_scenarios": req.n_scenarios,
        "deterministic": {
            "status": det["status"], "p": _lst(det["p"]), "soc": _lst(det["soc"]),
            "cost": round(float(det["cost"]), 2),
        },
        "robust": {
            "status": rob["status"], "p": _lst(rob["p"]), "soc": _lst(rob["soc"]),
            "worst_cost": round(float(rob["worst_cost"]), 2),
        },
    }
