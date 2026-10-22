# python-ems — 预测与调度服务

独立 Python 服务，与 Java 后端（端口 8082）通过 HTTP 协作，负责：

- **功率预测**：SARIMA + ARCH（光伏、负荷、电价）
- **场景生成**：D-Vine Copula 联合建模
- **优化调度**：分布鲁棒优化（DRO）

## 目录结构

```
python-ems/
├── main.py              # FastAPI 入口（端口 8000）
├── demo.py              # 预测示例（输出图表）
├── demo_dispatch.py     # 调度示例（Copula 场景 + DRO 对比）
├── config.py            # 数据源切换 + 模型参数 + 天气配置
├── requirements.txt
├── data/
│   ├── synthetic.py     # 合成数据生成器
│   └── loader.py        # 统一加载（合成数据 / 公开 CSV）
├── forecast/
│   └── sarima_arch.py   # SARIMA + ARCH/GARCH（预测均值 + 波动率）
├── copula/
│   └── dvine.py         # D-Vine Copula 联合建模 + 场景采样
└── dispatch/
    └── dro.py           # DRO 鲁棒调度 + 确定性调度
```

## 环境准备（Python 3.9~3.13）

```bash
cd python-ems
pip install -r requirements.txt
```

> 敏感配置：复制 `.env.example` 为 `.env` 并填入和风天气 `WEATHER_API_KEY`
> （可选 `WEATHER_API_HOST`）。`.env` 已被 `.gitignore` 忽略，切勿提交到仓库。

## 运行

**预测示例：**
```bash
python demo.py          # 出 forecast_pv.png
```

**调度示例（预测 → Copula → DRO）：**
```bash
python demo_dispatch.py # 出 dispatch_plan.png，打印确定性 vs 鲁棒调度对比
```

**启动服务（供 Java 调用）：**
```bash
python main.py          # 接口文档 http://localhost:8000/docs
```

## 接口

| 接口 | 说明 |
|---|---|
| `GET /api/health` | 健康检查 |
| `POST /api/forecast` | SARIMA+ARCH 预测（series=pv/load/price） |
| `POST /api/dispatch/plan` | 完整 DRO 链路：预测→Copula场景→确定性/鲁棒调度 |

调用示例：
```bash
curl -X POST http://localhost:8000/api/dispatch/plan \
  -H "Content-Type: application/json" \
  -d '{"steps":24,"n_scenarios":50}'
```

## 方法链

```
SARIMA+ARCH  ──► 各变量边缘预测(均值 + 时变波动率)
D-Vine Copula ─► 刻画变量间相依结构，生成联合场景
DRO           ─► 场景模糊集下最小化最坏情况购电成本（与确定性调度对比）
```

## 切换公开数据集

编辑 `config.py`：
```python
DATA_SOURCE = "csv"
CSV_PATH = "data/public_dataset.csv"
CSV_COLUMNS = {"timestamp": "时间", "pv_power_kw": "光伏功率", ...}
```
`loader.py` 会把任意列名映射成统一 schema，算法代码零改动。
