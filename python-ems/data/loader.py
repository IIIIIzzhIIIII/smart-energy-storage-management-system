"""
统一数据加载器：合成数据 / 公开数据集 一键切换。

无论数据来自哪里，最终都归一化成统一 schema：
    timestamp | pv_power_kw | load_power_kw | price_yuan_kwh

切换方式：改 config.py 里的 DATA_SOURCE 和 CSV_PATH / CSV_COLUMNS 即可，
调用方（预测、Copula、DRO）无需任何改动。
"""
import pandas as pd

import config
from data.synthetic import generate_synthetic


def load_data():
    """返回统一 schema 的 DataFrame。"""
    if config.DATA_SOURCE == "synthetic":
        df = generate_synthetic(
            days=config.SYNTHETIC_DAYS,
            freq=config.SYNTHETIC_FREQ,
        )
    elif config.DATA_SOURCE == "csv":
        df = _load_csv()
    else:
        raise ValueError(f"未知数据源: {config.DATA_SOURCE}")
    return _normalize(df)


def _load_csv():
    """读取公开数据集并映射到统一列名。"""
    raw = pd.read_csv(config.CSV_PATH)
    cols = config.CSV_COLUMNS
    df = pd.DataFrame()
    df["timestamp"] = pd.to_datetime(raw[cols["timestamp"]])
    df["pv_power_kw"] = raw[cols["pv_power_kw"]]
    df["load_power_kw"] = raw[cols["load_power_kw"]]
    if cols.get("price_yuan_kwh"):
        df["price_yuan_kwh"] = raw[cols["price_yuan_kwh"]]
    else:
        df["price_yuan_kwh"] = config.PRICE_FLAT
    return df


def _normalize(df):
    """统一时间索引、排序、重采样到小时级。"""
    df = df.copy()
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    df = df.set_index("timestamp").sort_index()
    df = df.resample("1h").mean().ffill().reset_index()
    return df
