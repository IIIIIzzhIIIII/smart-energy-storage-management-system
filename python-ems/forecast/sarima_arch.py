"""
SARIMA + ARCH/GARCH 时序预测。

SARIMA   -> 刻画序列的均值结构（趋势 + 日/周季节周期）
ARCH/GARCH -> 刻画残差的时变波动率（即"预测有多不确定"）

两者结合，既给出点预测，也给出随时间变化的置信区间，
后续 D-Vine Copula / DRO 会用到这里的"均值 + 波动"作为边缘分布。
"""
import numpy as np
import pandas as pd
from statsmodels.tsa.statespace.sarimax import SARIMAX
from arch import arch_model


def forecast(series, steps=24, order=(1, 1, 1), seasonal_order=(1, 1, 1, 24),
             garch_p=1, garch_q=1):
    """
    对单变量序列做 SARIMA + GARCH 预测。

    参数
    ----
    series : pandas.Series（index 为时间）
    steps  : 预测步数
    order / seasonal_order : SARIMA 阶数
    garch_p / garch_q      : GARCH 阶数

    返回
    ----
    mean  : (steps,) 预测均值
    lower : (steps,) 95% 置信区间下界（SARIMA 静态区间）
    upper : (steps,) 95% 置信区间上界
    vol   : (steps,) GARCH 预测的时变波动率（标准差）
    """
    series = series.astype(float).dropna()
    if len(series) < 50:
        raise ValueError("样本过少，无法拟合 SARIMA（至少几十个点）")
    # 补全频率信息，避免 statsmodels 推断频率告警
    if series.index.freq is None:
        series.index.freq = pd.infer_freq(series.index)

    # ---- 1) SARIMA 均值预测 ----
    sarima = SARIMAX(
        series,
        order=order,
        seasonal_order=seasonal_order,
        enforce_stationarity=False,
        enforce_invertibility=False,
    )
    sarima_fit = sarima.fit(disp=False)
    fc = sarima_fit.get_forecast(steps)
    mean = np.asarray(fc.predicted_mean.values, dtype=float)
    ci = fc.conf_int(alpha=0.05)          # 95% CI
    lower = np.asarray(ci.iloc[:, 0].values, dtype=float)
    upper = np.asarray(ci.iloc[:, 1].values, dtype=float)

    # ---- 2) 残差 ARCH/GARCH 时变波动率 ----
    resid = sarima_fit.resid.dropna()
    am = arch_model(resid, vol="GARCH", p=garch_p, q=garch_q, mean="zero")
    am_fit = am.fit(disp="off")
    vol_fc = am_fit.forecast(horizon=steps)
    vol = np.sqrt(np.asarray(vol_fc.variance.values[-1, :], dtype=float))

    return mean, lower, upper, vol


def forecast_dataframe(df, column, steps=24, **kwargs):
    """
    便捷方法：从统一 schema 的 DataFrame 中取列并预测，
    返回带时间戳的结果 DataFrame（便于绘图 / 接口返回）。
    """
    series = df.set_index("timestamp")[column]
    mean, lower, upper, vol = forecast(series, steps=steps, **kwargs)

    last_ts = series.index[-1]
    future = pd.date_range(start=last_ts + pd.Timedelta(hours=1), periods=steps, freq="1h")

    return pd.DataFrame({
        "timestamp": future,
        "mean": mean,
        "lower": lower,
        "upper": upper,
        "vol": vol,
    })
