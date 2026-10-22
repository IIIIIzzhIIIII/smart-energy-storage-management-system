"""
PyInstaller 冻结入口。

与 main.py 的区别：
  * 直接把 app 对象传给 uvicorn，而不是字符串 "main:app"。
    冻结后没有源码文件/模块搜索路径，字符串导入会失败。
  * 固定监听 127.0.0.1:8000，只供本机 Java 后端调用。
"""
import os
import sys

import uvicorn

# 保证以 exe 所在目录为工作目录，便于读取 .env / data 等相对路径资源
if getattr(sys, "frozen", False):
    os.chdir(os.path.dirname(os.path.abspath(sys.executable)))

from main import app  # noqa: E402

if __name__ == "__main__":
    uvicorn.run(
        app,
        host="127.0.0.1",
        port=8000,
        reload=False,
        access_log=False,
        log_level="info",
    )
