"""
FastAPI 入口：预测大脑服务（端口 8000）。

启动方式（在 python-ems 目录下）：
    python main.py
或
    uvicorn main:app --host 0.0.0.0 --port 8000

接口文档：启动后访问 http://localhost:8000/docs
"""
from fastapi import FastAPI

from api.routes import router

app = FastAPI(
    title="光储智慧调度 - 预测大脑",
    description="SARIMA+ARCH 预测 / D-Vine Copula / DRO 调度（逐步接入）",
    version="0.1.0",
)

app.include_router(router, prefix="/api")


@app.get("/")
def root():
    return {"name": "python-ems", "docs": "/docs"}


if __name__ == "__main__":
    import uvicorn
    # 生产/稳定运行请勿开启 reload：reload 会在文件变动时重启进程，
    # 导致 DRO 这类耗时请求（约10秒）连接被重置（Connection reset）。
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
